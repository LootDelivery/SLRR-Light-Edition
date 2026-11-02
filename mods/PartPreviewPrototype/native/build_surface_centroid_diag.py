#!/usr/bin/env python3
"""Build a read-only indexed-triangle surface-centroid query into the tested
SLRR nested-render AABB diagnostic EXE.

Adds INSTANCE_RENDER query axes 503..505. Existing axes 500..502 are preserved
and routed through a tiny gate that replays their displaced instructions.
The new centroid code is placed in a separately verified zero-filled .reloc
code cave. Input EXE is SHA-256 pinned and never overwritten.

Requires GNU binutils (as, ld) with ELF32/i386 support.
"""
from __future__ import annotations

import argparse
import hashlib
import struct
import subprocess
import tempfile
from pathlib import Path

BASE_SHA256 = "ebc2b46a976e00914f624f6937070bdac1d8dc8f6bb8e2538f74bd204244c0f8"
IMAGE_BASE = 0x00400000
DISPATCH_CMP_VA = 0x007E17D3
DISPATCH_CMP_EXPECTED = bytes.fromhex("81 F9 F6 01 00 00")  # cmp ecx, 502
AABB_ENTRY_VA = 0x007E1A3C
AABB_ENTRY_EXPECTED = bytes.fromhex("53 57 83 EC 50")      # push ebx; push edi; sub esp,0x50
CENTROID_CAVE_VA = 0x007E2268
AABB_RESUME_VA = AABB_ENTRY_VA + 5
ROOT = Path(__file__).resolve().parent
SOURCE = ROOT / "render_mesh_centroid.S"


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
    raise ValueError(f"VA {va:#010x} is not backed by a PE section")


def assemble(source: Path, base_va: int, work: Path) -> bytes:
    obj = work / "render_mesh_centroid.o"
    binary = work / "render_mesh_centroid.bin"
    subprocess.run(["as", "--32", "-o", str(obj), str(source)], check=True)
    subprocess.run([
        "ld", "-m", "elf_i386", f"-Ttext={base_va:#x}",
        "-e", "centroid_gate", "--oformat", "binary",
        "-o", str(binary), str(obj)
    ], check=True)
    return binary.read_bytes()


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("input", type=Path, help="tested rendercenter_v2 diagnostic EXE")
    parser.add_argument("output", type=Path, help="separate surface-centroid diagnostic EXE")
    args = parser.parse_args()
    source = args.input.resolve()
    output = args.output.resolve()
    if source == output:
        raise SystemExit("Refusing to overwrite input EXE")
    if not source.is_file() or not SOURCE.is_file():
        raise SystemExit("Input EXE or render_mesh_centroid.S is missing")

    original = source.read_bytes()
    digest = sha(original)
    if digest != BASE_SHA256:
        raise SystemExit(f"Wrong base SHA-256: {digest}; expected {BASE_SHA256}")

    image_base, sections = pe_sections(original)
    if image_base != IMAGE_BASE:
        raise SystemExit(f"Unexpected image base: {image_base:#x}")
    if ".reloc" not in sections:
        raise SystemExit(".reloc section is missing")

    dispatch_off, dispatch_section, _ = va_to_offset(image_base, sections, DISPATCH_CMP_VA)
    entry_off, entry_section, _ = va_to_offset(image_base, sections, AABB_ENTRY_VA)
    cave_off, cave_section, cave_room = va_to_offset(image_base, sections, CENTROID_CAVE_VA)
    if dispatch_section != ".reloc" or entry_section != ".reloc" or cave_section != ".reloc":
        raise SystemExit("Expected dispatcher, AABB handler and centroid cave in .reloc")
    if not (sections[".reloc"][4] & 0x20000000):
        raise SystemExit(".reloc is not marked executable")
    if not (sections[".reloc"][4] & 0x80000000):
        raise SystemExit(".reloc is not writable; the native scratch cache requires writable code-cave memory")
    if original[dispatch_off:dispatch_off + len(DISPATCH_CMP_EXPECTED)] != DISPATCH_CMP_EXPECTED:
        raise SystemExit("Dispatcher's 500..502 range check differs from the tested base")
    if original[entry_off:entry_off + len(AABB_ENTRY_EXPECTED)] != AABB_ENTRY_EXPECTED:
        raise SystemExit("AABB nested-center entry prologue differs from the tested base")

    with tempfile.TemporaryDirectory(prefix="slrr_surface_centroid_") as temp:
        code = assemble(SOURCE, CENTROID_CAVE_VA, Path(temp))

    if not code or len(code) > cave_room:
        raise SystemExit(f"Centroid code does not fit at cave ({len(code)} bytes)")
    if original[cave_off:cave_off + len(code)] != bytes(len(code)):
        raise SystemExit("Selected .reloc centroid code cave is not zero-filled")

    # Extend the reserved dispatcher axis limit 502 -> 505.
    new_cmp = bytes.fromhex("81 F9 F9 01 00 00")
    # Redirect the AABB handler entry to the centroid gate. The gate replays
    # the overwritten five-byte prologue for axes 500..502, then resumes AABB.
    rel32 = CENTROID_CAVE_VA - (AABB_ENTRY_VA + 5)
    detour = b"\xE9" + struct.pack("<i", rel32)

    patched = bytearray(original)
    patched[dispatch_off:dispatch_off + len(new_cmp)] = new_cmp
    patched[entry_off:entry_off + len(detour)] = detour
    patched[cave_off:cave_off + len(code)] = code

    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_bytes(patched)
    check = output.read_bytes()

    allowed = set(range(dispatch_off, dispatch_off + len(new_cmp)))
    allowed.update(range(entry_off, entry_off + len(detour)))
    allowed.update(range(cave_off, cave_off + len(code)))
    changed = [i for i, (a, b) in enumerate(zip(original, check)) if a != b]
    if any(i not in allowed for i in changed):
        output.unlink(missing_ok=True)
        raise SystemExit("Unexpected bytes changed outside range check, AABB entry and centroid cave")
    if check[dispatch_off:dispatch_off + len(new_cmp)] != new_cmp:
        output.unlink(missing_ok=True)
        raise SystemExit("Dispatcher range patch verification failed")
    if check[entry_off:entry_off + len(detour)] != detour:
        output.unlink(missing_ok=True)
        raise SystemExit("AABB handler redirect verification failed")
    if check[cave_off:cave_off + len(code)] != code:
        output.unlink(missing_ok=True)
        raise SystemExit("Centroid extension byte verification failed")

    print(f"Input SHA-256:   {digest}")
    print(f"Output SHA-256:  {sha(check)}")
    print(f"Dispatcher:     {DISPATCH_CMP_VA:#010x}, axis limit 502 -> 505")
    print(f"AABB entry:     {AABB_ENTRY_VA:#010x} -> {CENTROID_CAVE_VA:#010x}")
    print(f"Centroid code:  {len(code)} bytes in executable .reloc cave")
    print(f"Cave remaining: {cave_room - len(code)} bytes")
    print(f"Changed bytes:  {len(changed)}; confined to the two patches and centroid cave")
    print("Axes 500..502 retain the existing AABB world center.")
    print("The extension caches X/Y/Z from axis 503 for the immediately following 504/505 calls; only its own .reloc scratch fields are written.")
    print("Axes 503..505 return the indexed triangle surface-area centroid in world space, fixed-point *1000.")
    print("Static PE verification passed; runtime validation in SLRR is still required.")
    print(f"Output: {output}")
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except (OSError, ValueError, struct.error, subprocess.CalledProcessError) as exc:
        print(f"ERROR: {exc}")
        raise SystemExit(2)
