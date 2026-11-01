#!/usr/bin/env python3
"""Build the PartPreviewPrototype live AABB query from its known-good route probe.

The patch keeps the stock GameRef.getInfo(II)I registration, parser and receiver
resolver. It installs a small inline dispatcher at 0x0047DE25 and places the
read-only AABB helper in the existing .text raw-padding area. The input EXE is
SHA-256 pinned and is never overwritten.

Requires GNU binutils (as, ld) with ELF32/i386 support.
"""
from __future__ import annotations

import argparse
import hashlib
import struct
import subprocess
import tempfile
from pathlib import Path

BASE_SHA256 = "8e8cde4bcb09f83dd0888d9cdc3d58248d8f6cbb1d9499eb4593e42eccd88e0d"
IMAGE_BASE = 0x00400000
REG_VA = 0x00489666
REG_BYTES = bytes.fromhex("68 72 F9 7D 00")
STRING_REG_VA = 0x00489685
STRING_REG_BYTES = bytes.fromhex("68 F0 DD 47 00")
ROUTE_THUNK_VA = 0x007DF972
ROUTE_THUNK_BYTES = bytes.fromhex("E9 79 E4 C9 FF")
DISPATCH_VA = 0x0047DE25
DISPATCH_BYTES = bytes.fromhex("85 C0 0F 84 CB 00 00 00")
HOOK_VA = 0x005E6815
HELPER_VA = 0x005E6900
INVALID = 0x80000000  # signed Java int sentinel; coordinates are returned * 1000

ROOT = Path(__file__).resolve().parent
HOOK_SOURCE = ROOT / "aabb_inline_hook.S"
HELPER_SOURCE = ROOT / "aabb_helper.S"


