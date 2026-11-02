#!/usr/bin/env python3
"""Patch the tested render-matrix diagnostic EXE with extended INSTANCE_RENDER dumps.

This is a deliberately narrow binary patch: it replaces the type-3 diagnostic
dispatcher entry with a jump into an executable zero-filled .reloc code cave.
It keeps the AABB scan, R05 and R06 code already present in the base EXE.
"""
from __future__ import annotations

import argparse
import hashlib
import struct
import subprocess
import tempfile
from pathlib import Path

BASE_SHA256 = "2ad62dd389fb68524c0c8faa86da7b39b766346bd5a5dcf4d54ee3e64b34fda5"
IMAGE_BASE = 0x00400000
DISPATCH_VA = 0x005E6EEB
DISPATCH_EXPECTED = bytes.fromhex("8B 4D 0C 83 7E 4C 03 75")
CAVE_VA = 0x007E16D4
RETURN_RAW_VA = 0x005E6EC5
INVALID_VA = 0x005E6EDD
RECORD_DIAG_VA = 0x005E6F40
MESH_SCAN_VA = 0x005E693A
ROOT = Path(__file__).resolve().parent
SOURCE = ROOT / "render_diag_dispatch.S"


def u16(data: bytes, offset: int) -> int:
    return struct.unpack_from("<H", data, offset)[0]


def u32(data: bytes, offset: int) -> int:
    return struct.unpack_from("<I", data, offset)[0]


def sha256(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


def pe_sections(data: bytes):
    if data[:2] != b"MZ":
        raise ValueError("Input is not an MZ executable")
    pe = u32(data, 0x3C)
    if data[pe:pe + 4] != b"PE\\0\\0":
        raise ValueError("PE signature is missing")
    if u16(data, pe + 4) != 0x014C or u16(data, pe + 24) != 0x010B:
        raise ValueError("Expected PE32/i386")
    image_base = u32(data, pe + 24 + 28)
    count = u16(data, pe + 6)
    table = pe + 24 + u16(data, pe + 20)
    sections = {}
    for index in range(count):
        off = table + index * 40
        name = data[off:off + 8].split(b"\\0", 1)[0].decode("ascii", "replace")
        virtual_size, virtual_address, raw_size, raw_pointer = struct.unpack_from(
            "<IIII", data, off + 8
        )
        characteristics = u32(data, off + 36)
        sections[name] = {
            "header": off,
            "virtual_size": virtual_size,
            "virtual_address": virtual_address,
            "raw_size": raw_size,
            "raw_pointer": raw_pointer,
            "characteristics": characteristics,
        }
    return image_base, sections


def va_to_offset(image_base: int, sections, va: int):
    rva = va - image_base
    for name, section in sections.items():
        start = section["virtual_address"]
        size = section["raw_size"]
        if start <= rva < start + size:
            delta = rva - start
            return section["raw_pointer"] + delta, name, size - delta
    raise ValueError(f"VA {va:#010x} has no raw backing")


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("input", type=Path, help="tested render-matrix diagnostic EXE")
    parser.add_argument("output", type=Path, help="separate extended diagnostic EXE")
    args = parser.parse_args()
    source = args.input.resolve()
    output = args.output.resolve()
    if source == output:
        raise SystemExit("Refusing to overwrite the input EXE")
    if not source.is_file() or not SOURCE.is_file():
        raise SystemExit("Input EXE or render_diag_dispatch.S is missing")

    original = source.read_bytes()
    digest = sha256(original)
    if digest != BASE_SHA256:
        raise SystemExit(f"Wrong base SHA-256: {digest}; expected {BASE_SHA256}")

    image_base, sections = pe_sections(original)
    if image_base != IMAGE_BASE:
        raise SystemExit(f"Unexpected image base: {image_base:#x}")
    dispatch_off, dispatch_section, _ = va_to_offset(image_base, sections, DISPATCH_VA)
    cave_off, cave_section, cave_room = va_to_offset(image_base, sections, CAVE_VA)
    if dispatch_section != ".text" or cave_section != ".reloc":
        raise SystemExit("Unexpected dispatch/cave section mapping")
    if not (sections[".reloc"]["characteristics"] & 0x20000000):
        raise SystemExit(".reloc is not executable in this base EXE")
    if original[dispatch_off:dispatch_off + len(DISPATCH_EXPECTED)] != DISPATCH_EXPECTED:
        raise SystemExit("The expected render diagnostic dispatch bytes have changed")

    with tempfile.TemporaryDirectory(prefix="slrr_render_diag_") as temp:
        obj = Path(temp) / "render_diag_dispatch.o"
        binary = Path(temp) / "render_diag_dispatch.bin"
        subprocess.run(["as", "--32", "-o", str(obj), str(SOURCE)], check=True)
        subprocess.run([
            "ld", "-m", "elf_i386", f"-Ttext={CAVE_VA:#x}",
            "-e", "render_diag_dispatch", "--oformat", "binary",
            "-o", str(binary), str(obj)
        ], check=True)
        code = binary.read_bytes()

    if not code or len(code) > cave_room:
        raise SystemExit(f"Extension code does not fit in cave ({len(code)} bytes)")
    if original[cave_off:cave_off + len(code)] != bytes(len(code)):
        raise SystemExit("The selected .reloc code cave is not zero-filled")

    detour = b"\\xE9" + struct.pack("<i", CAVE_VA - (DISPATCH_VA + 5))
    patched = bytearray(original)
    patched[dispatch_off:dispatch_off + 5] = detour
    patched[cave_off:cave_off + len(code)] = code

    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_bytes(patched)
    check = output.read_bytes()
    changed = [
        index for index, (old, new) in enumerate(zip(original, check))
        if old != new
    ]
    allowed = set(range(dispatch_off, dispatch_off + 5))
    allowed.update(range(cave_off, cave_off + len(code)))
    if any(index not in allowed for index in changed):
        output.unlink(missing_ok=True)
        raise SystemExit("Unexpected bytes changed outside dispatch and .reloc code cave")
    if check[dispatch_off:dispatch_off + 5] != detour:
        output.unlink(missing_ok=True)
        raise SystemExit("Dispatch detour verification failed")
    if check[cave_off:cave_off + len(code)] != code:
        output.unlink(missing_ok=True)
        raise SystemExit("Extension code verification failed")

    print(f"Input SHA-256:  {digest}")
    print(f"Output SHA-256: {sha256(check)}")
    print(f"Dispatch:      {DISPATCH_VA:#010x} -> {CAVE_VA:#010x}")
    print(f"Extension:     {len(code)} bytes in executable .reloc")
    print(f"Changed bytes: {len(changed)}; only dispatch + extension code cave")
    print("Static verification passed; runtime validation in SLRR is still required.")
    print(f"Output: {output}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
