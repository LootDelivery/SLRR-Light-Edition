.intel_syntax noprefix
.code32
.section .text
.global centroid_gate
centroid_gate:
    cmp ecx, 503
    jb .resume_aabb
    cmp ecx, 505
    ja .invalid_no_frame
    cmp ecx, 504
    je .cached_y
    cmp ecx, 505
    je .cached_z
    jmp .centroid
.cached_y:
    cmp dword ptr [centroid_cache_state], 504
    jne .centroid
    cmp dword ptr [centroid_cache_handle], esi
    jne .centroid
    mov eax, dword ptr [centroid_cache_y]
    mov dword ptr [centroid_cache_state], 505
    jmp 0x005E6EC5
.cached_z:
    cmp dword ptr [centroid_cache_state], 505
    jne .centroid
    cmp dword ptr [centroid_cache_handle], esi
    jne .centroid
    mov eax, dword ptr [centroid_cache_z]
    mov dword ptr [centroid_cache_state], 0
    jmp 0x005E6EC5
.resume_aabb:
    mov dword ptr [centroid_cache_state], 0
    push ebx
    push edi
    sub esp, 0x50
    jmp 0x007E1A41
.centroid:
    push ebp
    mov ebp, esp
    push ebx
    push esi
    push edi
    sub esp, 0x100
    mov dword ptr [ebp-0x10], ecx
    mov dword ptr [ebp-0x14], esi
    mov dword ptr [centroid_cache_state], 0
    mov dword ptr [ebp-0xC8], 0
    fldz
    fstp qword ptr [ebp-0xAC]
    fldz
    fstp qword ptr [ebp-0xB4]
    fldz
    fstp qword ptr [ebp-0xBC]
    fldz
    fstp qword ptr [ebp-0xC4]
    mov edx, dword ptr [esi+0x15C]
    test edx, edx
    je .invalid
    mov edx, dword ptr [edx+0x98]
    test edx, edx
    je .invalid
    cmp dword ptr [edx+0x4C], 5
    jne .invalid
    mov dword ptr [ebp-0x18], edx
    mov esi, edx
    mov eax, dword ptr [esi]
    push 0x3F800000
    mov ecx, esi
    call dword ptr [eax+0x14]
    push 0
    push 0
    push 0x80000000
    mov ecx, esi
    call 0x005447D0
    test eax, 0x80000000
    jnz .invalid
    mov eax, dword ptr [esi]
    push 0x3F800000
    mov ecx, esi
    call dword ptr [eax+0x0C]
    test eax, eax
    je .invalid
    mov eax, dword ptr [eax+0x18]
    test eax, eax
    je .invalid
    cmp dword ptr [eax], 0x005F2D00
    je .root_ok
    cmp dword ptr [eax], 0x005F2CE8
    jne .invalid
.root_ok:
    mov dword ptr [ebp-0x1C], eax
    mov ecx, dword ptr [eax+0x4C]
    test ecx, ecx
    jle .invalid
    cmp ecx, 0x1000
    ja .invalid
    mov dword ptr [ebp-0x20], ecx
    mov eax, dword ptr [eax+0x48]
    test eax, eax
    je .invalid
    mov dword ptr [ebp-0x24], eax
    mov dword ptr [ebp-0x28], 0
.group_loop:
    mov eax, dword ptr [ebp-0x28]
    cmp eax, dword ptr [ebp-0x20]
    jae .groups_done
    mov edx, dword ptr [ebp-0x24]
    mov edi, dword ptr [edx+eax*4]
    test edi, edi
    je .next_group
    mov ecx, dword ptr [edi+0x08]
    test ecx, ecx
    jle .next_group
    cmp ecx, 0x10000
    ja .invalid
    mov dword ptr [ebp-0x30], ecx
    mov eax, dword ptr [edi+0x04]
    test eax, eax
    je .next_group
    mov dword ptr [ebp-0x2C], eax
    mov dword ptr [ebp-0x34], 0
