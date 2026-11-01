#!/usr/bin/env python3
"""Build an isolated clone of GameRef.getInfo(II)I's native dispatcher.

This stage copies the original native function, fixes all seven relative CALLs,
duplicates its seven HIGHLOW relocations, and redirects only the (II)I
registration. No Bounding Box logic or object-memory reads are added yet.
"""
from __future__ import annotations
import argparse
import hashlib
import struct
import sys
from pathlib import Path

EXPECTED_SHA256 = "a954fc724a4561e5e30eba3a2cc4be1c162636eac9db5ba7746c53e2f89638bf"
IMAGE_BASE = 0x00400000
REG_PUSH_VA = 0x00489666
REG_PUSH_BYTES = bytes.fromhex("68 F0 DD 47 00")
OTHER_OVERLOAD_VA = 0x00489685
DISPATCHER_VA = 0x0047DDF0
DISPATCHER_END_VA = 0x0047DF40
CLONE_VA = 0x007DF972
CALL_FIXUPS = {
    0x01C: (0x0045D910, bytes.fromhex("E8 FF FA FD FF")),
    0x02D: (0x0042AB50, bytes.fromhex("E8 2E CD FA FF")),
    0x075: (0x005447D0, bytes.fromhex("E8 66 69 0C 00")),
    0x0C2: (0x005447D0, bytes.fromhex("E8 19 69 0C 00")),
    0x117: (0x00551140, bytes.fromhex("E8 34 32 0D 00")),
    0x12B: (0x00551140, bytes.fromhex("E8 20 32 0D 00")),
    0x138: (0x005513B0, bytes.fromhex("E8 83 34 0D 00")),
}
EXPECTED_RELOC_VAS = [
    0x0047DE13, 0x0047DEFE, 0x0047DF03, 0x0047DF12,
    0x0047DF17, 0x0047DF24, 0x0047DF32,
]
WRITER_GUARDS = {
    0x007DE081: bytes.fromhex("C3 90 90 90"),
    0x007DE2D4: bytes.fromhex("C3 90 90 90"),
    0x007DE4BF: bytes.fromhex("C3 90 90 90 90"),
}


def u16(b: bytes, o: int) -> int:
    return struct.unpack_from("<H", b, o)[0]


def u32(b: bytes, o: int) -> int:
    return struct.unpack_from("<I", b, o)[0]


def p32(b: bytearray, o: int, v: int) -> None:
    struct.pack_into("<I", b, o, v)


def sections(b: bytes):
    pe = u32(b, 0x3C)
    n, opt_size = u16(b, pe + 6), u16(b, pe + 20)
    table = pe + 24 + opt_size
    result = []
    for i in range(n):
        o = table + i * 40
        name = b[o:o+8].split(b"\0", 1)[0].decode("ascii", "replace")
        vs, va, raw_size, raw_ptr = struct.unpack_from("<IIII", b, o + 8)
        chars = u32(b, o + 36)
        result.append((name, va, vs, raw_size, raw_ptr, chars))
    return pe, result


def va_to_offset(b: bytes, va: int):
    pe, secs = sections(b)
    image_base = u32(b, pe + 24 + 28)
    rva = va - image_base
    for name, va0, vs, raw_size, raw_ptr, chars in secs:
        if va0 <= rva < va0 + max(vs, raw_size):
            delta = rva - va0
            if delta >= raw_size:
                raise ValueError(f"VA {va:#010x} has no raw bytes in {name}")
            return raw_ptr + delta, name, chars, raw_size - delta
    raise ValueError(f"VA {va:#010x} is not mapped")


