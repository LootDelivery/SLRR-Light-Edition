.intel_syntax noprefix
.code32
.section .text
.global aabb_helper

# int __stdcall aabb_helper(void* nativeGameRef, int axis)
# Returns round(coordinate * 1000) as an ordinary int; AABB_INVALID on failure.
aabb_helper:
    push ebp
    mov ebp, esp
    sub esp, 0x64
    push ebx
    push esi
    push edi

    mov dword ptr [ebp-0x54], 0

    # Resolve GameRef -> native ResourceHandle.
    mov eax, dword ptr [ebp+0x08]
    test eax, eax
    jz .invalid
    cmp dword ptr [eax+0x08], 0
    je .invalid
    mov esi, dword ptr [eax+0x0c]
    test esi, esi
    jz .invalid

    # GameRef(meshRID) must resolve to RESOURCE_MESH (type 5).
    cmp dword ptr [esi+0x4c], 5
    jne .invalid

    # Match ResourceRef.scaleMesh's preparation and resolver sequence.
    mov eax, dword ptr [esi]
    push 0x3f800000
    mov ecx, esi
    call dword ptr [eax+0x14]

    push 0
    push 0
    push 0x80000000
    mov ecx, esi
    call 0x005447d0
    test eax, 0x80000000
    jnz .invalid

    mov eax, dword ptr [esi]
    push 0x3f800000
    mov ecx, esi
    call dword ptr [eax+0x0c]
    test eax, eax
    jz .invalid

    # The payload wrapper's +0x18 points at the root visual mesh object.
    mov eax, dword ptr [eax+0x18]
    test eax, eax
    jz .invalid
    cmp dword ptr [eax], 0x005f2d00
    jne .invalid

    mov dword ptr [ebp-0x08], eax
    mov ecx, dword ptr [eax+0x4c]
    test ecx, ecx
    jle .invalid
    cmp ecx, 0x1000
    ja .invalid
    mov dword ptr [ebp-0x10], ecx
    mov eax, dword ptr [eax+0x48]
    test eax, eax
    jz .invalid
    mov dword ptr [ebp-0x0c], eax
    mov dword ptr [ebp-0x14], 0

.group_loop:
    mov eax, dword ptr [ebp-0x14]
    cmp eax, dword ptr [ebp-0x10]
    jae .scan_done
    mov edx, dword ptr [ebp-0x0c]
    mov edi, dword ptr [edx+eax*4]
    test edi, edi
    jz .next_group

    mov ecx, dword ptr [edi+0x08]
    test ecx, ecx
    jle .next_group
    cmp ecx, 0x10000
    ja .invalid
    mov dword ptr [ebp-0x1c], ecx
    mov eax, dword ptr [edi+0x04]
    test eax, eax
    jz .next_group
    mov dword ptr [ebp-0x18], eax
    mov dword ptr [ebp-0x20], 0

.child_loop:
    mov eax, dword ptr [ebp-0x20]
    cmp eax, dword ptr [ebp-0x1c]
    jae .next_group
    mov edx, dword ptr [ebp-0x18]
    mov ebx, dword ptr [edx+eax*4]
    test ebx, ebx
    jz .next_child
    cmp dword ptr [ebx], 0x005f2c08
    jne .next_child

    mov eax, dword ptr [ebx+0x1c]
    test eax, eax
    jz .next_child
    mov dword ptr [ebp-0x24], eax

    # Require the position attribute, a non-empty buffer, and sane stride.
    test dword ptr [eax+0x0c], 1
    jz .next_child
    mov ecx, dword ptr [eax+0x04]
    test ecx, ecx
    jle .next_child
    cmp ecx, 0x100000
    ja .next_child
    mov dword ptr [ebp-0x28], ecx

    mov eax, dword ptr [eax+0x08]
    test eax, eax
    jz .next_child
    mov dword ptr [ebp-0x30], eax

    mov eax, dword ptr [ebp-0x24]
    mov ecx, dword ptr [eax+0x60]
    cmp ecx, 12
    jb .next_child
    cmp ecx, 0x10000
    ja .next_child
    mov dword ptr [ebp-0x38], ecx

    # +0x1c is the position attribute's byte offset within each vertex.
    mov eax, dword ptr [eax+0x1c]
    mov edx, dword ptr [ebp-0x38]
    sub edx, 12
    cmp eax, edx
    ja .next_child
    mov dword ptr [ebp-0x34], eax
    mov dword ptr [ebp-0x2c], 0

