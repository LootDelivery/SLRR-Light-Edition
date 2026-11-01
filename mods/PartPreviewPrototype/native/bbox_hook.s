.intel_syntax noprefix
.section .text
.code32
.global _start

# Installed at VA 0x007DF972; entered from 0x0047DEDD.
# At entry:
#   ECX       = resolved native object
#   EAX       = its vtable
#   [ESP]     = GameRef.getInfo first int (query)
#   [ESP + 4] = GameRef.getInfo second int (axis selector)
_start:
    cmp DWORD PTR [esp], 0x584F4242
    jne .Lpass

    cmp DWORD PTR [esp+4], 2
    ja .Linvalid

    # Guard the native bounds fields before reading them. IsBadReadPtr is
    # already imported by this executable at IAT VA 0x005E7124.
    push ecx
    lea eax, [ecx+0x80]
    push 0x10
    push eax
    call DWORD PTR [0x005E7124]
    pop ecx
    test eax, eax
    jne .Linvalid

    # Native bounds formatter: boundmode +0x80; AABB center XYZ +0x84.
    cmp DWORD PTR [ecx+0x80], 1
    jne .Linvalid

    mov edx, DWORD PTR [esp+4]
    fld DWORD PTR [ecx+edx*4+0x84]
    fmul DWORD PTR [scale_10000]
    sub esp, 4
    fistp DWORD PTR [esp]
    pop eax
    add esp, 8
    jmp 0x0047DEE4

.Linvalid:
    mov eax, 0x80000000
    add esp, 8
    jmp 0x0047DEE4

.Lpass:
    # Replay the original virtual call unchanged for all other getInfo queries.
    mov edx, DWORD PTR [edi+0x4c]
    push edx
    call DWORD PTR [eax+0x3c]
    jmp 0x0047DEE4

.align 4
scale_10000:
    .float 10000.0
