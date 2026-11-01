.intel_syntax noprefix
.section .text
.code32
.global _start

# Stage 0 only. This code intentionally does not dereference the resource.
# The two integer arguments are forwarded on the stack before this site.
# Check either slot for the reserved query family so we do not assume the
# VM's argument marshal order. Axis is the other Java int: 0, 1 or 2.
_start:
    mov edx, DWORD PTR [esp]
    sub edx, 0x584F4242
    cmp edx, 2
    jbe .Lprobe

    mov edx, DWORD PTR [esp+4]
    sub edx, 0x584F4242
    cmp edx, 2
    ja .Lpass

.Lprobe:
    # Return an unmistakable test marker; restore the two pushed Java args.
    # No field access, memory probe or floating-point operation occurs here.
    mov eax, 0x42424242
    add esp, 8
    jmp 0x0047DEE4

.Lpass:
    # Replay the original call-site instructions unchanged.
    mov edx, DWORD PTR [edi+0x4c]
    push edx
    call DWORD PTR [eax+0x3c]
    jmp 0x0047DEE4
