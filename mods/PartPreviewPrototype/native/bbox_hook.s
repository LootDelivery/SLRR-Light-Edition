.intel_syntax noprefix
.section .text
.code32
.global _start

# Installed at VA 0x007DF972; entered from 0x0047DEDD.
# At entry:
#   ECX       = resolved native object
#   EAX       = object's vtable
#   [ESP]     = GameRef.getInfo first int (query)
#   [ESP + 4] = GameRef.getInfo second int (axis selector)
_start:
    cmp DWORD PTR [esp], 0x584F4242
    jne .Lpass

    mov edx, DWORD PTR [esp+4]
    cmp edx, 2
    ja .Linvalid

    # AABB is mode 1. The native bounds formatter reads:
    #   boundmode  +0x80
    #   boundcenter +0x84 (XYZ float32)
    #   boundradius +0x90 (XYZ float32)
    cmp DWORD PTR [ecx+0x80], 1
    jne .Linvalid

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
