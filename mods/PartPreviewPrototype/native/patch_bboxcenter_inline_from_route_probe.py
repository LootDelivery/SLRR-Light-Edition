#!/usr/bin/env python3
"""Build an inline BBox query probe from the known-good GetInfo route probe.

The stock native handler, argument parser and receiver-resolution path remain
intact. A detour is installed only at 0x0047DE25, after stock setup completes.
Non-BBOX requests replay the original TEST/JZ and continue in stock code.
"""
from __future__ import annotations
import argparse, hashlib, struct, sys
from pathlib import Path

INPUT_SHA256 = "8e8cde4bcb09f83dd0888d9cdc3d58248d8f6cbb1d9499eb4593e42eccd88e0d"
BASE = 0x00400000
REG_VA, REG_BYTES = 0x00489666, bytes.fromhex("68 72 F9 7D 00")
STR_VA, STR_BYTES = 0x00489685, bytes.fromhex("68 F0 DD 47 00")
ROUTE_VA, ROUTE_BYTES = 0x007DF972, bytes.fromhex("E9 79 E4 C9 FF")
HOOK_VA = ROUTE_VA + 5
HOOK_SIZE = 129
SITE_VA = 0x0047DE25
SITE_OLD = bytes.fromhex("85 C0 0F 84 CB 00 00 00")
NULL_TARGET, CONTINUE_TARGET = 0x0047DEF8, 0x0047DE2D
NULL_DISP, CONTINUE_DISP = 0x78, 0x7D

HOOK = bytearray.fromhex("""
81 7c 24 10 42 42 4f 58 75 67
85 c0 74 42 83 78 08 00 74 3c
8b 70 0c 85 f6 74 35 83 7e 4c 05
75 36 83 be 80 00 00 00 01 75 34
8b 54 24 0c 83 fa 02 77 32
d9 84 96 84 00 00 00 68 e8 03 00 00
da 0c 24 83 ec 04 db 1c 24 8b 04 24
83 c4 08 eb 1a
b8 00 00 00 80 eb 13
b8 01 00 00 80 eb 0c
b8 02 00 00 80 eb 05
b8 03 00 00 80
5f 5e 5d 83 c4 08 c3
85 c0 74 02 eb 05
e9 00 00 00 00
e9 00 00 00 00
""")

def u16(b, o): return struct.unpack_from("<H", b, o)[0]
def u32(b, o): return struct.unpack_from("<I", b, o)[0]

def offset_for_va(data, va):
    pe = u32(data, 0x3c)
    image_base = u32(data, pe + 24 + 28)
    count, opt_size = u16(data, pe + 6), u16(data, pe + 20)
    table, rva = pe + 24 + opt_size, va - image_base
    for i in range(count):
        o = table + i * 40
        name = data[o:o+8].split(b"\\0", 1)[0].decode("ascii", "replace")
        vs, vaddr, raw_size, raw_ptr = struct.unpack_from("<IIII", data, o + 8)
        flags = u32(data, o + 36)
        if vaddr <= rva < vaddr + max(vs, raw_size):
            delta = rva - vaddr
            if delta >= raw_size: raise ValueError(f"VA {va:#x} has no raw bytes")
            return raw_ptr + delta, name, flags, raw_size - delta
    raise ValueError(f"VA {va:#x} is unmapped")

def rel32(jmp_va, target):
    return struct.pack("<i", target - (jmp_va + 5))

