#!/usr/bin/env python3
"""Build the live-AABB + native-pivot + component-scale diagnostic EXE.

Base input: StreetLegal_Redline_native_aabb_rendercenter_v2.exe
Adds Bone00 matrix probes (489..498), native pivot (510..512), and
INSTANCE_RENDER scale (513..515). Keeps live AABB center axes 500..502.
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
DISPATCH_VA = 0x007E17C7
DISPATCH_EXPECTED = bytes.fromhex("81 F9 F4 01 00 00")
AABB_ENTRY_VA = 0x007E1A3C
AABB_ENTRY_EXPECTED = bytes.fromhex("53 57 83 EC 50")
AABB_LIMIT_VA = 0x007E17D3
AABB_LIMIT_EXPECTED = bytes.fromhex("81 F9 F6 01 00 00")
CAVE_VA = 0x007E2B10
CENTROID_CAVE_VA = 0x007E2268
CENTER_AXIS_CALLS = (
    (0x007E1A78, 0x06, 0x00),
    (0x007E1A93, 0x07, 0x01),
    (0x007E1AAE, 0x08, 0x02),
    (0x007E1AC9, 0x09, 0x03),
    (0x007E1AE4, 0x0A, 0x04),
    (0x007E1AFF, 0x0B, 0x05),
)
NAME_COMPARE_PATCHES = (
    (0x007E18D0, bytes.fromhex("81 78 18 30 30 00 00"), bytes.fromhex("66 81 78 18 30 30 90")),
    (0x007E1B8A, bytes.fromhex("81 7F 18 30 30 00 00"), bytes.fromhex("66 81 7F 18 30 30 90")),
)
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
    ap.add_argument("input", type=Path)
    ap.add_argument("output", type=Path)
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
        raise SystemExit(f"Wrong base SHA-256: {digest}; expected {BASE_SHA256}")
    image_base, sections = pe_sections(original)
    if image_base != IMAGE_BASE or ".reloc" not in sections:
        raise SystemExit("Unexpected PE image base or missing .reloc section")

    dispatch_off, dispatch_sec, _ = va_to_offset(image_base, sections, DISPATCH_VA)
    entry_off, entry_sec, _ = va_to_offset(image_base, sections, AABB_ENTRY_VA)
    limit_off, limit_sec, _ = va_to_offset(image_base, sections, AABB_LIMIT_VA)
    cave_off, cave_sec, cave_room = va_to_offset(image_base, sections, CAVE_VA)
    centroid_off, centroid_sec, centroid_room = va_to_offset(image_base, sections, CENTROID_CAVE_VA)
    if any(x != ".reloc" for x in (dispatch_sec, entry_sec, limit_sec, cave_sec, centroid_sec)):
        raise SystemExit("Expected dispatcher, AABB handler and caves in .reloc")
    if (sections[".reloc"][4] & 0xA0000000) != 0xA0000000:
        raise SystemExit(".reloc must be executable and writable")
    if original[dispatch_off:dispatch_off + 6] != DISPATCH_EXPECTED:
        raise SystemExit("The axis-500 dispatch signature differs")
    if original[entry_off:entry_off + 5] != AABB_ENTRY_EXPECTED:
        raise SystemExit("AABB handler prologue differs")
    if original[limit_off:limit_off + 6] != AABB_LIMIT_EXPECTED:
        raise SystemExit("AABB axis limit is not 502")
    if original[centroid_off:centroid_off + min(256, centroid_room)] != bytes(min(256, centroid_room)):
        raise SystemExit("Centroid cave is not empty in the selected base EXE")

    with tempfile.TemporaryDirectory(prefix="slrr_component_fit_") as tmp:
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
        raise SystemExit(f"BoneUpper query does not fit in .reloc cave ({len(code)} bytes)")
    if original[cave_off:cave_off + len(code)] != bytes(len(code)):
        raise SystemExit("Selected BoneUpper code cave is not zero-filled")

    rel32 = CAVE_VA - (DISPATCH_VA + 5)
    patched = bytearray(original)
    patched[dispatch_off:dispatch_off + 6] = b"\xE9" + struct.pack("<i", rel32) + b"\x90"
    patched[cave_off:cave_off + len(code)] = code

    name_compare_offsets = []
    for va, expected, replacement in NAME_COMPARE_PATCHES:
        off, section, _ = va_to_offset(image_base, sections, va)
        if section != ".reloc" or original[off:off + len(expected)] != expected:
            raise SystemExit(f"Unexpected bone-name compare at {va:#010x}")
        if len(expected) != len(replacement):
            raise SystemExit("Bone-name compare patch must preserve instruction footprint")
        patched[off:off + len(replacement)] = replacement
        name_compare_offsets.append(off)

    center_patch_offsets = []
    for va, expected_axis, live_axis in CENTER_AXIS_CALLS:
        off, section, _ = va_to_offset(image_base, sections, va)
        if section != ".reloc" or original[off] != expected_axis or original[off - 1] != 0x6A:
            raise SystemExit(f"Unexpected nested-center PUSH axis at {va:#010x}")
        patched[off] = live_axis
        center_patch_offsets.append(off)

    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_bytes(patched)
    check = output.read_bytes()
    allowed = set(range(dispatch_off, dispatch_off + 6))
    allowed.update(range(cave_off, cave_off + len(code)))
    allowed.update(center_patch_offsets)
    for off, (_, expected, replacement) in zip(name_compare_offsets, NAME_COMPARE_PATCHES):
        allowed.update(range(off, off + len(replacement)))
    changed = [i for i, (a, b) in enumerate(zip(original, check)) if a != b]
    if any(i not in allowed for i in changed):
        output.unlink(missing_ok=True)
        raise SystemExit("Unexpected bytes changed outside the diagnostic patch scope")
    if check[limit_off:limit_off + 6] != AABB_LIMIT_EXPECTED:
        output.unlink(missing_ok=True)
        raise SystemExit("AABB 500..502 route was modified")
    if check[entry_off:entry_off + 5] != AABB_ENTRY_EXPECTED:
        output.unlink(missing_ok=True)
        raise SystemExit("AABB handler was modified")
    if check[centroid_off:centroid_off + min(256, centroid_room)] != bytes(min(256, centroid_room)):
        output.unlink(missing_ok=True)
        raise SystemExit("Centroid code appeared in the output")
    for (va, _, replacement), off in zip(NAME_COMPARE_PATCHES, name_compare_offsets):
        if check[off:off + len(replacement)] != replacement:
            output.unlink(missing_ok=True)
            raise SystemExit(f"Bone-name compare verification failed at {va:#010x}")
    for (va, _, live_axis), off in zip(CENTER_AXIS_CALLS, center_patch_offsets):
        if check[off] != live_axis:
            output.unlink(missing_ok=True)
            raise SystemExit(f"Live AABB axis patch verification failed at {va:#010x}")
    if check[cave_off:cave_off + len(code)] != code:
        output.unlink(missing_ok=True)
        raise SystemExit("BoneUpper code verification failed")

    print(f"Base SHA-256:   {digest}")
    print(f"Output SHA-256: {sha(check)}")
    print(f"BoneUpper VA:   {CAVE_VA:#010x}; code size {len(code)} bytes")
    print(f"Center:         axes 500..502 remain transformed live-AABB center")
    print(f"Native pivot:   axes 510..512 = INSTANCE_RENDER.position * 1000")
    print(f"Scale:          axes 513..515 = INSTANCE_RENDER.scale * 1000")
    print("Bone names:     NUL-terminated Bone00 compare")
    print("Centroid:       disabled; centroid cave remains zero-filled")
    print(f"Changed bytes:  {len(changed)}; all within the verified patch scope")
    print("Static PE verification passed; SLRR runtime validation is still required.")
    print(f"Output: {output}")
    return 0

if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except (OSError, ValueError, struct.error, subprocess.CalledProcessError) as exc:
        print(f"ERROR: {exc}")
        raise SystemExit(2)
