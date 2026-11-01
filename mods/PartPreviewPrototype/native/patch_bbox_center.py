#!/usr/bin/env python3
"""Build a guarded SLRR Bounding Box probe EXE from the verified silent_v2 build.

Only the reserved GameRef.getInfo(II)I query 0x584F4242 (+ axis 0..2) is
intercepted. Every ordinary getInfo call replays the original virtual call.
The input file is never overwritten.
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
    "66 90 81 3C 24 42 42 4F 58 75 52 83 7C 24 04 02 77 3E 51 8D 81 80 00 00 00 6A 10"
    "50 FF 15 24 71 5E 00 59 85 C0 75 29 83 B9 80 00 00 00 01 75 20 8B 54 24 04 D9"
    "84 91 84 00 00 00 D8 0D DC F9 7D 00 83 EC 04 DB 1C 24 58 83 C4 08 E9 22 E5 C9 FF"
    "B8 00 00 00 80 83 C4 08 E9 15 E5 C9 FF 8B 57 4C 52 FF 50 3C E9 09 E5 C9 FF 90 00"
    "40 1C 46"
)

# The three Reflection diagnostic file writers disabled by the working silent_v2.
SILENT_WRITER_GUARDS = {
    0x007DE081: bytes.fromhex("C3 90 90 90"),
    0x007DE2D4: bytes.fromhex("C3 90 90 90"),
    0x007DE4BF: bytes.fromhex("C3 90 90 90 90"),
}


def u16(data: bytes, offset: int) -> int:
    return struct.unpack_from("<H", data, offset)[0]


def u32(data: bytes, offset: int) -> int:
    return struct.unpack_from("<I", data, offset)[0]


def va_to_offset(data: bytes, va: int, image_base: int) -> tuple[int, str]:
    rva = va - image_base
    pe = u32(data, 0x3C)
    section_count = u16(data, pe + 6)
    optional_size = u16(data, pe + 20)
    section_table = pe + 24 + optional_size
    for index in range(section_count):
        section = section_table + index * 40
        name = data[section:section + 8].split(b"\0", 1)[0].decode("ascii", "replace")
        virtual_size, virtual_address, raw_size, raw_pointer = struct.unpack_from(
            "<IIII", data, section + 8
        )
        if virtual_address <= rva < virtual_address + max(virtual_size, raw_size):
            delta = rva - virtual_address
            if delta >= raw_size:
                raise ValueError(f"VA 0x{va:08X} has no raw-file data in section {name}")
            return raw_pointer + delta, name
    raise ValueError(f"VA 0x{va:08X} is not mapped by a PE section")


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("input", type=Path, help="working StreetLegal_Redline_native_silent_v2.exe")
    parser.add_argument("output", type=Path, nargs="?", help="output EXE path")
    args = parser.parse_args()

    source = args.input.resolve()
    if not source.is_file():
        parser.error(f"input file does not exist: {source}")
    original = source.read_bytes()
    digest = hashlib.sha256(original).hexdigest()
    if digest != EXPECTED_SHA256:
        raise SystemExit(
            "Refusing to patch an unknown executable build.\n"
            f"Expected SHA-256: {EXPECTED_SHA256}\n"
            f"Actual SHA-256:   {digest}"
        )

    if original[:2] != b"MZ":
        raise SystemExit("Input is not an MZ/PE executable")
    pe = u32(original, 0x3C)
    if original[pe:pe + 4] != b"PE\0\0":
        raise SystemExit("PE signature is missing")
    if u16(original, pe + 4) != 0x014C or u16(original, pe + 24) != 0x010B:
        raise SystemExit("Expected x86 PE32 executable")
    image_base = u32(original, pe + 24 + 28)
    if image_base != IMAGE_BASE:
        raise SystemExit(f"Unexpected image base 0x{image_base:08X}")

    site_off, site_section = va_to_offset(original, HOOK_SITE_VA, image_base)
    cave_off, cave_section = va_to_offset(original, HOOK_CAVE_VA, image_base)
    if site_section != ".text" or cave_section != ".reloc":
        raise SystemExit("Unexpected PE section layout for the hook")
    if original[site_off:site_off + len(HOOK_SITE_BYTES)] != HOOK_SITE_BYTES:
        raise SystemExit("getInfo virtual-call tail does not match the expected bytes")

    # The native method table must still contain the original GameRef overloads.
    if original[0x89666:0x8966B] != bytes.fromhex("68 F0 DD 47 00"):
        raise SystemExit("GameRef.getInfo(II)I registration changed")
    if original[0x89685:0x8968A] != bytes.fromhex("68 F0 DD 47 00"):
        raise SystemExit("GameRef.getInfo(ILjava.lang.String;)I registration changed")

    for va, expected in SILENT_WRITER_GUARDS.items():
        offset, _ = va_to_offset(original, va, image_base)
        if original[offset:offset + len(expected)] != expected:
            raise SystemExit(f"Reflection writer guard failed at VA 0x{va:08X}")

    if any(original[cave_off:cave_off + len(HOOK_BYTES)]):
        raise SystemExit("The selected .reloc code cave is not empty")

    displacement = HOOK_CAVE_VA - (HOOK_SITE_VA + 5)
    detour = b"\xE9" + struct.pack("<i", displacement) + b"\x90\x90"
    patched = bytearray(original)
    patched[site_off:site_off + len(detour)] = detour
    patched[cave_off:cave_off + len(HOOK_BYTES)] = HOOK_BYTES

    output = args.output.resolve() if args.output else source.with_name(
        source.stem + "_bbox_test.exe"
    )
    if output == source:
        raise SystemExit("Output path must differ from input")
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_bytes(patched)

    print(f"Wrote:            {output}")
    print(f"Input SHA-256:    {digest}")
    print(f"Output SHA-256:   {hashlib.sha256(patched).hexdigest()}")
    print(f"Hook site:        0x{HOOK_SITE_VA:08X} ({site_section})")
    print(f"Hook cave:        0x{HOOK_CAVE_VA:08X} ({cave_section}), {len(HOOK_BYTES)} bytes")
    print("Reflection file writers remain disabled.")
    print("Runtime validation is required before enabling pivot compensation.")
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except (OSError, ValueError, struct.error) as exc:
        print(f"ERROR: {exc}", file=sys.stderr)
        raise SystemExit(2)