.vertex_loop:
    mov ecx, dword ptr [ebp-0x2c]
    cmp ecx, dword ptr [ebp-0x28]
    jae .next_child
    imul ecx, dword ptr [ebp-0x38]
    jo .skip_vertex
    add ecx, dword ptr [ebp-0x30]
    jc .skip_vertex
    add ecx, dword ptr [ebp-0x34]
    jc .skip_vertex

    mov eax, dword ptr [ecx]
    mov dword ptr [ebp-0x58], eax
    mov edx, eax
    and edx, 0x7f800000
    cmp edx, 0x7f800000
    je .skip_vertex

    mov eax, dword ptr [ecx+4]
    mov dword ptr [ebp-0x5c], eax
    mov edx, eax
    and edx, 0x7f800000
    cmp edx, 0x7f800000
    je .skip_vertex

    mov eax, dword ptr [ecx+8]
    mov dword ptr [ebp-0x60], eax
    mov edx, eax
    and edx, 0x7f800000
    cmp edx, 0x7f800000
    je .skip_vertex

    cmp dword ptr [ebp-0x54], 0
    jne .update_bounds
    mov eax, dword ptr [ebp-0x58]
    mov dword ptr [ebp-0x3c], eax
    mov dword ptr [ebp-0x48], eax
    mov eax, dword ptr [ebp-0x5c]
    mov dword ptr [ebp-0x40], eax
    mov dword ptr [ebp-0x4c], eax
    mov eax, dword ptr [ebp-0x60]
    mov dword ptr [ebp-0x44], eax
    mov dword ptr [ebp-0x50], eax
    mov dword ptr [ebp-0x54], 1
    jmp .skip_vertex

.update_bounds:
    # Min X: update only when x < minX (unordered values were filtered).
    fld dword ptr [ebp-0x58]
    fcom dword ptr [ebp-0x3c]
    fnstsw ax
    fstp st(0)
    sahf
    jae .min_y
    mov eax, dword ptr [ebp-0x58]
    mov dword ptr [ebp-0x3c], eax
.min_y:
    fld dword ptr [ebp-0x5c]
    fcom dword ptr [ebp-0x40]
    fnstsw ax
    fstp st(0)
    sahf
    jae .min_z
    mov eax, dword ptr [ebp-0x5c]
    mov dword ptr [ebp-0x40], eax
.min_z:
    fld dword ptr [ebp-0x60]
    fcom dword ptr [ebp-0x44]
    fnstsw ax
    fstp st(0)
    sahf
    jae .max_x
    mov eax, dword ptr [ebp-0x60]
    mov dword ptr [ebp-0x44], eax
.max_x:
    fld dword ptr [ebp-0x58]
    fcom dword ptr [ebp-0x48]
    fnstsw ax
    fstp st(0)
    sahf
    jbe .max_y
    mov eax, dword ptr [ebp-0x58]
    mov dword ptr [ebp-0x48], eax
.max_y:
    fld dword ptr [ebp-0x5c]
    fcom dword ptr [ebp-0x4c]
    fnstsw ax
    fstp st(0)
    sahf
    jbe .max_z
    mov eax, dword ptr [ebp-0x5c]
    mov dword ptr [ebp-0x4c], eax
.max_z:
    fld dword ptr [ebp-0x60]
    fcom dword ptr [ebp-0x50]
    fnstsw ax
    fstp st(0)
    sahf
    jbe .skip_vertex
    mov eax, dword ptr [ebp-0x60]
    mov dword ptr [ebp-0x50], eax

.skip_vertex:
    inc dword ptr [ebp-0x2c]
    jmp .vertex_loop

.next_child:
    inc dword ptr [ebp-0x20]
    jmp .child_loop

.next_group:
    inc dword ptr [ebp-0x14]
    jmp .group_loop

.scan_done:
    cmp dword ptr [ebp-0x54], 0
    je .invalid
    mov eax, dword ptr [ebp+0x0c]
    cmp eax, 0
    je .return_min_x
    cmp eax, 1
    je .return_min_y
    cmp eax, 2
    je .return_min_z
    cmp eax, 3
    je .return_max_x
    cmp eax, 4
    je .return_max_y
    cmp eax, 5
    je .return_max_z
    jmp .invalid
.return_min_x:
    fld dword ptr [ebp-0x3c]
    jmp .return_scaled
.return_min_y:
    fld dword ptr [ebp-0x40]
    jmp .return_scaled
.return_min_z:
    fld dword ptr [ebp-0x44]
    jmp .return_scaled
.return_max_x:
    fld dword ptr [ebp-0x48]
    jmp .return_scaled
.return_max_y:
    fld dword ptr [ebp-0x4c]
    jmp .return_scaled
.return_max_z:
    fld dword ptr [ebp-0x50]
.return_scaled:
    # SLRR's bundled java.lang.Float has no intBitsToFloat().
    # Return signed fixed-point coordinate * 1000 using the existing int API.
    push 1000
    fimul dword ptr [esp]
    sub esp, 4
    fistp dword ptr [esp]
    mov eax, dword ptr [esp]
    add esp, 8
    jmp .epilogue
.invalid:
    mov eax, 0x80000000
.epilogue:
    pop edi
    pop esi
    pop ebx
    mov esp, ebp
    pop ebp
    ret 8
