#!/usr/bin/env python3
"""Build a narrowly-scoped EXE patch that computes transformed geometry AABBs.

Base: diagnostic second-bone-probe EXE (SHA-256 pinned).
The patch transforms each finite CPU position vertex by the live instance scale,
selected render-node basis and INSTANCE_RENDER position before min/max accumulation.
"""
from __future__ import annotations
import argparse
import hashlib
import struct
import subprocess
import tempfile
from pathlib import Path

BASE_SHA256 = "84f2834bf02adc4f4c913cfe90224d7fdc10a746e7aa619f3c5c8110938cc389"
IMAGE_BASE = 0x00400000
VERTEX_HOOK_VA = 0x005E6DCA
VERTEX_HOOK_EXPECTED = bytes.fromhex("83 7D AC 00 75 24")
CENTER_DISPATCH_VA = 0x007E1A3C
CENTER_DISPATCH_EXPECTED = bytes.fromhex("53 57 83 EC 50")
CAVE_VA = 0x007E2268
CAVE_END_VA = 0x007E2B10  # next diagnostic cave begins here
TRANSFORMED_CENTER_OFFSET = 0x109
ROOT = Path(__file__).resolve().parent
SOURCE = ROOT / "aabb_transform.S"

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
        virtual_size, virtual_address, raw_size, raw_ptr = struct.unpack_from("<IIII", data, off + 8)
        characteristics = u32(data, off + 36)
        sections[name] = (virtual_size, virtual_address, raw_size, raw_ptr, characteristics)
    return image_base, sections

def va_to_offset(image_base: int, sections, va: int):
    rva = va - image_base
    for name, (vs, sec_va, raw_size, raw_ptr, characteristics) in sections.items():
        if sec_va <= rva < sec_va + raw_size:
            delta = rva - sec_va
            return raw_ptr + delta, name, raw_size - delta
    raise ValueError(f"VA {va:#010x} is not backed by a section")

def rel32_jump(site: int, target: int, footprint: int) -> bytes:
    if footprint < 5:
        raise ValueError("Jump footprint too small")
    rel = target - (site + 5)
    return b"\xE9" + struct.pack("<i", rel) + b"\x90" * (footprint - 5)