.child_loop:
    mov eax, dword ptr [ebp-0x34]
    cmp eax, dword ptr [ebp-0x30]
    jae .next_group
    mov edx, dword ptr [ebp-0x2C]
    mov ebx, dword ptr [edx+eax*4]
    test ebx, ebx
    je .next_child
    cmp dword ptr [ebx], 0x005F2C08
    jne .next_child
    mov eax, dword ptr [ebx+0x1C]
    test eax, eax
    je .invalid
    mov dword ptr [ebp-0x38], eax
    test dword ptr [eax+0x0C], 1
    jz .next_child
    mov edx, dword ptr [ebx+0x20]
    test edx, edx
    je .invalid
    mov dword ptr [ebp-0x3C], edx
    mov ecx, dword ptr [eax+0x04]
    test ecx, ecx
    jle .invalid
    cmp ecx, 0x10000
    ja .invalid
    mov dword ptr [ebp-0x40], ecx
    mov ecx, dword ptr [eax+0x08]
    test ecx, ecx
    je .invalid
    mov dword ptr [ebp-0x44], ecx
    mov ecx, dword ptr [eax+0x60]
    cmp ecx, 12
    jb .invalid
    cmp ecx, 0x10000
    ja .invalid
    mov dword ptr [ebp-0x48], ecx
    mov edx, dword ptr [eax+0x1C]
    mov eax, ecx
    sub eax, 12
    cmp edx, eax
    ja .invalid
    mov dword ptr [ebp-0x4C], edx
    mov eax, dword ptr [ebp-0x3C]
    mov ecx, dword ptr [eax]
    test ecx, ecx
    jle .invalid
    cmp ecx, 0x100000
    ja .invalid
    mov eax, ecx
    xor edx, edx
    mov ebx, 3
    div ebx
    test edx, edx
    jnz .invalid
    mov eax, dword ptr [ebp-0x3C]
    cmp dword ptr [eax+0x08], 2
    jne .invalid
    cmp dword ptr [eax+0x0C], 2
    jne .invalid
    mov ecx, dword ptr [eax]
    mov dword ptr [ebp-0x50], ecx
    mov edx, dword ptr [eax+0x04]
    test edx, edx
    je .invalid
    mov dword ptr [ebp-0x54], edx
    mov dword ptr [ebp-0x58], 0
