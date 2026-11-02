.intel_syntax noprefix
.code32
.section .text
.global aabb_transform_vertex
.global transformed_center

# Called by a JMP detour from aabb_helper at 0x005E6DCA.
# EBP remains the aabb_helper frame. The current raw vertex position is at
# [EBP-0x58/-0x5C/-0x60]. The optional transform context is passed through
# the synthetic GameRef at [EBP+8]: +0x10=context pointer, +0x14=magic.
# Context = { INSTANCE_RENDER*, selected bone/node* }.
# If the marker is absent, the normal local-AABB path is unchanged.
aabb_transform_vertex:
    push eax
    push ebx
    push ecx
    push edx
    push edi

    mov eax, dword ptr [ebp+0x08]
    cmp dword ptr [eax+0x14], 0x54424142
    jne .vertex_transform_done
    mov eax, dword ptr [eax+0x10]
    test eax, eax
    je .vertex_transform_done
    mov edx, dword ptr [eax]
    test edx, edx
    je .vertex_transform_done
    mov ebx, dword ptr [eax+4]
    test ebx, ebx
    je .vertex_transform_done

    # Keep the raw XYZ tuple intact until all three output coordinates have
    # been calculated. The min/max scanner stores transformed values back into
    # its locals, so later axes must read these saved originals.
    push dword ptr [ebp-0x58]   # original x -> [esp+8]
    push dword ptr [ebp-0x5c]   # original y -> [esp+4]
    push dword ptr [ebp-0x60]   # original z -> [esp]

    # X = x*sx*m00 + y*sy*m10 + z*sz*m20 + position.x
    fld dword ptr [esp+8]
    fmul dword ptr [edx+0x90]
    fmul dword ptr [ebx+0xb0]
    fld dword ptr [esp+4]
    fmul dword ptr [edx+0x94]
    fmul dword ptr [ebx+0xc0]
    faddp st(1), st(0)
    fld dword ptr [esp]
    fmul dword ptr [edx+0x98]
    fmul dword ptr [ebx+0xd0]
    faddp st(1), st(0)
    fadd dword ptr [edx+0x84]
    fstp dword ptr [ebp-0x58]

    # Y = x*sx*m01 + y*sy*m11 + z*sz*m21 + position.y
    fld dword ptr [esp+8]
    fmul dword ptr [edx+0x90]
    fmul dword ptr [ebx+0xb4]
    fld dword ptr [esp+4]
    fmul dword ptr [edx+0x94]
    fmul dword ptr [ebx+0xc4]
    faddp st(1), st(0)
    fld dword ptr [esp]
    fmul dword ptr [edx+0x98]
    fmul dword ptr [ebx+0xd4]
    faddp st(1), st(0)
    fadd dword ptr [edx+0x88]
    fstp dword ptr [ebp-0x5c]

    # Z = x*sx*m02 + y*sy*m12 + z*sz*m22 + position.z
    fld dword ptr [esp+8]
    fmul dword ptr [edx+0x90]
    fmul dword ptr [ebx+0xb8]
    fld dword ptr [esp+4]
    fmul dword ptr [edx+0x94]
    fmul dword ptr [ebx+0xc8]
    faddp st(1), st(0)
    fld dword ptr [esp]
    fmul dword ptr [edx+0x98]
    fmul dword ptr [ebx+0xd8]
    faddp st(1), st(0)
    fadd dword ptr [edx+0x8c]
    fstp dword ptr [ebp-0x60]

    add esp, 12

.vertex_transform_done:
    pop edi
    pop edx
    pop ecx
    pop ebx
    pop eax

    # Reproduce the original first-vertex / update-bounds branch that the
    # detour replaced (CMP [EBP-0x54],0; JNE 0x005E6DF4).
    cmp dword ptr [ebp-0x54], 0
    jne 0x005e6df4
    jmp 0x005e6dd0

