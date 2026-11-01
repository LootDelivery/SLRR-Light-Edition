#!/usr/bin/env python3
"""Create an experimental SLRR native EXE with a read-only AABB-center query.

This patch is pinned to one exact StreetLegal_Redline_native.exe build.
It does not modify the original input. Runtime behavior still requires testing
in a disposable game installation.
"""
from __future__ import annotations

import argparse
import hashlib
import struct
import sys
from pathlib import Path

EXPECTED_SHA256 = "0aca820d43a18e3c1559b6ba10f2ee158aad5f2b1577aea1a0c763d56ccf0d16"
IMAGE_BASE = 0x00400000
ENTRY_VA = 0x0047DECE
HOOK_VA = 0x007DF972
SCALE_VA = 0x007DF9C0
EXPECTED_ENTRY = bytes.fromhex("8b54240c8b480c")
HOOK_CODE = bytes.fromhex(
    "6690"
    "8b4c240c"
    "81e942424f58"
    "83f902"
    "772f"
    "8b500c"
    "83ba8000000001"
    "7519"
    "d9848a84000000"
    "d80dc0f97d00"
    "83ec04"
    "db1c24"
    "58"
    "e93ce5c9ff"
    "b800000080"
    "e932e5c9ff"
    "8b54240c"
    "8b480c"
    "e917e5c9ff"
    "6690"
    "00401c46"  # float32 10000.0 at VA 0x007DF9C0
)


def u16(data: bytes, offset: int) -> int:
    return struct.unpack_from("<H", data, offset)[0]


def u32(data: bytes, offset: int) -> int:
    return struct.unpack_from("<I", data, offset)[0]


def rva_to_offset(data: bytes, rva: int) -> tuple[int, str, int]:
    pe = u32(data, 0x3C)
    section_count = u16(data, pe + 6)
    optional_size = u16(data, pe + 20)
    section_table = pe + 24 + optional_size
    for i in range(section_count):
        off = section_table + i * 40
        name = data[off:off + 8].split(b"\0", 1)[0].decode("ascii", "replace")
        virtual_size, virtual_address, raw_size, raw_pointer = struct.unpack_from(
            "<IIII", data, off + 8
        )
        extent = max(virtual_size, raw_size)
        if virtual_address <= rva < virtual_address + extent:
            delta = rva - virtual_address
            if delta >= raw_size:
                raise ValueError(
                    f"RVA 0x{rva:X} has no raw-file backing in section {name}"
                )
            return raw_pointer + delta, name, raw_size - delta
    raise ValueError(f"RVA 0x{rva:X} is not inside a PE section")


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("input", type=Path, help="exact native EXE used as the patch base")
    parser.add_argument(
        "output", type=Path, nargs="?",
        help="output EXE path (default: *_bbox_test.exe)"
    )
    args = parser.parse_args()

    src = args.input.resolve()
    if not src.is_file():
        parser.error(f"input file does not exist: {src}")
    raw = src.read_bytes()
    digest = hashlib.sha256(raw).hexdigest()
    if digest != EXPECTED_SHA256:
        raise SystemExit(
            "Refusing to patch an unknown executable build.\n"
            f"Expected SHA-256: {EXPECTED_SHA256}\n"
            f"Actual SHA-256:   {digest}"
        )
    if raw[:2] != b"MZ":
        raise SystemExit("Input is not an MZ/PE executable")
    pe = u32(raw, 0x3C)
    if raw[pe:pe + 4] != b"PE\0\0":
        raise SystemExit("PE signature is missing")
    if u16(raw, pe + 4) != 0x014C or u16(raw, pe + 24) != 0x010B:
        raise SystemExit("Expected a 32-bit x86 PE32 executable")
    image_base = u32(raw, pe + 24 + 28)
    if image_base != IMAGE_BASE:
        raise SystemExit(f"Unexpected image base 0x{image_base:X}")

    entry_rva = ENTRY_VA - IMAGE_BASE
    hook_rva = HOOK_VA - IMAGE_BASE
    scale_rva = SCALE_VA - IMAGE_BASE
    entry_off, entry_section, _ = rva_to_offset(raw, entry_rva)
    hook_off, hook_section, hook_room = rva_to_offset(raw, hook_rva)
    scale_off, scale_section, _ = rva_to_offset(raw, scale_rva)

    if entry_section != ".text" or hook_section != ".reloc" or scale_section != ".reloc":
        raise SystemExit("Unexpected PE section mapping; refusing to patch")
    if raw[entry_off:entry_off + len(EXPECTED_ENTRY)] != EXPECTED_ENTRY:
        raise SystemExit("Native callback entry bytes do not match the expected build")
    if hook_room < len(HOOK_CODE):
        raise SystemExit("Not enough room in the target .reloc code cave")
    if any(raw[hook_off:hook_off + len(HOOK_CODE)]):
        raise SystemExit("The target code cave is not empty; refusing to overwrite it")
    if scale_off != hook_off + (SCALE_VA - HOOK_VA):
        raise SystemExit("Scale constant is not at the expected position")

    patched = bytearray(raw)
    rel = HOOK_VA - (ENTRY_VA + 5)
    patched[entry_off:entry_off + 7] = b"\xE9" + struct.pack("<i", rel) + b"\x90\x90"
    patched[hook_off:hook_off + len(HOOK_CODE)] = HOOK_CODE

    out = args.output.resolve() if args.output else src.with_name(
        src.stem + "_bbox_test.exe"
    )
    if out == src:
        raise SystemExit("Output path must not overwrite the original input")
    out.parent.mkdir(parents=True, exist_ok=True)
    out.write_bytes(patched)

    print(f"Patched test EXE: {out}")
    print(f"Input SHA-256:    {digest}")
    print(f"Output SHA-256:   {hashlib.sha256(patched).hexdigest()}")
    print(f"Entry patch:      file 0x{entry_off:X}, VA 0x{ENTRY_VA:X}")
    print(
        f"Hook payload:     file 0x{hook_off:X}, VA 0x{HOOK_VA:X}, "
        f"{len(HOOK_CODE)} bytes"
    )
    print("WARNING: static patch only; not yet runtime-tested in SLRR.")
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except (OSError, ValueError, struct.error) as exc:
        print(f"ERROR: {exc}", file=sys.stderr)
        raise SystemExit(2)