.triangle_loop:
    mov eax, dword ptr [ebp-0x58]
    cmp eax, dword ptr [ebp-0x50]
    jae .next_child
    mov edx, dword ptr [ebp-0x54]
    movzx ebx, word ptr [edx+eax*2]
    cmp ebx, dword ptr [ebp-0x40]
    jae .invalid
    mov dword ptr [ebp-0x5C], ebx
    lea eax, [eax+1]
    movzx ebx, word ptr [edx+eax*2]
    cmp ebx, dword ptr [ebp-0x40]
    jae .invalid
    mov dword ptr [ebp-0x60], ebx
    lea eax, [eax+1]
    movzx ebx, word ptr [edx+eax*2]
    cmp ebx, dword ptr [ebp-0x40]
    jae .invalid
    mov dword ptr [ebp-0x64], ebx
    mov eax, dword ptr [ebp-0x5C]
    imul eax, dword ptr [ebp-0x48]
    jo .invalid
    add eax, dword ptr [ebp-0x44]
    jc .invalid
    add eax, dword ptr [ebp-0x4C]
    jc .invalid
    mov dword ptr [ebp-0x68], eax
    mov eax, dword ptr [ebp-0x60]
    imul eax, dword ptr [ebp-0x48]
    jo .invalid
    add eax, dword ptr [ebp-0x44]
    jc .invalid
    add eax, dword ptr [ebp-0x4C]
    jc .invalid
    mov dword ptr [ebp-0x6C], eax
    mov eax, dword ptr [ebp-0x64]
    imul eax, dword ptr [ebp-0x48]
    jo .invalid
    add eax, dword ptr [ebp-0x44]
    jc .invalid
    add eax, dword ptr [ebp-0x4C]
    jc .invalid
    mov dword ptr [ebp-0x70], eax
    mov edx, dword ptr [ebp-0x68]
    mov eax, dword ptr [edx]
    mov ebx, eax
    and ebx, 0x7F800000
    cmp ebx, 0x7F800000
    je .invalid
    mov dword ptr [ebp-0x74], eax
    mov eax, dword ptr [edx+4]
    mov ebx, eax
    and ebx, 0x7F800000
    cmp ebx, 0x7F800000
    je .invalid
    mov dword ptr [ebp-0x78], eax
    mov eax, dword ptr [edx+8]
    mov ebx, eax
    and ebx, 0x7F800000
    cmp ebx, 0x7F800000
    je .invalid
    mov dword ptr [ebp-0x7C], eax
    mov edx, dword ptr [ebp-0x6C]
    mov eax, dword ptr [edx]
    mov ebx, eax
    and ebx, 0x7F800000
    cmp ebx, 0x7F800000
    je .invalid
    mov dword ptr [ebp-0x80], eax
    mov eax, dword ptr [edx+4]
    mov ebx, eax
    and ebx, 0x7F800000
    cmp ebx, 0x7F800000
    je .invalid
    mov dword ptr [ebp-0x84], eax
    mov eax, dword ptr [edx+8]
    mov ebx, eax
    and ebx, 0x7F800000
    cmp ebx, 0x7F800000
    je .invalid
    mov dword ptr [ebp-0x88], eax
    mov edx, dword ptr [ebp-0x70]
    mov eax, dword ptr [edx]
    mov ebx, eax
    and ebx, 0x7F800000
    cmp ebx, 0x7F800000
    je .invalid
    mov dword ptr [ebp-0x8C], eax
    mov eax, dword ptr [edx+4]
    mov ebx, eax
    and ebx, 0x7F800000
    cmp ebx, 0x7F800000
    je .invalid
    mov dword ptr [ebp-0x90], eax
    mov eax, dword ptr [edx+8]
    mov ebx, eax
    and ebx, 0x7F800000
    cmp ebx, 0x7F800000
    je .invalid
    mov dword ptr [ebp-0x94], eax
    fld dword ptr [ebp-0x84]
    fsub dword ptr [ebp-0x78]
    fld dword ptr [ebp-0x94]
    fsub dword ptr [ebp-0x7C]
    fmulp st(1), st(0)
    fld dword ptr [ebp-0x88]
    fsub dword ptr [ebp-0x7C]
    fld dword ptr [ebp-0x90]
    fsub dword ptr [ebp-0x78]
    fmulp st(1), st(0)
    fsubp st(1), st(0)
    fstp dword ptr [ebp-0x98]
    fld dword ptr [ebp-0x88]
    fsub dword ptr [ebp-0x7C]
    fld dword ptr [ebp-0x8C]
    fsub dword ptr [ebp-0x74]
    fmulp st(1), st(0)
    fld dword ptr [ebp-0x80]
    fsub dword ptr [ebp-0x74]
    fld dword ptr [ebp-0x94]
    fsub dword ptr [ebp-0x7C]
    fmulp st(1), st(0)
    fsubp st(1), st(0)
    fstp dword ptr [ebp-0x9C]
    fld dword ptr [ebp-0x80]
    fsub dword ptr [ebp-0x74]
    fld dword ptr [ebp-0x90]
    fsub dword ptr [ebp-0x78]
    fmulp st(1), st(0)
    fld dword ptr [ebp-0x84]
    fsub dword ptr [ebp-0x78]
    fld dword ptr [ebp-0x8C]
    fsub dword ptr [ebp-0x74]
    fmulp st(1), st(0)
    fsubp st(1), st(0)
    fstp dword ptr [ebp-0xA0]
    fld dword ptr [ebp-0x98]
    fmul st(0), st(0)
    fld dword ptr [ebp-0x9C]
    fmul st(0), st(0)
    faddp st(1), st(0)
    fld dword ptr [ebp-0xA0]
    fmul st(0), st(0)
    faddp st(1), st(0)
    fsqrt
    fstp dword ptr [ebp-0xA4]
    mov eax, dword ptr [ebp-0xA4]
    mov edx, eax
    and edx, 0x7F800000
    cmp edx, 0x7F800000
    je .invalid
    fld dword ptr [ebp-0xA4]
    fcomp dword ptr [.min_area]
    fnstsw ax
    sahf
    jbe .skip_triangle
    fld dword ptr [ebp-0x74]
    fadd dword ptr [ebp-0x80]
    fadd dword ptr [ebp-0x8C]
    fdiv dword ptr [.three]
    fmul dword ptr [ebp-0xA4]
    fadd qword ptr [ebp-0xB4]
    fstp qword ptr [ebp-0xB4]
    fld dword ptr [ebp-0x78]
    fadd dword ptr [ebp-0x84]
    fadd dword ptr [ebp-0x90]
    fdiv dword ptr [.three]
    fmul dword ptr [ebp-0xA4]
    fadd qword ptr [ebp-0xBC]
    fstp qword ptr [ebp-0xBC]
    fld dword ptr [ebp-0x7C]
    fadd dword ptr [ebp-0x88]
    fadd dword ptr [ebp-0x94]
    fdiv dword ptr [.three]
    fmul dword ptr [ebp-0xA4]
    fadd qword ptr [ebp-0xC4]
    fstp qword ptr [ebp-0xC4]
    fld dword ptr [ebp-0xA4]
    fadd qword ptr [ebp-0xAC]
    fstp qword ptr [ebp-0xAC]
    inc dword ptr [ebp-0xC8]
