.intel_syntax noprefix
.code32
.section .text
.global hook_start

/* Entry: 0x0047DE25, after stock argument parsing and receiver resolution.
   [ESP+0x10] = first Java int argument (query)
   [ESP+0x0C] = second Java int argument (axis)
   The patcher fills the two final rel32 JMP operands. */
hook_start:
    cmp DWORD PTR [esp+0x10], 0x584F4242
    jne normal_path
    test eax, eax
    jz no_resource
    cmp DWORD PTR [eax+0x08], 0
    je no_resource
    mov esi, DWORD PTR [eax+0x0c]
    test esi, esi
    jz no_resource
    cmp DWORD PTR [esi+0x4c], 5
    jne wrong_type
    cmp DWORD PTR [esi+0x80], 1
    jne wrong_bounds
    mov edx, DWORD PTR [esp+0x0c]
    cmp edx, 2
    ja wrong_axis
    fld DWORD PTR [esi+edx*4+0x84]
    push 1000
    fimul DWORD PTR [esp]
    sub esp, 4
    fistp DWORD PTR [esp]
    mov eax, DWORD PTR [esp]
    add esp, 8
    jmp return_from_native
no_resource:
    mov eax, 0x80000000
    jmp return_from_native
wrong_type:
    mov eax, 0x80000001
    jmp return_from_native
wrong_bounds:
    mov eax, 0x80000002
    jmp return_from_native
wrong_axis:
    mov eax, 0x80000003
return_from_native:
    pop edi
    pop esi
    pop ebp
    add esp, 8
    ret
normal_path:
    test eax, eax
    jz null_original_path
    jmp continue_original_path
null_original_path:
    .byte 0xE9, 0, 0, 0, 0
continue_original_path:
    .byte 0xE9, 0, 0, 0, 0
