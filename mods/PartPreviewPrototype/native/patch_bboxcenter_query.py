#!/usr/bin/env python3
"""Build the guarded native BBox-center query probe from silent_v2.exe."""
from __future__ import annotations
import argparse, hashlib, struct, sys
from pathlib import Path

BASE_SHA256 = "a954fc724a4561e5e30eba3a2cc4be1c162636eac9db5ba7746c53e2f89638bf"
BASE = 0x00400000
REG_VA = 0x00489666
REG_EXPECTED = bytes.fromhex("68 F0 DD 47 00")
STRING_OVERLOAD_VA = 0x00489685
THUNK_VA = 0x007DF972
THUNK = bytes.fromhex("""
83ec085556578d44240c508b44241c8d4c2414518d542420525031ede87ddfc7ffe800000000598d8970e6e4ff8b098b5424285152e8a4b1c4ff83c418817c240c42424f58756785c0744283780800743c8b700c85f67435837e4c05753683be800000000175348b54241083fa027732d984968400000068e8030000da0c2483ec04db1c248b042483c408eb1ab800000080eb13b801000080eb0cb802000080eb05b8030000805f5e5d83c408c3e900e4c9ff
""")
THUNK_SHA256 = "271a46f11c7f9b205407caed6b9a7e453ef30551fee1b028ec05ca19c2bfe28b"
WRITER_GUARDS = {
    0x007DE081: bytes.fromhex("C3 90 90 90"),
    0x007DE2D4: bytes.fromhex("C3 90 90 90"),
    0x007DE4BF: bytes.fromhex("C3 90 90 90 90"),
}

def u16(b, o): return struct.unpack_from("<H", b, o)[0]
def u32(b, o): return struct.unpack_from("<I", b, o)[0]

def va_to_offset(b, va):
    pe = u32(b, 0x3C)
    image_base = u32(b, pe + 24 + 28)
    rva = va - image_base
    count, opt_size = u16(b, pe + 6), u16(b, pe + 20)
    table = pe + 24 + opt_size
    for i in range(count):
        o = table + i * 40
        name = b[o:o+8].split(b"\\0", 1)[0].decode("ascii", "replace")
        vs, va0, rs, rp = struct.unpack_from("<IIII", b, o + 8)
        flags = u32(b, o + 36)
        if va0 <= rva < va0 + max(vs, rs):
            delta = rva - va0
            if delta >= rs: raise ValueError(f"{va:#x} has no raw backing")
            return rp + delta, name, flags, rs - delta
    raise ValueError(f"{va:#x} is not mapped")

def has_highlow_reloc(b, target_rva):
    pe = u32(b, 0x3C)
    reloc_rva, reloc_size = struct.unpack_from("<II", b, pe + 24 + 96 + 5*8)
    pos, _, _, _ = va_to_offset(b, BASE + reloc_rva)
    end = pos + reloc_size
    while pos + 8 <= end:
        page, size = struct.unpack_from("<II", b, pos)
        if size < 8 or pos + size > end: raise ValueError("Malformed relocation block")
        for i in range((size - 8)//2):
            ent = u16(b, pos + 8 + i*2)
            if ent >> 12 == 3 and page + (ent & 0xFFF) == target_rva:
                return True
        pos += size
    return False

def cstr(b, va):
    o, _, _, _ = va_to_offset(b, va)
    e = b.find(b"\\0", o)
    if e < 0: raise ValueError(f"Unterminated string at {va:#x}")
    return b[o:e].decode("ascii", "replace")

def main():
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument("input", type=Path, help="known-good StreetLegal_Redline_native_silent_v2.exe")
    ap.add_argument("output", type=Path, nargs="?", help="separate test EXE")
    a = ap.parse_args()
    src = a.input.resolve()
    if not src.is_file(): ap.error(f"missing input: {src}")
    original = src.read_bytes()
    digest = hashlib.sha256(original).hexdigest()
    if digest != BASE_SHA256: raise SystemExit(f"Wrong base EXE: {digest}")
    if hashlib.sha256(THUNK).hexdigest() != THUNK_SHA256: raise SystemExit("Embedded thunk hash mismatch")
    if u32(original, 0x3C) <= 0 or original[:2] != b"MZ": raise SystemExit("Not a PE file")
    pe = u32(original, 0x3C)
    if original[pe:pe+4] != b"PE\\0\\0" or u16(original, pe+4) != 0x014C or u16(original, pe+24) != 0x010B:
        raise SystemExit("Expected x86 PE32")
    if u32(original, pe+24+28) != BASE: raise SystemExit("Unexpected image base")

    reg, reg_sec, reg_flags, _ = va_to_offset(original, REG_VA)
    alt, _, _, _ = va_to_offset(original, STRING_OVERLOAD_VA)
    cave, cave_sec, cave_flags, room = va_to_offset(original, THUNK_VA)
    if reg_sec != ".text" or cave_sec != ".reloc": raise SystemExit("Unexpected PE sections")
    if not (reg_flags & 0x20000000 and cave_flags & 0x20000000): raise SystemExit("Non-executable patch region")
    if original[reg:reg+5] != REG_EXPECTED or original[alt:alt+5] != REG_EXPECTED:
        raise SystemExit("Native registration table differs from expected baseline")
    for va, expected in [(0x616278, "(II)I"), (0x616280, "getInfo"),
                         (0x616288, "java.util.resource.GameRef"),
                         (0x6162A4, "(ILjava.lang.String;)I")]:
        if cstr(original, va) != expected: raise SystemExit(f"Unexpected registration string at {va:#x}")
    if not has_highlow_reloc(original, REG_VA + 1 - BASE): raise SystemExit("Missing registration HIGHLOW relocation")
    if room < len(THUNK) or original[cave:cave+len(THUNK)] != bytes(len(THUNK)):
        raise SystemExit("Thunk cave is not empty or is too small")
    if THUNK_VA + 0x26 - 0x001B1990 != 0x0062E008:
        raise SystemExit("Position-independent VM-global displacement is inconsistent")
    for va, expected in WRITER_GUARDS.items():
        o, _, _, _ = va_to_offset(original, va)
        if original[o:o+len(expected)] != expected: raise SystemExit(f"Writer guard mismatch at {va:#x}")

    out = a.output.resolve() if a.output else src.with_name("StreetLegal_Redline_native_bboxcenter_query_probe.exe")
    if out == src: raise SystemExit("Refusing to overwrite source")
    patched = bytearray(original)
    patched[reg+1:reg+5] = struct.pack("<I", THUNK_VA)
    patched[cave:cave+len(THUNK)] = THUNK
    out.parent.mkdir(parents=True, exist_ok=True)
    out.write_bytes(patched)
    check = out.read_bytes()
    if check[reg+1:reg+5] != struct.pack("<I", THUNK_VA) or check[cave:cave+len(THUNK)] != THUNK:
        raise SystemExit("Output verification failed")
    if check[alt:alt+5] != REG_EXPECTED: raise SystemExit("String overload changed")
    print(f"Output: {out}")
    print(f"Base SHA-256: {digest}")
    print(f"Output SHA-256: {hashlib.sha256(check).hexdigest()}")
    print(f"Thunk: {len(THUNK)} bytes at {THUNK_VA:#010x}; reserved query 0x584F4242")
    print("String overload and Reflection writer guards unchanged; runtime test still required.")

if __name__ == "__main__":
    try: main()
    except (OSError, ValueError, struct.error) as e:
        print(f"ERROR: {e}", file=sys.stderr)
        raise SystemExit(2)
