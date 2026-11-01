#!/usr/bin/env python3
"""Disable only the Reflection native file writers in one pinned SLRR EXE.

The ResourceRef.set reflection handler and its runtime behavior are preserved.
Only the two routines that create/write grefl.log and grefl_dump.bin are turned
into immediate returns. The input is never overwritten.
"""
from __future__ import annotations

import argparse
import hashlib
import struct
from pathlib import Path

EXPECTED_SHA256 = "0aca820d43a18e3c1559b6ba10f2ee158aad5f2b1577aea1a0c763d56ccf0d16"
IMAGE_BASE = 0x00400000
SECTION_VA = 0x007C2000
SECTION_RAW = 0x0024E000

# Confirmed from the exact native EXE's x86 disassembly:
# Both 0x7DE081 and 0x7DE2D4 open/write grefl_dump.bin via the same path buffer.
# 0x7DE4BF is the separate grefl.log writer.
PATCHES = {
    0x007DE081: (
        bytes.fromhex("53 56 57 55"),
        bytes.fromhex("C3 90 90 90"),
        "grefl_dump.bin primary writer",
    ),
    0x007DE2D4: (
        bytes.fromhex("53 56 57 55"),
        bytes.fromhex("C3 90 90 90"),
        "grefl_dump.bin secondary writer",
    ),
    0x007DE4BF: (
        bytes.fromhex("53 56 57 89 C6"),
        bytes.fromhex("C3 90 90 90 90"),
        "grefl.log writer",
    ),
}


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("input", type=Path, help="exact StreetLegal_Redline_native.exe")
    parser.add_argument("output", type=Path, nargs="?", help="output path; defaults to *_silent.exe")
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
    pe_offset = struct.unpack_from("<I", original, 0x3C)[0]
    if original[pe_offset:pe_offset + 4] != bytes((0x50, 0x45, 0, 0)):
        raise SystemExit("PE signature is missing")
    machine = struct.unpack_from("<H", original, pe_offset + 4)[0]
    magic = struct.unpack_from("<H", original, pe_offset + 24)[0]
    image_base = struct.unpack_from("<I", original, pe_offset + 24 + 28)[0]
    if machine != 0x014C or magic != 0x010B or image_base != IMAGE_BASE:
        raise SystemExit("Expected the pinned x86 PE32 executable at image base 0x00400000")

    patched = bytearray(original)
    for virtual_address, (expected, replacement, label) in PATCHES.items():
        file_offset = SECTION_RAW + (virtual_address - SECTION_VA)
        actual = original[file_offset:file_offset + len(expected)]
        if actual != expected:
            raise SystemExit(
                f"Unexpected bytes at {virtual_address:#010x} ({label}): {actual.hex(' ')}"
            )
        patched[file_offset:file_offset + len(replacement)] = replacement
        print(f"Disabled {label}: VA {virtual_address:#010x}, file offset {file_offset:#x}")

    output = args.output.resolve() if args.output else source.with_name(source.stem + "_silent.exe")
    if output == source:
        raise SystemExit("Output path must differ from input")
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_bytes(patched)

    print(f"Input SHA-256:  {digest}")
    print(f"Output SHA-256: {hashlib.sha256(patched).hexdigest()}")
    print(f"Wrote: {output}")
    print("Reflection set(I)V handler and native registry are unchanged.")
    print("Runtime test is still required in SLRR.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
