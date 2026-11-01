.intel_syntax noprefix
.section .text
.code32
.global _start
_start:
    # Copy the original GameRef.getInfo(II)I setup through receiver resolution.
    sub esp, 8
    push ebp
    push esi
    push edi
    lea eax, [esp+0x0c]
    push eax
    mov eax, DWORD PTR [esp+0x1c]
    lea ecx, [esp+0x14]
    push ecx
    lea edx, [esp+0x20]
    push edx
    push eax
    xor ebp, ebp
    call 0x0045D910

    # Position-independent access to the VM global at 0x0062E008.
    call .Lget_global_base
.Lget_global_base:
    pop ecx
    lea ecx, [ecx - 0x001B1990]
    mov ecx, DWORD PTR [ecx]
    mov edx, DWORD PTR [esp+0x28]
    push ecx
    push edx
    call 0x0042AB50
    add esp, 0x18

    # getInfo(query, axis): query=[ESP+0x0C], axis=[ESP+0x10].
    # Non-reserved requests resume at the original dispatcher continuation.
    cmp DWORD PTR [esp+0x0c], 0x584F4242
    jne .Lresume_original

    test eax, eax
    je .Lno_object
    cmp DWORD PTR [eax+0x08], 0
    je .Lno_object
    mov esi, DWORD PTR [eax+0x0c]
    test esi, esi
    je .Lno_object

    # ResourceRef.type() reads +0x4C. U_MESH == 5; AABB mode == 1.
    cmp DWORD PTR [esi+0x4c], 5
    jne .Lwrong_type
    cmp DWORD PTR [esi+0x80], 1
    jne .Lwrong_bound_mode

    mov edx, DWORD PTR [esp+0x10]
    cmp edx, 2
    ja .Lbad_axis

    # Return one float32 center component multiplied by 1000 as a Java int.
    fld DWORD PTR [esi+edx*4+0x84]
    push 1000
    fimul DWORD PTR [esp]
    sub esp, 4
    fistp DWORD PTR [esp]
    mov eax, DWORD PTR [esp]
    add esp, 8
    jmp .Lreturn

.Lno_object:
    mov eax, 0x80000000
    jmp .Lreturn
.Lwrong_type:
    mov eax, 0x80000001
    jmp .Lreturn
.Lwrong_bound_mode:
    mov eax, 0x80000002
    jmp .Lreturn
.Lbad_axis:
    mov eax, 0x80000003

.Lreturn:
    pop edi
    pop esi
    pop ebp
    add esp, 8
    ret

.Lresume_original:
    jmp 0x0047DE25
