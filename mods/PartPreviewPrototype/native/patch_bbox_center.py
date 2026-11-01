#!/usr/bin/env python3
"""Route only GameRef.getInfo(II)I through a pass-through thunk.

This isolation build does NOT hook the shared getInfo dispatcher internals.
The registered function pointer for the (II)I overload is redirected to a
five-byte JMP thunk, which immediately jumps to the original native function.
No Java arguments, object fields, or mesh memory are inspected.
"""
from __future__ import annotations
import argparse
import hashlib
import struct
import sys
from pathlib import Path

EXPECTED_SHA256 = "a954fc724a4561e5e30eba3a2cc4be1c162636eac9db5ba7746c53e2f89638bf"
IMAGE_BASE = 0x00400000
REGISTER_PUSH_VA = 0x00489666
REGISTER_PUSH = bytes.fromhex("68 F0 DD 47 00")
ORIGINAL_NATIVE_VA = 0x0047DDF0
THUNK_VA = 0x007DF972
STRING_OVERLOAD_PUSH_VA = 0x00489685
WRITER_GUARDS = {
    0x007DE081: bytes.fromhex("C3 90 90 90"),
    0x007DE2D4: bytes.fromhex("C3 90 90 90"),
    0x007DE4BF: bytes.fromhex("C3 90 90 90 90"),
}


def u16(data: bytes, off: int) -> int:
    return struct.unpack_from("<H", data, off)[0]


def u32(data: bytes, off: int) -> int:
    return struct.unpack_from("<I", data, off)[0]


def va_to_offset(data: bytes, va: int) -> tuple[int, str, int, int]:
    pe = u32(data, 0x3C)
    image_base = u32(data, pe + 24 + 28)
    rva = va - image_base
    count = u16(data, pe + 6)
    opt_size = u16(data, pe + 20)
    table = pe + 24 + opt_size
    for i in range(count):
        off = table + i * 40
        name = data[off:off+8].split(b"\0", 1)[0].decode("ascii", "replace")
        vs, va0, raw_size, raw_ptr = struct.unpack_from("<IIII", data, off + 8)
        chars = u32(data, off + 36)
        if va0 <= rva < va0 + max(vs, raw_size):
            delta = rva - va0
            if delta >= raw_size:
                raise ValueError(f"VA {va:#010x} has no raw backing in {name}")
            return raw_ptr + delta, name, chars, raw_size - delta
    raise ValueError(f"VA {va:#010x} is not mapped by a PE section")


def c_string(data: bytes, va: int) -> str:
    off, _, _, _ = va_to_offset(data, va)
    end = data.find(b"\0", off)
    if end < 0:
        raise ValueError(f"Unterminated string at {va:#010x}")
    return data[off:end].decode("ascii", "replace")