def parse_relocations(b: bytes):
    pe, _ = sections(b)
    opt = pe + 24
    reloc_rva, reloc_size = struct.unpack_from("<II", b, opt + 96 + 5 * 8)
    reloc_off, _, _, _ = va_to_offset(b, IMAGE_BASE + reloc_rva)
    pos, end = reloc_off, reloc_off + reloc_size
    entries = []
    while pos + 8 <= end:
        page, block_size = struct.unpack_from("<II", b, pos)
        if page == 0 and block_size == 0:
            break
        if block_size < 8 or block_size % 4 or pos + block_size > end:
            raise ValueError(f"Malformed relocation block at {pos:#x}")
        for i in range((block_size - 8) // 2):
            e = u16(b, pos + 8 + i * 2)
            typ, delta = e >> 12, e & 0x0FFF
            if typ:
                entries.append((page + delta + IMAGE_BASE, typ))
        pos += block_size
    return reloc_rva, reloc_size, pos, entries


def read_c_string(b: bytes, va: int) -> str:
    off, _, _, _ = va_to_offset(b, va)
    end = b.find(b"\0", off)
    if end < 0:
        raise ValueError(f"Unterminated string at {va:#010x}")
    return b[off:end].decode("ascii", "replace")


def main() -> int:
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument("input", type=Path, help="known-good native_silent_v2.exe")
    ap.add_argument("output", type=Path, nargs="?", help="separate clone-test executable")
    args = ap.parse_args()
    src = args.input.resolve()
    if not src.is_file():
        ap.error(f"Input not found: {src}")
    original = src.read_bytes()
    digest = hashlib.sha256(original).hexdigest()
    if digest != EXPECTED_SHA256:
        raise SystemExit(f"Wrong base EXE. Expected {EXPECTED_SHA256}; got {digest}")

    pe, _ = sections(original)
    if original[:2] != b"MZ" or original[pe:pe+4] != b"PE\0\0":
        raise SystemExit("Invalid PE")
    if u16(original, pe + 4) != 0x014C or u16(original, pe + 24) != 0x010B:
        raise SystemExit("Expected x86 PE32")
    if u32(original, pe + 24 + 28) != IMAGE_BASE:
        raise SystemExit("Unexpected image base")

    reg_off, reg_sec, reg_chars, _ = va_to_offset(original, REG_PUSH_VA)
    other_off, _, _, _ = va_to_offset(original, OTHER_OVERLOAD_VA)
    clone_off, clone_sec, clone_chars, clone_room = va_to_offset(original, CLONE_VA)
    fn_off, fn_sec, _, _ = va_to_offset(original, DISPATCHER_VA)
    fn_size = DISPATCHER_END_VA - DISPATCHER_VA
    fn_end_off, _, _, _ = va_to_offset(original, DISPATCHER_END_VA - 1)
    if fn_end_off + 1 - fn_off != fn_size:
        raise SystemExit("Dispatcher file mapping is not contiguous")
    if reg_sec != ".text" or fn_sec != ".text" or clone_sec != ".reloc":
        raise SystemExit("Unexpected PE sections")
    if not (reg_chars & 0x20000000 and clone_chars & 0x20000000):
        raise SystemExit("Registration or clone region is not executable")
    if original[reg_off:reg_off+5] != REG_PUSH_BYTES:
        raise SystemExit("(II)I registration no longer points to expected dispatcher")
    if original[other_off:other_off+5] != REG_PUSH_BYTES:
        raise SystemExit("String overload registration changed")
    if read_c_string(original, 0x00616278) != "(II)I":
        raise SystemExit("Unexpected (II)I signature")
    if read_c_string(original, 0x00616280) != "getInfo":
        raise SystemExit("Unexpected method name")
    if read_c_string(original, 0x00616288) != "java.util.resource.GameRef":
        raise SystemExit("Unexpected registered class")
    if read_c_string(original, 0x006162A4) != "(ILjava.lang.String;)I":
        raise SystemExit("Unexpected String overload signature")
    if clone_room < fn_size or original[clone_off:clone_off+fn_size] != bytes(fn_size):
        raise SystemExit("Clone code cave is not large enough or not empty")

    function = bytearray(original[fn_off:fn_off+fn_size])
    for off, (_, expected) in CALL_FIXUPS.items():
        if function[off:off+5] != expected:
            raise SystemExit(f"Unexpected relative CALL bytes at offset {off:#x}")
    if function[0xED:0xF4] != bytes.fromhex("8B 57 4C 52 FF 50 3C"):
        raise SystemExit("Dispatcher's virtual-call tail changed")

    reloc_rva, reloc_size, reloc_end_off, all_relocs = parse_relocations(original)
    actual = sorted(va for va, typ in all_relocs
                    if DISPATCHER_VA <= va < DISPATCHER_END_VA and typ == 3)
    if actual != EXPECTED_RELOC_VAS:
        raise SystemExit(f"Unexpected dispatcher HIGHLOW relocations: {actual!r}")

    clone_rva = CLONE_VA - IMAGE_BASE
    clone_function = bytearray(function)
    for off, (target, _) in CALL_FIXUPS.items():
        next_va = CLONE_VA + off + 5
        clone_function[off:off+5] = b"\xE8" + struct.pack("<i", target - next_va)

    patched = bytearray(original)
    patched[reg_off+1:reg_off+5] = struct.pack("<I", CLONE_VA)
    patched[clone_off:clone_off+fn_size] = clone_function

    # Replicate every absolute-address relocation inside the cloned routine.
    entries = sorted(0x3000 | ((clone_rva + (va - DISPATCHER_VA)) & 0x0FFF)
                     for va in EXPECTED_RELOC_VAS)
    if len(entries) % 2:
        entries.append(0)  # IMAGE_REL_BASED_ABSOLUTE aligns the block to 4 bytes
    block_size = 8 + 2 * len(entries)
    block = struct.pack("<II", clone_rva & 0xFFFFF000, block_size)
    block += struct.pack("<" + "H" * len(entries), *entries)
    if reloc_end_off + len(block) > len(patched):
        raise SystemExit("No room to append clone relocations")
    if patched[reloc_end_off:reloc_end_off+len(block)] != bytes(len(block)):
        raise SystemExit("Relocation append area is not empty")
    patched[reloc_end_off:reloc_end_off+len(block)] = block
    p32(patched, pe + 24 + 96 + 5 * 8 + 4, reloc_size + len(block))

    for va, expected in WRITER_GUARDS.items():
        off, _, _, _ = va_to_offset(original, va)
        if original[off:off+len(expected)] != expected:
            raise SystemExit(f"Reflection writer guard changed at {va:#010x}")

    out = args.output.resolve() if args.output else src.with_name(src.stem + "_getinfo_clone_probe.exe")
    if out == src:
        raise SystemExit("Refusing to overwrite source executable")
    out.parent.mkdir(parents=True, exist_ok=True)
    out.write_bytes(patched)

    check = out.read_bytes()
    if struct.unpack_from("<I", check, reg_off+1)[0] != CLONE_VA:
        raise SystemExit("Registration pointer verification failed")
    if check[clone_off:clone_off+fn_size] != clone_function:
        raise SystemExit("Cloned function verification failed")
    if check[other_off:other_off+5] != REG_PUSH_BYTES:
        raise SystemExit("String overload registration was modified")
    new_rva, new_size, _, check_relocs = parse_relocations(check)
    expected_clone_relocs = sorted(CLONE_VA + (va - DISPATCHER_VA) for va in EXPECTED_RELOC_VAS)
    found_clone_relocs = sorted(va for va, typ in check_relocs
                                if typ == 3 and CLONE_VA <= va < CLONE_VA + fn_size)
    if new_rva != reloc_rva or new_size != reloc_size + len(block):
        raise SystemExit("Relocation directory extension failed")
    if found_clone_relocs != expected_clone_relocs:
        raise SystemExit("Not all cloned HIGHLOW relocations were registered")

    print(f"Output: {out}")
    print(f"Base SHA-256: {digest}")
    print(f"Output SHA-256: {hashlib.sha256(check).hexdigest()}")
    print(f"Cloned dispatcher: {DISPATCHER_VA:#010x}-{DISPATCHER_END_VA-1:#010x} -> {CLONE_VA:#010x}")
    print("All seven relative CALLs were retargeted to their original destinations.")
    print("All seven HIGHLOW relocations were duplicated for the cloned code.")
    print("Only getInfo(II)I is redirected; the String overload is untouched.")
    print("No BBox interception or native object reads are present in this stage.")
    print("Reflection diagnostic writer suppression is retained.")
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except (OSError, ValueError, struct.error) as exc:
        print(f"ERROR: {exc}", file=sys.stderr)
        raise SystemExit(2)
