.intel_syntax noprefix
.section .text
.code32
.global _start

# Stage 0: no object dereferences. Save EFLAGS before checking the reserved
# request so ordinary calls enter the original virtual method with identical
# flags, registers, stack arguments, and return address.
_start:
    pushfd

    # At hook entry ESP held [argument B, argument A, ...]. PUSHFD shifts it.
    # Check either slot so this does not depend on argument marshal order.
    mov edx, DWORD PTR [esp+4]
    sub edx, 0x584F4242
    cmp edx, 2
    jbe .Lprobe

    mov edx, DWORD PTR [esp+8]
    sub edx, 0x584F4242
    cmp edx, 2
    ja .Lpass

.Lprobe:
    popfd
    mov eax, 0x42424242
    add esp, 8
    jmp 0x0047DEE4

.Lpass:
    # Original instructions were MOV / PUSH / CALL; they preserve EFLAGS.
    # Restore those flags and synthesize the exact original return address.
    popfd
    mov edx, DWORD PTR [edi+0x4c]
    push edx
    push 0x0047DEE4
    jmp DWORD PTR [eax+0x3c]
