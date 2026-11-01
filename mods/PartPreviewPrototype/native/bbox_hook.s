.intel_syntax noprefix
.section .text
.code32
.global hook
hook:
    # Only intercept the reserved query; all other getInfo calls are replayed.
    cmp dword ptr [esp+0x0c], 0x584f4242
    jne passthrough

    # The native callback's virtual target is the resolved resource object at [eax+0x0c].
    # The second Java int is the X/Y/Z axis selector.
    mov ecx, dword ptr [esp+0x10]
    cmp ecx, 2
    ja unavailable
    mov edx, dword ptr [eax+0x0c]
    cmp dword ptr [edx+0x80], 1
    jne unavailable

    # Resource diagnostic confirms AABB center at +0x84/+0x88/+0x8C.
    # Return the selected float as fixed-point int (float * 10000).
    fld dword ptr [edx+ecx*4+0x84]
    fmul dword ptr [scale_10000]
    sub esp, 4
    fistp dword ptr [esp]
    pop eax
    jmp 0x47dee4

unavailable:
    mov eax, 0x80000000
    jmp 0x47dee4

passthrough:
    # Replay both instructions replaced at 0x47DECE and continue unchanged.
    mov edx, dword ptr [esp+0x0c]
    mov ecx, dword ptr [eax+0x0c]
    jmp 0x47ded5

    .align 4
scale_10000:
    .float 10000.0