.skip_triangle:
    add dword ptr [ebp-0x58], 3
    jmp .triangle_loop
.next_child:
    inc dword ptr [ebp-0x34]
    jmp .child_loop
.next_group:
    inc dword ptr [ebp-0x28]
    jmp .group_loop
.groups_done:
    cmp dword ptr [ebp-0xC8], 0
    je .invalid
    fld qword ptr [ebp-0xAC]
    ftst
    fnstsw ax
    sahf
    fstp st(0)
    jbe .invalid
    fld qword ptr [ebp-0xB4]
    fdiv qword ptr [ebp-0xAC]
    fstp dword ptr [ebp-0xCC]
    fld qword ptr [ebp-0xBC]
    fdiv qword ptr [ebp-0xAC]
    fstp dword ptr [ebp-0xD0]
    fld qword ptr [ebp-0xC4]
    fdiv qword ptr [ebp-0xAC]
    fstp dword ptr [ebp-0xD4]
    mov esi, dword ptr [ebp-0x14]
    cmp dword ptr [esi+0x90], 0
    je .invalid
    cmp dword ptr [esi+0x94], 0
    je .invalid
    cmp dword ptr [esi+0x98], 0
    je .invalid
    lea ecx, [esi+0x134]
    call 0x00429390
    test eax, eax
    jnz .first_node
    lea ecx, [esi+0x1FC]
    call 0x004293A0
    test eax, eax
    je .invalid
    mov edi, eax
    mov dword ptr [ebp-0xF0], 64
.find_bone00:
    cmp dword ptr [edi+0x14], 0x656E6F62
    jne .next_bone00
    cmp dword ptr [edi+0x18], 0x00003030
    je .node_ready
.next_bone00:
    mov ecx, edi
    call 0x0040CFC0
    test eax, eax
    je .invalid
    mov edi, eax
    dec dword ptr [ebp-0xF0]
    jnz .find_bone00
    jmp .invalid
