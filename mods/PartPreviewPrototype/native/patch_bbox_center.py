#!/usr/bin/env python3
"""Create a flags-preserving, non-dereferencing SLRR BBox interception probe.

The patch is based only on the verified, working silent_v2 executable.
For the reserved BBox query family it returns 0x42424242 and does not read
native object memory. For ordinary getInfo calls it restores EFLAGS, recreates
the original stack layout and transfers to the same virtual method with the
original return address. The input is never overwritten.
"""
from __future__ import annotations
import argparse
import hashlib
import struct
import sys
from pathlib import Path

EXPECTED_SHA256 = "a954fc724a4561e5e30eba3a2cc4be1c162636eac9db5ba7746c53e2f89638bf"
IMAGE_BASE = 0x00400000
HOOK_SITE_VA = 0x0047DEDD
HOOK_SITE_BYTES = bytes.fromhex("8B 57 4C 52 FF 50 3C")
HOOK_CAVE_VA = 0x007DF972
HOOK_BYTES = bytes.fromhex(
    "9C"
    "8B 54 24 04"
    "81 EA 42 42 4F 58"
    "83 FA 02"
    "76 0F"
    "8B 54 24 08"
    "81 EA 42 42 4F 58"
    "83 FA 02"
    "77 0E"
    "9D"
    "B8 42 42 42 42"
    "83 C4 08"
    "E9 45 E5 C9 FF"
    "9D"
    "8B 57 4C"
    "52"
    "68 E4 DE 47 00"
    "FF 60 3C"
)
SILENT_WRITER_GUARDS = {
    0x007DE081: bytes.fromhex("C3 90 90 90"),
    0x007DE2D4: bytes.fromhex("C3 90 90 90"),
    0x007DE4BF: bytes.fromhex("C3 90 90 90 90"),
}


def u16(data: bytes, off: int) -> int:
    return struct.unpack_from("<H", data, off)[0]


def u32(data: bytes, off: int) -> int:
    return struct.unpack_from("<I", data, off)[0]


def va_to_offset(data: bytes, va: int, image_base: int) -> tuple[int, str, int, int]:
    rva = va - image_base
    pe = u32(data, 0x3C)
    count = u16(data, pe + 6)
    opt_size = u16(data, pe + 20)
    section_table = pe + 24 + opt_size
    for i in range(count):
        off = section_table + i * 40
        name = data[off:off+8].split(b"\0",1)[0].decode("ascii","replace")
        vs, va0, raw_size, raw_ptr = struct.unpack_from("<IIII", data, off+8)
        characteristics = u32(data, off+36)
        if va0 <= rva < va0 + max(vs, raw_size):
            delta = rva - va0
            if delta >= raw_size:
                raise ValueError(f"VA 0x{va:08X} is not backed by raw data ({name})")
            return raw_ptr + delta, name, characteristics, raw_size - delta
    raise ValueError(f"VA 0x{va:08X} is not mapped by a PE section")


def main() -> int:
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument("input", type=Path, help="working StreetLegal_Redline_native_silent_v2.exe")
    ap.add_argument("output", type=Path, nargs="?", help="new test EXE path")
    args = ap.parse_args()
    src = args.input.resolve()
    if not src.is_file():
        ap.error(f"input file not found: {src}")
    original = src.read_bytes()
    digest = hashlib.sha256(original).hexdigest()
    if digest != EXPECTED_SHA256:
        raise SystemExit(
            "Wrong base EXE; refusing to patch.\n"
            f"Expected: {EXPECTED_SHA256}\n"
            f"Actual:   {digest}"
        )
    if original[:2] != b"MZ":
        raise SystemExit("Missing MZ header")
    pe = u32(original, 0x3C)
    if original[pe:pe+4] != b"PE\0\0":
        raise SystemExit("Missing PE signature")
    if u16(original, pe+4) != 0x014C or u16(original, pe+24) != 0x010B:
        raise SystemExit("Expected x86 PE32")
    image_base = u32(original, pe+24+28)
    if image_base != IMAGE_BASE:
        raise SystemExit(f"Unexpected image base: {image_base:#x}")

    site_off, site_sec, site_chars, _ = va_to_offset(original, HOOK_SITE_VA, image_base)
    cave_off, cave_sec, cave_chars, cave_room = va_to_offset(original, HOOK_CAVE_VA, image_base)
    if site_sec != ".text" or cave_sec != ".reloc":
        raise SystemExit("Unexpected hook/cave sections")
    if not (site_chars & 0x20000000):
        raise SystemExit("Hook site is not executable")
    if not (cave_chars & 0x20000000 and cave_chars & 0x80000000):
        raise SystemExit("Code cave is not executable+writable")
    if original[site_off:site_off+len(HOOK_SITE_BYTES)] != HOOK_SITE_BYTES:
        raise SystemExit("Virtual-call instructions differ from the verified build")
    if cave_room < len(HOOK_BYTES):
        raise SystemExit("Code cave is too small")
    if any(original[cave_off:cave_off+len(HOOK_BYTES)]):
        raise SystemExit("Code cave is not empty")

    # Verify the two GameRef.getInfo overloads still use the shared dispatcher.
    for va in (0x00489666, 0x00489685):
        off, _, _, _ = va_to_offset(original, va, image_base)
        if original[off:off+5] != bytes.fromhex("68 F0 DD 47 00"):
            raise SystemExit(f"GameRef.getInfo registration changed at {va:#010x}")
    # Preserve the working silent_v2 Reflection log suppression.
    for va, expected in SILENT_WRITER_GUARDS.items():
        off, _, _, _ = va_to_offset(original, va, image_base)
        if original[off:off+len(expected)] != expected:
            raise SystemExit(f"Reflection diagnostic writer guard changed at {va:#010x}")

    rel = HOOK_CAVE_VA - (HOOK_SITE_VA + 5)
    detour = b"\xE9" + struct.pack("<i", rel) + b"\x90\x90"
    patched = bytearray(original)
    patched[site_off:site_off+len(detour)] = detour
    patched[cave_off:cave_off+len(HOOK_BYTES)] = HOOK_BYTES

    out = args.output.resolve() if args.output else src.with_name(src.stem + "_bbox_probe.exe")
    if out == src:
        raise SystemExit("Output must not overwrite input")
    out.parent.mkdir(parents=True, exist_ok=True)
    out.write_bytes(patched)
    check = out.read_bytes()
    if check[site_off:site_off+len(detour)] != detour or check[cave_off:cave_off+len(HOOK_BYTES)] != HOOK_BYTES:
        raise SystemExit("Output verification failed")

    print(f"Output: {out}")
    print(f"Base SHA-256: {digest}")
    print(f"Output SHA-256: {hashlib.sha256(check).hexdigest()}")
    print(f"Hook site: VA {HOOK_SITE_VA:#010x}, file {site_off:#x}, section {site_sec}")
    print(f"Hook cave: VA {HOOK_CAVE_VA:#010x}, file {cave_off:#x}, {len(HOOK_BYTES)} bytes")
    print("Other getInfo calls replay the original virtual call.")
    print("Reserved BBox calls return test marker 0x42424242; no native object fields are read.")
    print("Reflection log and dump writer patches are retained.")
    print("Runtime validation in SLRR is still required.")
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except (OSError, ValueError, struct.error) as exc:
        print(f"ERROR: {exc}", file=sys.stderr)
        raise SystemExit(2)