def main() -> int:
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument("input", type=Path)
    ap.add_argument("output", type=Path)
    args = ap.parse_args()
    source = args.input.resolve()
    output = args.output.resolve()
    if source == output:
        raise SystemExit("Refusing to overwrite input EXE")
    if not source.is_file() or not SOURCE.is_file():
        raise SystemExit("Input EXE or aabb_transform.S is missing")
    original = source.read_bytes()
    digest = sha(original)
    if digest != BASE_SHA256:
        raise SystemExit(f"Wrong base SHA-256: {digest}; expected {BASE_SHA256}")
    image_base, sections = pe_sections(original)
    if image_base != IMAGE_BASE or ".reloc" not in sections:
        raise SystemExit("Unexpected image base or missing .reloc section")
    reloc = sections[".reloc"]
    if (reloc[4] & 0xA0000000) != 0xA0000000:
        raise SystemExit(".reloc must be executable and writable")

    hook_off, hook_sec, hook_room = va_to_offset(image_base, sections, VERTEX_HOOK_VA)
    center_off, center_sec, center_room = va_to_offset(image_base, sections, CENTER_DISPATCH_VA)
    cave_off, cave_sec, cave_room = va_to_offset(image_base, sections, CAVE_VA)
    next_cave_off, next_cave_sec, _ = va_to_offset(image_base, sections, CAVE_END_VA)
    if hook_sec != ".text" or center_sec != ".reloc" or cave_sec != ".reloc" or next_cave_sec != ".reloc":
        raise SystemExit("Unexpected hook / dispatcher / cave section mapping")
    if original[hook_off:hook_off + len(VERTEX_HOOK_EXPECTED)] != VERTEX_HOOK_EXPECTED:
        raise SystemExit("AABB vertex-loop hook signature differs")
    if original[center_off:center_off + len(CENTER_DISPATCH_EXPECTED)] != CENTER_DISPATCH_EXPECTED:
        raise SystemExit("Nested center dispatcher signature differs")
    if CAVE_END_VA <= CAVE_VA or CAVE_END_VA - CAVE_VA > cave_room:
        raise SystemExit("Transform code cave overlaps the following cave or section end")

    with tempfile.TemporaryDirectory(prefix="slrr_transformed_aabb_") as tmp:
        obj = Path(tmp) / "aabb_transform.o"
        raw = Path(tmp) / "aabb_transform.bin"
        subprocess.run(["as", "--32", "-o", str(obj), str(SOURCE)], check=True)
        subprocess.run([
            "ld", "-m", "elf_i386", f"-Ttext={CAVE_VA:#x}",
            "-e", "aabb_transform_vertex", "--oformat", "binary",
            "-o", str(raw), str(obj)
        ], check=True)
        code = raw.read_bytes()
        symbols = subprocess.run(["nm", "-n", str(obj)], check=True, capture_output=True, text=True).stdout
    center_symbols = [line for line in symbols.splitlines() if line.rstrip().endswith(" transformed_center")]
    if len(center_symbols) != 1:
        raise SystemExit("Could not resolve transformed_center symbol")
    center_offset = int(center_symbols[0].split()[0], 16)
    if center_offset != TRANSFORMED_CENTER_OFFSET:
        raise SystemExit(f"Unexpected transformed_center offset {center_offset:#x}; expected {TRANSFORMED_CENTER_OFFSET:#x}")
    transformed_center_va = CAVE_VA + center_offset
    if not code or len(code) > CAVE_END_VA - CAVE_VA:
        raise SystemExit(f"Transformed AABB code does not fit ({len(code)} bytes)")
    if original[cave_off:cave_off + len(code)] != bytes(len(code)):
        raise SystemExit("Transform code cave is not zero-filled in pinned base EXE")

    vertex_jump = rel32_jump(VERTEX_HOOK_VA, CAVE_VA, len(VERTEX_HOOK_EXPECTED))
    center_jump = rel32_jump(CENTER_DISPATCH_VA, transformed_center_va, len(CENTER_DISPATCH_EXPECTED))
    patched = bytearray(original)
    patched[hook_off:hook_off + len(vertex_jump)] = vertex_jump
    patched[center_off:center_off + len(center_jump)] = center_jump
    patched[cave_off:cave_off + len(code)] = code

    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_bytes(patched)
    check = output.read_bytes()
    changed = [i for i, (a, b) in enumerate(zip(original, check)) if a != b]
    allowed = set(range(hook_off, hook_off + len(vertex_jump)))
    allowed.update(range(center_off, center_off + len(center_jump)))
    allowed.update(range(cave_off, cave_off + len(code)))
    if any(i not in allowed for i in changed):
        output.unlink(missing_ok=True)
        raise SystemExit("Unexpected bytes changed outside the two jumps and transform code cave")
    if check[hook_off:hook_off + len(vertex_jump)] != vertex_jump:
        output.unlink(missing_ok=True)
        raise SystemExit("Vertex-loop detour verification failed")
    if check[center_off:center_off + len(center_jump)] != center_jump:
        output.unlink(missing_ok=True)
        raise SystemExit("Center-dispatch detour verification failed")
    if check[cave_off:cave_off + len(code)] != code:
        output.unlink(missing_ok=True)
        raise SystemExit("Transformed AABB code verification failed")
    # Preserve the later second-bone diagnostic cave byte-for-byte.
    if check[next_cave_off:] != original[next_cave_off:]:
        output.unlink(missing_ok=True)
        raise SystemExit("The existing second-bone diagnostic cave was modified")

    print(f"Base SHA-256:             {digest}")
    print(f"Output SHA-256:           {sha(check)}")
    print(f"Vertex transform hook:    {VERTEX_HOOK_VA:#010x} -> {CAVE_VA:#010x}")
    print(f"Center dispatch:          {CENTER_DISPATCH_VA:#010x} -> {transformed_center_va:#010x}")
    print(f"Transform code size:      {len(code)} bytes")
    print(f"Changed bytes:            {len(changed)}; strictly allowlisted")
    print("Existing second-bone diagnostic code: preserved")
    print("Static PE verification passed. In-game runtime validation is still required.")
    print(f"Output: {output}")
    return 0

if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except (OSError, ValueError, struct.error, subprocess.CalledProcessError) as exc:
        print(f"ERROR: {exc}")
        raise SystemExit(2)
