#!/usr/bin/env python3
"""Build BBox query probe v2 from the user-tested GetInfo route-probe EXE.

This revision fixes the order of the two Java int arguments in the native
parser's output locals:
  query (first Java argument) -> [ESP+0x10]
  axis  (second Java argument) -> [ESP+0x0C]

Only the existing GetInfo trampoline slot is replaced. The input executable
is SHA-256 pinned and is never overwritten.
"""
from __future__ import annotations
import argparse
import hashlib
import struct
import sys
from pathlib import Path

INPUT_SHA256 = "8e8cde4bcb09f83dd0888d9cdc3d58248d8f6cbb1d9499eb4593e42eccd88e0d"
IMAGE_BASE = 0x00400000
REG_VA = 0x00489666
REG_EXPECTED = bytes.fromhex("68 72 F9 7D 00")
STRING_OVERLOAD_VA = 0x00489685
STRING_OVERLOAD_EXPECTED = bytes.fromhex("68 F0 DD 47 00")
THUNK_VA = 0x007DF972
ROUTE_THUNK = bytes.fromhex("E9 79 E4 C9 FF")
THUNK = bytes.fromhex("""
83ec085556578d44240c508b44241c8d4c2414518d542420525031ede87ddfc7ffe800000000598d8970e6e4ff8b098b5424285152e8a4b1c4ff83c418817c241042424f58756785c0744283780800743c8b700c85f67435837e4c05753683be800000000175348b54240c83fa027732d984968400000068e8030000da0c2483ec04db1c248b042483c408eb1ab800000080eb13b801000080eb0cb802000080eb05b8030000805f5e5d83c408c3e900e4c9ff
""")
THUNK_SHA256 = hashlib.sha256(THUNK).hexdigest()
WRITER_GUARDS = {
    0x007DE081: bytes.fromhex("C3 90 90 90"),
    0x007DE2D4: bytes.fromhex("C3 90 90 90"),
    0x007DE4BF: bytes.fromhex("C3 90 90 90 90"),
}

def u16(data: bytes, offset: int) -> int:
    return struct.unpack_from("<H", data, offset)[0]

def u32(data: bytes, offset: int) -> int:
    return struct.unpack_from("<I", data, offset)[0]

def va_to_offset(data: bytes, va: int) -> tuple[int, str, int, int]:
    pe = u32(data, 0x3C)
    image_base = u32(data, pe + 24 + 28)
    count = u16(data, pe + 6)
    optional_size = u16(data, pe + 20)
    section_table = pe + 24 + optional_size
    rva = va - image_base
    for index in range(count):
        offset = section_table + index * 40
        name = data[offset:offset + 8].split(b"\0", 1)[0].decode("ascii", "replace")
        virtual_size, virtual_address, raw_size, raw_pointer = struct.unpack_from("<IIII", data, offset + 8)
        characteristics = u32(data, offset + 36)
        if virtual_address <= rva < virtual_address + max(virtual_size, raw_size):
            delta = rva - virtual_address
            if delta >= raw_size:
                raise ValueError(f"VA {va:#010x} has no raw backing")
            return raw_pointer + delta, name, characteristics, raw_size - delta
    raise ValueError(f"VA {va:#010x} is not mapped")

def read_c_string(data: bytes, va: int) -> str:
    offset, _, _, _ = va_to_offset(data, va)
    end = data.find(b"\0", offset)
    if end < 0:
        raise ValueError(f"Unterminated string at {va:#010x}")
    return data[offset:end].decode("ascii", "replace")