def build(src, dst):
    data = src.read_bytes()
    digest = hashlib.sha256(data).hexdigest()
    if digest != INPUT_SHA256: raise ValueError(f"Wrong route-probe SHA-256: {digest}")
    pe = u32(data, 0x3c)
    if data[:2] != b"MZ" or data[pe:pe+4] != b"PE\\0\\0": raise ValueError("Not a PE executable")
    if u16(data, pe+4) != 0x14c or u16(data, pe+24) != 0x10b: raise ValueError("Expected x86 PE32")
    if u32(data, pe+24+28) != BASE: raise ValueError("Unexpected image base")

    reg, _, _, _ = offset_for_va(data, REG_VA)
    strreg, _, _, _ = offset_for_va(data, STR_VA)
    route, rsec, rflags, rroom = offset_for_va(data, ROUTE_VA)
    site, ssec, sflags, _ = offset_for_va(data, SITE_VA)
    hook, hsec, hflags, hroom = offset_for_va(data, HOOK_VA)
    if data[reg:reg+5] != REG_BYTES: raise ValueError("Known-good route registration mismatch")
    if data[strreg:strreg+5] != STR_BYTES: raise ValueError("String overload registration mismatch")
    if data[route:route+5] != ROUTE_BYTES: raise ValueError("Known-good route thunk mismatch")
    if data[site:site+8] != SITE_OLD: raise ValueError("Stock dispatch bytes mismatch")
    if data[hook:hook+HOOK_SIZE] != bytes(HOOK_SIZE): raise ValueError("Code cave is not empty")
    if rsec != ".reloc" or hsec != ".reloc" or ssec != ".text": raise ValueError("Unexpected PE section mapping")
    if not (rflags & 0x20000000 and hflags & 0x20000000 and sflags & 0x20000000): raise ValueError("Hook site is not executable")
    if rroom < 5 + HOOK_SIZE or hroom < HOOK_SIZE: raise ValueError("Insufficient code cave")

    code = bytearray(HOOK)
    if len(code) != HOOK_SIZE: raise ValueError(f"Hook length is {len(code)}, expected {HOOK_SIZE}")
    code[NULL_DISP:NULL_DISP+4] = rel32(HOOK_VA + NULL_DISP - 1, NULL_TARGET)
    code[CONTINUE_DISP:CONTINUE_DISP+4] = rel32(HOOK_VA + CONTINUE_DISP - 1, CONTINUE_TARGET)
    detour = b"\\xE9" + rel32(SITE_VA, HOOK_VA) + b"\\x90\\x90\\x90"

    out = bytearray(data)
    out[hook:hook+HOOK_SIZE] = code
    out[site:site+8] = detour
    if dst.resolve() == src.resolve(): raise ValueError("Refusing to overwrite input")
    dst.parent.mkdir(parents=True, exist_ok=True)
    dst.write_bytes(out)

    check = dst.read_bytes()
    diffs = {i for i, (a, b) in enumerate(zip(data, check)) if a != b}
    allowed = set(range(hook, hook+HOOK_SIZE)) | set(range(site, site+8))
    if not diffs <= allowed: raise ValueError("Unexpected bytes changed outside hook ranges")
    if check[hook:hook+HOOK_SIZE] != code or check[site:site+8] != detour: raise ValueError("Output verification failed")
    targets = (
        (SITE_VA + 5 + struct.unpack_from("<i", detour, 1)[0]) & 0xffffffff,
        (HOOK_VA + NULL_DISP + 4 + struct.unpack_from("<i", code, NULL_DISP)[0]) & 0xffffffff,
        (HOOK_VA + CONTINUE_DISP + 4 + struct.unpack_from("<i", code, CONTINUE_DISP)[0]) & 0xffffffff,
    )
    if targets != (HOOK_VA, NULL_TARGET, CONTINUE_TARGET): raise ValueError(f"Bad branch targets: {targets}")
    print(f"Input SHA-256:  {digest}")
    print(f"Output SHA-256: {hashlib.sha256(check).hexdigest()}")
    print(f"Hook: {HOOK_VA:#010x}, {HOOK_SIZE} bytes; dispatch detour: {SITE_VA:#010x}, 8 bytes")
    print("Original native registration, argument parser and receiver-resolution code are retained.")
    print("Only the hook cave and 8-byte dispatch site are modified.")
    print("Static verification passed; runtime testing is still required.")

def main():
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument("input", type=Path)
    ap.add_argument("output", type=Path, nargs="?")
    a = ap.parse_args()
    src = a.input.resolve()
    dst = a.output.resolve() if a.output else src.with_name("StreetLegal_Redline_native_bboxcenter_inline_probe.exe")
    build(src, dst)

if __name__ == "__main__":
    try: main()
    except (OSError, ValueError, struct.error) as e:
        print(f"ERROR: {e}", file=sys.stderr)
        raise SystemExit(2)
