#!/usr/bin/env python3
"""Patch only the existing PartPreview diagnostic code cave with second-bone reads.

Input must be the user's verified component-scale EXE:
StreetLegal_Redline_native_live_aabb_component_scale.exe
SHA-256: f94756966b54bbd3728007d1f93e7e8c0cef3fc0b04e53052840e551eaa743ec

The script preserves the existing dispatcher jump and replaces bytes only inside
the already-installed bone_upper_query code cave at VA 0x007E2B10.
"""
from __future__ import annotations

import argparse
import hashlib
import struct
import subprocess
import tempfile
from pathlib import Path

BASE_SHA256 = "f94756966b54bbd3728007d1f93e7e8c0cef3fc0b04e53052840e551eaa743ec"
IMAGE_BASE = 0x00400000
DISPATCH_VA = 0x007E17C7
CAVE_VA = 0x007E2B10

ROOT = Path(__file__).resolve().parent
SOURCE = ROOT / "bone_upper_query.S"


def u16(data: bytes, off: int) -> int:
    return struct.unpack_from("<H", data, off)[0]


def u32(data: bytes, off: int) -> int:
    return struct.unpack_from("<I", data, off)[0]


def sha(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


def pe_sections(data: bytes):
    if data[:2] != b"MZ":
        raise ValueError("Input is not an MZ executable")
    pe = u32(data, 0x3C)
    if data[pe:pe + 4] != b"PE\0\0":
        raise ValueError("PE signature is missing")
    if u16(data, pe + 4) != 0x014C or u16(data, pe + 24) != 0x010B:
        raise ValueError("Expected PE32/i386")
    image_base = u32(data, pe + 24 + 28)
    count = u16(data, pe + 6)
    table = pe + 24 + u16(data, pe + 20)
    sections = {}
    for i in range(count):
        off = table + i * 40
        name = data[off:off + 8].split(b"\0", 1)[0].decode("ascii", "replace")
        vs, va, raw_size, raw_ptr = struct.unpack_from("<IIII", data, off + 8)
        characteristics = u32(data, off + 36)
        sections[name] = (vs, va, raw_size, raw_ptr, characteristics)
    return image_base, sections


def va_to_offset(image_base: int, sections, va: int):
    rva = va - image_base
    for name, (vs, sec_va, raw_size, raw_ptr, characteristics) in sections.items():
        if sec_va <= rva < sec_va + raw_size:
            delta = rva - sec_va
            return raw_ptr + delta, name, raw_size - delta
    raise ValueError(f"VA {va:#010x} is not backed by a section")


def main() -> int:
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument("input", type=Path, help="Verified F947 component-scale EXE")
    ap.add_argument("output", type=Path, help="New second-bone diagnostic EXE")
    args = ap.parse_args()
    source = args.input.resolve()
    output = args.output.resolve()
    if source == output:
        raise SystemExit("Refusing to overwrite input EXE")
    if not source.is_file() or not SOURCE.is_file():
        raise SystemExit("Input EXE or bone_upper_query.S is missing")

    original = source.read_bytes()
    digest = sha(original)
    if digest != BASE_SHA256:
        raise SystemExit(f"Wrong input SHA-256: {digest}; expected {BASE_SHA256}")

    image_base, sections = pe_sections(original)
    if image_base != IMAGE_BASE or ".reloc" not in sections:
        raise SystemExit("Unexpected PE image base or missing .reloc section")

    dispatch_off, dispatch_sec, _ = va_to_offset(image_base, sections, DISPATCH_VA)
    cave_off, cave_sec, cave_room = va_to_offset(image_base, sections, CAVE_VA)
    if dispatch_sec != ".reloc" or cave_sec != ".reloc":
        raise SystemExit("Expected dispatcher and code cave in .reloc")

    rel32 = CAVE_VA - (DISPATCH_VA + 5)
    expected_jump = b"\xE9" + struct.pack("<i", rel32) + b"\x90"
    if original[dispatch_off:dispatch_off + 6] != expected_jump:
        raise SystemExit("Existing dispatcher no longer jumps to the expected bone_upper_query cave")

    with tempfile.TemporaryDirectory(prefix="slrr_second_bone_") as tmp:
        obj = Path(tmp) / "bone_upper_query.o"
        raw = Path(tmp) / "bone_upper_query.bin"
        subprocess.run(["as", "--32", "-o", str(obj), str(SOURCE)], check=True)
        subprocess.run([
            "ld", "-m", "elf_i386", f"-Ttext={CAVE_VA:#x}",
            "-e", "bone_upper_gate", "--oformat", "binary",
            "-o", str(raw), str(obj)
        ], check=True)
        code = raw.read_bytes()

    if not code or len(code) > cave_room:
        raise SystemExit(f"Second-bone code does not fit in .reloc cave ({len(code)} bytes)")

    patched = bytearray(original)
    patched[cave_off:cave_off + len(code)] = code

    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_bytes(patched)
    check = output.read_bytes()

    allowed = set(range(cave_off, cave_off + len(code)))
    changed = [i for i, (a, b) in enumerate(zip(original, check)) if a != b]
    if any(i not in allowed for i in changed):
        output.unlink(missing_ok=True)
        raise SystemExit("Unexpected bytes changed outside the existing diagnostic code cave")
    if check[dispatch_off:dispatch_off + 6] != expected_jump:
        output.unlink(missing_ok=True)
        raise SystemExit("Dispatcher jump changed unexpectedly")
    if check[cave_off:cave_off + len(code)] != code:
        output.unlink(missing_ok=True)
        raise SystemExit("Second-bone code verification failed")

    print(f"Input SHA-256:  {digest}")
    print(f"Output SHA-256: {sha(check)}")
    print(f"Code cave VA:   {CAVE_VA:#010x}; code size {len(code)} bytes")
    print("Dispatcher:    unchanged; still targets the existing code cave")
    print("Patch scope:   code cave only; all other EXE bytes preserved")
    print("Queries:       516 pointer, 517..518 name, 519..534 matrix, 535 next node")
    print(f"Changed bytes: {len(changed)}; all within the verified code cave")
    print(f"Output: {output}")
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except (OSError, ValueError, struct.error, subprocess.CalledProcessError) as exc:
        print(f"ERROR: {exc}")
        raise SystemExit(2)