def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("input", type=Path, help="user-tested getinfo_route_probe.exe")
    parser.add_argument("output", type=Path, nargs="?", help="separate experimental probe EXE")
    args = parser.parse_args()

    source = args.input.resolve()
    if not source.is_file():
        parser.error(f"Input file not found: {source}")
    original = source.read_bytes()
    digest = hashlib.sha256(original).hexdigest()
    if digest != INPUT_SHA256:
        raise SystemExit(f"Wrong input EXE. Expected {INPUT_SHA256}; got {digest}")

    if original[:2] != b"MZ":
        raise SystemExit("Input is not an MZ executable")
    pe = u32(original, 0x3C)
    if original[pe:pe + 4] != b"PE\0\0":
        raise SystemExit("PE signature is missing")
    if u16(original, pe + 4) != 0x014C or u16(original, pe + 24) != 0x010B:
        raise SystemExit("Expected x86 PE32")
    if u32(original, pe + 24 + 28) != IMAGE_BASE:
        raise SystemExit("Unexpected PE image base")

    reg, reg_section, reg_flags, _ = va_to_offset(original, REG_VA)
    string_overload, _, _, _ = va_to_offset(original, STRING_OVERLOAD_VA)
    cave, cave_section, cave_flags, cave_room = va_to_offset(original, THUNK_VA)
    if reg_section != ".text" or cave_section != ".reloc":
        raise SystemExit("Unexpected PE section mapping")
    if not (reg_flags & 0x20000000 and cave_flags & 0x20000000):
        raise SystemExit("Registration or thunk region is not executable")
    if original[reg:reg + 5] != REG_EXPECTED:
        raise SystemExit("Native registration no longer points to the known-good route thunk")
    if original[string_overload:string_overload + 5] != STRING_OVERLOAD_EXPECTED:
        raise SystemExit("String overload registration differs from the known-good route probe")
    if original[cave:cave + len(ROUTE_THUNK)] != ROUTE_THUNK:
        raise SystemExit("Expected pass-through JMP is missing from the thunk slot")
    if cave_room < len(THUNK):
        raise SystemExit("Not enough executable code-cave space")
    if original[cave + len(ROUTE_THUNK):cave + len(THUNK)] != bytes(len(THUNK) - len(ROUTE_THUNK)):
        raise SystemExit("Remaining code-cave bytes are not empty")

    for va, expected in WRITER_GUARDS.items():
        offset, _, _, _ = va_to_offset(original, va)
        if original[offset:offset + len(expected)] != expected:
            raise SystemExit(f"Reflection writer guard mismatch at {va:#010x}")
    for va, expected in (
        (0x00616278, "(II)I"),
        (0x00616280, "getInfo"),
        (0x00616288, "java.util.resource.GameRef"),
        (0x006162A4, "(ILjava.lang.String;)I"),
    ):
        if read_c_string(original, va) != expected:
            raise SystemExit(f"Unexpected native registration string at {va:#010x}")

    # Validate argument offsets and control-flow targets.
    if THUNK[0x3D:0x45] != bytes.fromhex("817c241042424f58"):
        raise SystemExit("Query comparison is not using first-argument local [ESP+0x10]")
    if THUNK[0x67:0x6B] != bytes.fromhex("8b54240c"):
        raise SystemExit("Axis load is not using second-argument local [ESP+0x0C]")
    query_resume = (THUNK_VA + len(THUNK) + struct.unpack_from("<i", THUNK, len(THUNK) - 4)[0]) & 0xFFFFFFFF
    if query_resume != 0x0047DE25:
        raise SystemExit(f"Unexpected normal-dispatch continuation: {query_resume:#010x}")

    output = args.output.resolve() if args.output else source.with_name(
        "StreetLegal_Redline_native_bboxcenter_query_probe_v2.exe"
    )
    if output == source:
        raise SystemExit("Refusing to overwrite the known-good input EXE")

    patched = bytearray(original)
    patched[cave:cave + len(THUNK)] = THUNK
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_bytes(patched)

    check = output.read_bytes()
    if check[cave:cave + len(THUNK)] != THUNK:
        raise SystemExit("Output thunk verification failed")
    if check[reg:reg + 5] != REG_EXPECTED:
        raise SystemExit("Native registration changed unexpectedly")
    if check[string_overload:string_overload + 5] != STRING_OVERLOAD_EXPECTED:
        raise SystemExit("String overload registration changed unexpectedly")
    if check[:cave] != original[:cave] or check[cave + len(THUNK):] != original[cave + len(THUNK):]:
        raise SystemExit("Unexpected changes outside the thunk slot")

    print(f"Input: {source}")
    print(f"Output: {output}")
    print(f"Input SHA-256:  {digest}")
    print(f"Output SHA-256: {hashlib.sha256(check).hexdigest()}")
    print(f"Thunk SHA-256:  {THUNK_SHA256}")
    print(f"Thunk: {len(THUNK)} bytes at {THUNK_VA:#010x}")
    print("Query check: [ESP+0x10] (first Java argument)")
    print("Axis load:   [ESP+0x0C] (second Java argument)")
    print("Only the existing 179-byte trampoline slot is replaced.")
    print("Static verification passed; in-game runtime test is still required.")
    return 0

if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except (OSError, ValueError, struct.error) as exc:
        print(f"ERROR: {exc}", file=sys.stderr)
        raise SystemExit(2)