def sha256(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


def u16(data: bytes, offset: int) -> int:
    return struct.unpack_from("<H", data, offset)[0]


def u32(data: bytes, offset: int) -> int:
    return struct.unpack_from("<I", data, offset)[0]


def parse_pe(data: bytes):
    if data[:2] != b"MZ":
        raise ValueError("Input is not an MZ executable")
    pe = u32(data, 0x3C)
    if data[pe:pe + 4] != b"PE\0\0":
        raise ValueError("PE signature is missing")
    if u16(data, pe + 4) != 0x014C or u16(data, pe + 24) != 0x010B:
        raise ValueError("Expected PE32/i386")
    image_base = u32(data, pe + 24 + 28)
    count = u16(data, pe + 6)
    optional_size = u16(data, pe + 20)
    section_table = pe + 24 + optional_size
    sections = {}
    for i in range(count):
        off = section_table + i * 40
        name = data[off:off + 8].split(b"\0", 1)[0].decode("ascii", "replace")
        vs, va, raw_size, raw_ptr = struct.unpack_from("<IIII", data, off + 8)
        characteristics = u32(data, off + 36)
        sections[name] = {
            "header": off,
            "virtual_size": vs,
            "virtual_address": va,
            "raw_size": raw_size,
            "raw_ptr": raw_ptr,
            "characteristics": characteristics,
        }
    return pe, image_base, sections


def va_to_offset(image_base: int, sections, va: int):
    rva = va - image_base
    for name, sec in sections.items():
        start = sec["virtual_address"]
        raw_size = sec["raw_size"]
        if start <= rva < start + raw_size:
            delta = rva - start
            return sec["raw_ptr"] + delta, name, raw_size - delta
    raise ValueError(f"VA {va:#010x} has no raw backing")


def assemble(source: Path, base_va: int, entry: str, work: Path) -> bytes:
    obj = work / (source.stem + ".o")
    binary = work / (source.stem + ".bin")
    subprocess.run(["as", "--32", "-o", str(obj), str(source)], check=True)
    subprocess.run([
        "ld", "-m", "elf_i386", f"-Ttext={base_va:#x}", "-e", entry,
        "--oformat", "binary", "-o", str(binary), str(obj)
    ], check=True)
    return binary.read_bytes()


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("input", type=Path, help="known-good getInfo route-probe EXE")
    parser.add_argument("output", type=Path, help="separate experimental AABB EXE")
    args = parser.parse_args()
    source = args.input.resolve()
    output = args.output.resolve()
    if source == output:
        raise SystemExit("Refusing to overwrite the known-good input EXE")
    if not source.is_file():
        raise SystemExit(f"Input executable not found: {source}")
    if not HOOK_SOURCE.is_file() or not HELPER_SOURCE.is_file():
        raise SystemExit("aabb_inline_hook.S or aabb_helper.S is missing next to this builder")

    original = source.read_bytes()
    digest = sha256(original)
    if digest != BASE_SHA256:
        raise SystemExit(f"Wrong base SHA-256: {digest}; expected {BASE_SHA256}")
    pe, image_base, sections = parse_pe(original)
    if image_base != IMAGE_BASE:
        raise SystemExit(f"Unexpected image base: {image_base:#x}")

    text = sections.get(".text")
    rdata = sections.get(".rdata")
    if not text or not rdata:
        raise SystemExit("Required .text/.rdata sections are missing")
    if not (text["characteristics"] & 0x20000000):
        raise SystemExit(".text is not executable")
    if text["virtual_size"] >= text["raw_size"]:
        raise SystemExit("Expected an unused .text raw-padding area")
    if text["virtual_address"] + text["raw_size"] != rdata["virtual_address"]:
        raise SystemExit("Extending .text to RawSize would overlap or gap before .rdata")

    hook_cave_va = image_base + text["virtual_address"] + text["virtual_size"]
    if hook_cave_va != HOOK_VA:
        raise SystemExit(f"Unexpected .text cave start: {hook_cave_va:#010x}")
    cave_off, cave_section, cave_room = va_to_offset(image_base, sections, HOOK_VA)
    helper_off, helper_section, helper_room = va_to_offset(image_base, sections, HELPER_VA)
    dispatch_off, dispatch_section, _ = va_to_offset(image_base, sections, DISPATCH_VA)
    reg_off, reg_section, _ = va_to_offset(image_base, sections, REG_VA)
    string_off, string_section, _ = va_to_offset(image_base, sections, STRING_REG_VA)
    thunk_off, thunk_section, _ = va_to_offset(image_base, sections, ROUTE_THUNK_VA)

    if cave_section != ".text" or helper_section != ".text" or dispatch_section != ".text":
        raise SystemExit("Hook, helper and dispatcher must be in .text")
    if reg_section != ".text" or string_section != ".text" or thunk_section != ".reloc":
        raise SystemExit("Unexpected native registration/thunk section mapping")
    if original[reg_off:reg_off + 5] != REG_BYTES:
        raise SystemExit("GameRef.getInfo(II)I registration differs from the route probe")
    if original[string_off:string_off + 5] != STRING_REG_BYTES:
        raise SystemExit("GameRef.getInfo(I,String)I registration differs from expected stock target")
    if original[thunk_off:thunk_off + 5] != ROUTE_THUNK_BYTES:
        raise SystemExit("The known-good 5-byte route thunk has changed")
    if original[dispatch_off:dispatch_off + 8] != DISPATCH_BYTES:
        raise SystemExit("The original GetInfo dispatch bytes at 0x0047DE25 have changed")

    raw_end_off = text["raw_ptr"] + text["raw_size"]
    if original[cave_off:raw_end_off] != bytes(raw_end_off - cave_off):
        raise SystemExit("Candidate .text padding is not all zero")
    if cave_room < 0x200 or helper_room < 0x400:
        raise SystemExit("Insufficient .text padding for inline hook/helper")

    output.parent.mkdir(parents=True, exist_ok=True)
    with tempfile.TemporaryDirectory(prefix="slrr_aabb_build_") as temp:
        work = Path(temp)
        hook = assemble(HOOK_SOURCE, HOOK_VA, "aabb_inline_hook", work)
        helper = assemble(HELPER_SOURCE, HELPER_VA, "aabb_helper", work)

    if HOOK_VA + len(hook) > HELPER_VA:
        raise SystemExit(f"Inline hook overlaps helper: hook is {len(hook)} bytes")
    if HELPER_VA + len(helper) > image_base + text["virtual_address"] + text["raw_size"]:
        raise SystemExit("AABB helper exceeds .text raw padding")
    if len(helper) > 0x400:
        raise SystemExit(f"AABB helper too large for validated helper slot: {len(helper)} bytes")

    detour = b"\xE9" + struct.pack("<i", HOOK_VA - (DISPATCH_VA + 5)) + b"\x90\x90\x90"
    patched = bytearray(original)
    struct.pack_into("<I", patched, text["header"] + 8, text["raw_size"])
    patched[cave_off:cave_off + len(hook)] = hook
    patched[helper_off:helper_off + len(helper)] = helper
    patched[dispatch_off:dispatch_off + 8] = detour

    # Strict changed-byte allowlist and post-write verification.
    output.write_bytes(patched)
    check = output.read_bytes()
    allowed = set(range(text["header"] + 8, text["header"] + 12))
    allowed.update(range(cave_off, cave_off + len(hook)))
    allowed.update(range(helper_off, helper_off + len(helper)))
    allowed.update(range(dispatch_off, dispatch_off + 8))
    diffs = [i for i, (a, b) in enumerate(zip(original, check)) if a != b]
    if any(i not in allowed for i in diffs):
        output.unlink(missing_ok=True)
        raise SystemExit("Unexpected changes outside .text size, hook/helper caves and dispatch site")
    if check[dispatch_off:dispatch_off + 8] != detour:
        output.unlink(missing_ok=True)
        raise SystemExit("Dispatch detour verification failed")
    if check[cave_off:cave_off + len(hook)] != hook or check[helper_off:helper_off + len(helper)] != helper:
        output.unlink(missing_ok=True)
        raise SystemExit("Hook/helper byte verification failed")
    if check[reg_off:reg_off + 5] != REG_BYTES or check[string_off:string_off + 5] != STRING_REG_BYTES:
        output.unlink(missing_ok=True)
        raise SystemExit("Native registration table unexpectedly changed")
    if check[thunk_off:thunk_off + 5] != ROUTE_THUNK_BYTES:
        output.unlink(missing_ok=True)
        raise SystemExit("Known-good route thunk unexpectedly changed")
    if u32(check, text["header"] + 8) != text["raw_size"]:
        output.unlink(missing_ok=True)
        raise SystemExit(".text VirtualSize extension verification failed")

    print(f"Input SHA-256:  {digest}")
    print(f"Output SHA-256: {sha256(check)}")
    print(f"Inline hook:   {HOOK_VA:#010x}, {len(hook)} bytes")
    print(f"AABB helper:   {HELPER_VA:#010x}, {len(helper)} bytes")
    print(f"Dispatch site: {DISPATCH_VA:#010x}, 8-byte detour")
    print(f"Changed bytes: {len(diffs)}; only .text size, 2 code caves and dispatch site")
    print("The original GameRef.getInfo parser/resolver, both registrations and route thunk are preserved.")
    print("Static verification passed. Runtime validation in SLRR is still required.")
    print(f"Output: {output}")
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except (OSError, ValueError, struct.error, subprocess.CalledProcessError) as exc:
        print(f"ERROR: {exc}")
        raise SystemExit(2)