.first_node:
    lea ecx, [esi+0x1FC]
    call 0x004293A0
    test eax, eax
    je .invalid
    mov edi, eax
.node_ready:
    fld dword ptr [ebp-0xCC]
    fmul dword ptr [esi+0x90]
    fstp dword ptr [ebp-0xD8]
    fld dword ptr [ebp-0xD0]
    fmul dword ptr [esi+0x94]
    fstp dword ptr [ebp-0xDC]
    fld dword ptr [ebp-0xD4]
    fmul dword ptr [esi+0x98]
    fstp dword ptr [ebp-0xE0]
    fld dword ptr [ebp-0xD8]
    fmul dword ptr [edi+0xB0]
    fld dword ptr [ebp-0xDC]
    fmul dword ptr [edi+0xC0]
    faddp st(1), st(0)
    fld dword ptr [ebp-0xE0]
    fmul dword ptr [edi+0xD0]
    faddp st(1), st(0)
    fadd dword ptr [esi+0x84]
    push 1000
    fimul dword ptr [esp]
    add esp, 4
    fistp dword ptr [ebp-0xE4]
    fld dword ptr [ebp-0xD8]
    fmul dword ptr [edi+0xB4]
    fld dword ptr [ebp-0xDC]
    fmul dword ptr [edi+0xC4]
    faddp st(1), st(0)
    fld dword ptr [ebp-0xE0]
    fmul dword ptr [edi+0xD4]
    faddp st(1), st(0)
    fadd dword ptr [esi+0x88]
    push 1000
    fimul dword ptr [esp]
    add esp, 4
    fistp dword ptr [ebp-0xE8]
    fld dword ptr [ebp-0xD8]
    fmul dword ptr [edi+0xB8]
    fld dword ptr [ebp-0xDC]
    fmul dword ptr [edi+0xC8]
    faddp st(1), st(0)
    fld dword ptr [ebp-0xE0]
    fmul dword ptr [edi+0xD8]
    faddp st(1), st(0)
    fadd dword ptr [esi+0x8C]
    push 1000
    fimul dword ptr [esp]
    add esp, 4
    fistp dword ptr [ebp-0xEC]
    mov eax, dword ptr [ebp-0x14]
    mov dword ptr [centroid_cache_handle], eax
    mov eax, dword ptr [ebp-0xE4]
    mov dword ptr [centroid_cache_x], eax
    mov eax, dword ptr [ebp-0xE8]
    mov dword ptr [centroid_cache_y], eax
    mov eax, dword ptr [ebp-0xEC]
    mov dword ptr [centroid_cache_z], eax
    mov ecx, dword ptr [ebp-0x10]
    cmp ecx, 503
    je .cache_after_x
    mov dword ptr [centroid_cache_state], 0
    cmp ecx, 504
    je .return_y
    mov eax, dword ptr [ebp-0xEC]
    jmp .finish
.cache_after_x:
    mov dword ptr [centroid_cache_state], 504
    mov eax, dword ptr [ebp-0xE4]
    jmp .finish
.return_x:
    mov eax, dword ptr [ebp-0xE4]
    jmp .finish
.return_y:
    mov eax, dword ptr [ebp-0xE8]
    jmp .finish
.invalid:
    mov dword ptr [centroid_cache_state], 0
    mov eax, 0x80000000
.finish:
    add esp, 0x100
    pop edi
    pop esi
    pop ebx
    pop ebp
    jmp 0x005E6EC5
.invalid_no_frame:
    mov dword ptr [centroid_cache_state], 0
    mov eax, 0x80000000
    jmp 0x005E6EC5
.align 4
.min_area:
    .float 1.0e-12
.three:
    .float 3.0
centroid_cache_handle:
    .long 0
centroid_cache_state:
    .long 0
centroid_cache_x:
    .long 0
centroid_cache_y:
    .long 0
centroid_cache_z:
    .long 0