def has_highlow_reloc(data: bytes, target_rva: int) -> bool:
    pe = u32(data, 0x3C)
    opt = pe + 24
    reloc_rva, reloc_size = struct.unpack_from("<II", data, opt + 96 + 5 * 8)
    reloc_off, _, _, _ = va_to_offset(data, IMAGE_BASE + reloc_rva)
    pos, end = reloc_off, reloc_off + reloc_size
    while pos + 8 <= end:
        page, block_size = struct.unpack_from("<II", data, pos)
        if block_size < 8 or pos + block_size > end:
            raise ValueError("Malformed base-relocation directory")
        for i in range((block_size - 8) // 2):
            entry = u16(data, pos + 8 + i * 2)
            if (entry >> 12) == 3 and page + (entry & 0x0FFF) == target_rva:
                return True
        pos += block_size
    return False


def main() -> int:
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument("input", type=Path, help="known-good native_silent_v2.exe")
    ap.add_argument("output", type=Path, nargs="?", help="separate test output")
    args = ap.parse_args()
    src = args.input.resolve()
    if not src.is_file():
        ap.error(f"input file not found: {src}")
    original = src.read_bytes()
    digest = hashlib.sha256(original).hexdigest()
    if digest != EXPECTED_SHA256:
        raise SystemExit(f"Wrong base EXE. Expected {EXPECTED_SHA256}, got {digest}")

    pe = u32(original, 0x3C)
    if original[:2] != b"MZ" or original[pe:pe+4] != b"PE\0\0":
        raise SystemExit("Invalid PE file")
    if u16(original, pe + 4) != 0x014C or u16(original, pe + 24) != 0x010B:
        raise SystemExit("Expected x86 PE32")
    if u32(original, pe + 24 + 28) != IMAGE_BASE:
        raise SystemExit("Unexpected image base")

    reg_off, reg_sec, reg_chars, _ = va_to_offset(original, REGISTER_PUSH_VA)
    cave_off, cave_sec, cave_chars, cave_room = va_to_offset(original, THUNK_VA)
    string_overload_off, _, _, _ = va_to_offset(original, STRING_OVERLOAD_PUSH_VA)
    if reg_sec != ".text" or cave_sec != ".reloc":
        raise SystemExit("Unexpected registration/thunk sections")
    if not (reg_chars & 0x20000000) or not (cave_chars & 0x20000000):
        raise SystemExit("Registration site or thunk region is not executable")
    if original[reg_off:reg_off+5] != REGISTER_PUSH:
        raise SystemExit("getInfo(II)I registration does not match expected dispatcher")
    if original[string_overload_off:string_overload_off+5] != REGISTER_PUSH:
        raise SystemExit("String overload registration unexpectedly changed")
    if c_string(original, 0x00616278) != "(II)I":
        raise SystemExit("Unexpected first registration signature")
    if c_string(original, 0x00616280) != "getInfo":
        raise SystemExit("Unexpected first registration method")
    if c_string(original, 0x00616288) != "java.util.resource.GameRef":
        raise SystemExit("Unexpected first registration class")
    if c_string(original, 0x006162A4) != "(ILjava.lang.String;)I":
        raise SystemExit("Unexpected second registration signature")
    if not has_highlow_reloc(original, REGISTER_PUSH_VA + 1 - IMAGE_BASE):
        raise SystemExit("Function pointer immediate has no HIGHLOW relocation")
    if cave_room < 5 or original[cave_off:cave_off+5] != b"\0" * 5:
        raise SystemExit("Thunk cave is not empty")
    for va, expected in WRITER_GUARDS.items():
        off, _, _, _ = va_to_offset(original, va)
        if original[off:off+len(expected)] != expected:
            raise SystemExit(f"Reflection writer guard changed at {va:#010x}")

    # Five-byte relative JMP is position-independent and preserves all state.
    rel = ORIGINAL_NATIVE_VA - (THUNK_VA + 5)
    thunk = b"\xE9" + struct.pack("<i", rel)
    patched = bytearray(original)
    patched[reg_off+1:reg_off+5] = struct.pack("<I", THUNK_VA)
    patched[cave_off:cave_off+5] = thunk

    out = args.output.resolve() if args.output else src.with_name(src.stem + "_getinfo_route_probe.exe")
    if out == src:
        raise SystemExit("Refusing to overwrite source executable")
    out.parent.mkdir(parents=True, exist_ok=True)
    out.write_bytes(patched)
    check = out.read_bytes()
    if check[reg_off+1:reg_off+5] != struct.pack("<I", THUNK_VA):
        raise SystemExit("Registration pointer verification failed")
    if check[cave_off:cave_off+5] != thunk:
        raise SystemExit("Thunk verification failed")
    if check[string_overload_off:string_overload_off+5] != REGISTER_PUSH:
        raise SystemExit("String overload registration was modified")

    print(f"Output: {out}")
    print(f"Base SHA-256: {digest}")
    print(f"Output SHA-256: {hashlib.sha256(check).hexdigest()}")
    print(f"Only registration changed: getInfo(II)I -> thunk {THUNK_VA:#010x}")
    print(f"Thunk directly jumps to original dispatcher {ORIGINAL_NATIVE_VA:#010x}")
    print("Shared dispatcher internals and the String overload are untouched.")
    print("Reflection diagnostic writer suppression is retained.")
    print("No Java arguments or object fields are inspected.")
    print("Runtime validation in SLRR is still required.")
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except (OSError, ValueError, struct.error) as exc:
        print(f"ERROR: {exc}", file=sys.stderr)
        raise SystemExit(2)
