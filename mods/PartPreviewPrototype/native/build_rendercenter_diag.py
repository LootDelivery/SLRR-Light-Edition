#!/usr/bin/env python3
"""Build the read-only bone-name probe and nested-mesh center query into the known-good payload diagnostic EXE.

Adds INSTANCE_RENDER AABB_QUERY axes 489..498 for an exact-case "Bone00" node
pointer and the nine matrix terms used by the center transform. Keeps axes
500..502 for the world-space nested-mesh center as signed fixed-point *1000.
The pinned source EXE is preserved.
"""
from __future__ import annotations
import argparse
import hashlib
import struct
import subprocess
import tempfile
from pathlib import Path

BASE_SHA256 = "38e09d0dbdd1c8d8afa1f3d0365b8a629b91e0fa8968c91ec53ccbc69bacdc88"
IMAGE_BASE = 0x00400000
DISPATCH_VA = 0x005E6EEB
CAVE_VA = 0x007E16D4
OLD_CODE_SHA256 = "db58da42d4bc69859afc339ef49453728ba0acae43f12e644180b4d7d1fa3250"
OLD_CODE_SIZE = 944
ROOT = Path(__file__).resolve().parent
SOURCE = ROOT / "render_diag_dispatch.S"
EXPECTED_DETOUR = b"\xE9" + struct.pack("<i", CAVE_VA - (DISPATCH_VA + 5))


def u16(data: bytes, off: int) -> int:
    return struct.unpack_from("<H", data, off)[0]


def u32(data: bytes, off: int) -> int:
    return struct.unpack_from("<I", data, off)[0]


def sha(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


def pe_sections(data: bytes):
    if data[:2] != b"MZ":
        raise ValueError("Not an MZ executable")
    pe = u32(data, 0x3C)
    if data[pe:pe + 4] != b"PE\0\0":
        raise ValueError("Missing PE signature")
    if u16(data, pe + 4) != 0x14C or u16(data, pe + 24) != 0x10B:
        raise ValueError("Expected PE32/i386")
    image_base = u32(data, pe + 24 + 28)
    count = u16(data, pe + 6)
    optional_size = u16(data, pe + 20)
    table = pe + 24 + optional_size
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
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("input", type=Path, help="known-good render-payload diagnostic EXE")
    parser.add_argument("output", type=Path, help="separate nested-center diagnostic EXE")
    args = parser.parse_args()
    source = args.input.resolve()
    output = args.output.resolve()
    if source == output:
        raise SystemExit("Refusing to overwrite input EXE")
    if not source.is_file() or not SOURCE.is_file():
        raise SystemExit("Input EXE or render_diag_dispatch.S is missing")

    original = source.read_bytes()
    digest = sha(original)
    if digest != BASE_SHA256:
        raise SystemExit(f"Wrong base SHA-256: {digest}; expected {BASE_SHA256}")

    image_base, sections = pe_sections(original)
    if image_base != IMAGE_BASE:
        raise SystemExit(f"Unexpected image base: {image_base:#x}")
    dispatch_off, dispatch_section, _ = va_to_offset(image_base, sections, DISPATCH_VA)
    cave_off, cave_section, cave_room = va_to_offset(image_base, sections, CAVE_VA)
    if dispatch_section != ".text" or cave_section != ".reloc":
        raise SystemExit("Unexpected dispatcher/cave section mapping")
    if original[dispatch_off:dispatch_off + 5] != EXPECTED_DETOUR:
        raise SystemExit("Existing dispatcher detour differs")
    if sha(original[cave_off:cave_off + OLD_CODE_SIZE]) != OLD_CODE_SHA256:
        raise SystemExit("Existing payload diagnostic code differs from expected base")

    with tempfile.TemporaryDirectory(prefix="slrr_rendercenter_") as temp:
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
        raise SystemExit(f"New dispatcher does not fit in .reloc ({len(code)} bytes)")
    if len(code) < OLD_CODE_SIZE:
        raise SystemExit("New dispatcher unexpectedly became shorter than the base code")
    if original[cave_off + OLD_CODE_SIZE:cave_off + len(code)] != bytes(len(code) - OLD_CODE_SIZE):
        raise SystemExit("Dispatcher growth area is not zero-filled")

    patched = bytearray(original)
    patched[cave_off:cave_off + len(code)] = code
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_bytes(patched)
    check = output.read_bytes()

    changed = [
        index for index, (old, new) in enumerate(zip(original, check))
        if old != new
    ]
    allowed = set(range(cave_off, cave_off + len(code)))
    if any(index not in allowed for index in changed):
        output.unlink(missing_ok=True)
        raise SystemExit("Unexpected bytes changed outside the existing .reloc dispatcher")
    if check[dispatch_off:dispatch_off + 5] != EXPECTED_DETOUR:
        output.unlink(missing_ok=True)
        raise SystemExit("Existing dispatcher detour changed")
    if check[cave_off:cave_off + len(code)] != code:
        output.unlink(missing_ok=True)
        raise SystemExit("Post-write dispatcher verification failed")

    print(f"Input SHA-256:  {digest}")
    print(f"Output SHA-256: {sha(check)}")
    print(f"Dispatcher VA:  {CAVE_VA:#010x}; code size {len(code)} bytes (base {OLD_CODE_SIZE})")
    print(f"Changed bytes:  {len(changed)}; all confined to the existing .reloc dispatcher")
    print("New axes: 489=uppercase Bone00 node pointer; 490..498=its center-transform matrix terms.")
    print("Center axes remain 500=X, 501=Y, 502=Z; signed fixed-point *1000.")
    print("Static verification passed; runtime validation in SLRR is still required.")
    print(f"Output: {output}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