# Replacement for the old nested_mesh_center routine at 0x007E1A3C.
# It asks the existing CPU-vertex scanner for min/max AFTER every vertex has
# been transformed by INSTANCE_RENDER scale, selected-node basis and position.
# Thus it returns the center of the transformed geometry AABB, not the
# transform of the untransformed local-AABB midpoint.
transformed_center:
    push ebx
    push edi
    sub esp, 0x50
    mov dword ptr [esp+0x4c], ecx

    # Resolve the embedded RESOURCE_MESH (type 5).
    mov edx, dword ptr [esi+0x15c]
    test edx, edx
    je .center_fail
    mov edx, dword ptr [edx+0x98]
    test edx, edx
    je .center_fail
    cmp dword ptr [edx+0x4c], 5
    jne .center_fail

    # Do not sample a render instance before its native scale has initialized.
    cmp dword ptr [esi+0x90], 0
    je .center_fail
    cmp dword ptr [esi+0x94], 0
    je .center_fail
    cmp dword ptr [esi+0x98], 0
    je .center_fail

    # Match native 0x5433E0 node selection exactly:
    # empty auxiliary list -> first node; otherwise locate lowercase bone00.
    lea ecx, [esi+0x134]
    call 0x00429390
    test eax, eax
    jnz .center_first_node

    lea ecx, [esi+0x1fc]
    call 0x004293a0
    test eax, eax
    je .center_fail
    mov edi, eax
    mov ebx, 64
.center_find_bone00:
    cmp dword ptr [edi+0x14], 0x656e6f62
    jne .center_next_bone
    cmp word ptr [edi+0x18], 0x3030
    je .center_node_ready
.center_next_bone:
    mov ecx, edi
    call 0x0040cfc0
    test eax, eax
    je .center_fail
    mov edi, eax
    dec ebx
    jnz .center_find_bone00
    jmp .center_fail

.center_first_node:
    lea ecx, [esi+0x1fc]
    call 0x004293a0
    test eax, eax
    je .center_fail
    mov edi, eax

.center_node_ready:
    # Synthetic GameRef passed to aabb_helper:
    # +08=valid id, +0C=RESOURCE_MESH, +10=context ptr, +14=magic.
    # Context is kept in this stack frame, away from the six AABB outputs.
    mov dword ptr [esp+0x08], 1
    mov edx, dword ptr [esi+0x15c]
    mov edx, dword ptr [edx+0x98]
    mov dword ptr [esp+0x0c], edx
    mov dword ptr [esp+0x30], esi
    mov dword ptr [esp+0x34], edi
    lea eax, [esp+0x30]
    mov dword ptr [esp+0x10], eax
    mov dword ptr [esp+0x14], 0x54424142

    # The existing helper returns minX,minY,minZ,maxX,maxY,maxZ * 1000.
    push 0
    lea eax, [esp+4]
    push eax
    call 0x005e6900
    cmp eax, 0x80000000
    je .center_fail
    mov dword ptr [esp+0x18], eax

    push 1
    lea eax, [esp+4]
    push eax
    call 0x005e6900
    cmp eax, 0x80000000
    je .center_fail
    mov dword ptr [esp+0x1c], eax

    push 2
    lea eax, [esp+4]
    push eax
    call 0x005e6900
    cmp eax, 0x80000000
    je .center_fail
    mov dword ptr [esp+0x20], eax

    push 3
    lea eax, [esp+4]
    push eax
    call 0x005e6900
    cmp eax, 0x80000000
    je .center_fail
    mov dword ptr [esp+0x24], eax

    push 4
    lea eax, [esp+4]
    push eax
    call 0x005e6900
    cmp eax, 0x80000000
    je .center_fail
    mov dword ptr [esp+0x28], eax

    push 5
    lea eax, [esp+4]
    push eax
    call 0x005e6900
    cmp eax, 0x80000000
    je .center_fail
    mov dword ptr [esp+0x2c], eax

    # Midpoint of transformed AABB, preserving the native fixed-point scale.
    mov eax, dword ptr [esp+0x18]
    add eax, dword ptr [esp+0x24]
    jo .center_fail
    sar eax, 1
    mov dword ptr [esp+0x38], eax

    mov eax, dword ptr [esp+0x1c]
    add eax, dword ptr [esp+0x28]
    jo .center_fail
    sar eax, 1
    mov dword ptr [esp+0x3c], eax

    mov eax, dword ptr [esp+0x20]
    add eax, dword ptr [esp+0x2c]
    jo .center_fail
    sar eax, 1
    mov dword ptr [esp+0x40], eax

    mov ecx, dword ptr [esp+0x4c]
    cmp ecx, 500
    je .center_return_x
    cmp ecx, 501
    je .center_return_y
    cmp ecx, 502
    je .center_return_z
    jmp .center_fail
.center_return_x:
    mov eax, dword ptr [esp+0x38]
    jmp .center_return
.center_return_y:
    mov eax, dword ptr [esp+0x3c]
    jmp .center_return
.center_return_z:
    mov eax, dword ptr [esp+0x40]
    jmp .center_return

.center_fail:
    mov eax, 0x80000000
.center_return:
    add esp, 0x50
    pop edi
    pop ebx
    jmp 0x005e6ec5
