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

    # INSTANCE_RENDER (type 3) diagnostics. Axes 100..119 expose the
    # object's first 20 raw DWORDs. Axes 120..156 inspect the linked
    # render-record list and the 4x4 matrix stored in each record.
    mov ecx, dword ptr [ebp+0x0c]
    cmp dword ptr [esi+0x4c], 3
    jne .require_mesh
    cmp ecx, 100
    jb .invalid
    cmp ecx, 119
    jbe .diag_render_handle_fields
    cmp ecx, 156
    jbe .diag_render_record_fields
    cmp ecx, 195
    jbe .diag_render_outer_fields
    cmp ecx, 220
    jbe .diag_render_inner_fields
    jmp .invalid

.diag_render_handle_fields:
    sub ecx, 100
    mov eax, dword ptr [esi+ecx*4]
    jmp .return_raw

.diag_render_outer_fields:
    # Outer INSTANCE_RENDER fields at +0x50..+0xDC, raw DWORDs only.
    sub ecx, 160
    add ecx, 20
    mov eax, dword ptr [esi+ecx*4]
    jmp .return_raw

.diag_render_inner_fields:
    # Embedded render-list object at outer handle +0xD8, offsets +0x00..+0x50.
    sub ecx, 200
    mov eax, dword ptr [esi+ecx*4+0xd8]
    jmp .return_raw

.require_mesh:
    # All ordinary AABB queries still require RESOURCE_MESH (type 5).
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
    # Both visual root factory variants share the common group/bounds layout.
    # Type 3 uses vtable 0x005F2D00; type 4 uses vtable 0x005F2CE8.
    cmp dword ptr [eax], 0x005f2d00
    je .root_supported
    cmp dword ptr [eax], 0x005f2ce8
    jne .invalid

.root_supported:
    mov dword ptr [ebp-0x08], eax

    # Read-only runtime group diagnostics. These return raw 32-bit field values
    # (not coordinates) so Java can log the actual group/FaceIndices wiring.
    mov ecx, dword ptr [ebp+0x0c]
    cmp ecx, 12
    jb .normal_axis_dispatch
    cmp ecx, 13
    je .diag_groups_ptr
    cmp ecx, 12
    je .diag_group_count
    cmp ecx, 14
    jb .invalid
    cmp ecx, 41
    jbe .diag_group_fields
    cmp ecx, 61
    ja .invalid
    cmp ecx, 52
    jb .diag_vertex_zero
    jmp .diag_vertex_one

.diag_group_fields:
    cmp ecx, 28
    jb .diag_group_zero
    jmp .diag_group_one

.diag_groups_ptr:
    mov eax, dword ptr [ebp-0x08]
    mov eax, dword ptr [eax+0x48]
    jmp .return_raw
.diag_group_count:
    mov eax, dword ptr [ebp-0x08]
    mov eax, dword ptr [eax+0x4c]
    jmp .return_raw

.diag_group_zero:
    mov eax, dword ptr [ebp-0x08]
    cmp dword ptr [eax+0x4c], 1
    jb .invalid
    mov edx, dword ptr [eax+0x48]
    test edx, edx
    jz .invalid
    mov edi, dword ptr [edx]
    mov ecx, dword ptr [ebp+0x0c]
    sub ecx, 14
    jmp .diag_group_field

.diag_group_one:
    mov eax, dword ptr [ebp-0x08]
    cmp dword ptr [eax+0x4c], 2
    jb .invalid
    mov edx, dword ptr [eax+0x48]
    test edx, edx
    jz .invalid
    mov edi, dword ptr [edx+4]
    mov ecx, dword ptr [ebp+0x0c]
    sub ecx, 28

.diag_group_field:
    test edi, edi
    jz .invalid
    cmp ecx, 0
    je .diag_return_wrapper
    cmp ecx, 1
    je .diag_return_inner
    cmp ecx, 2
    je .diag_return_children
    cmp ecx, 3
    je .diag_return_child_count
    cmp ecx, 4
    je .diag_return_child_capacity

    mov eax, dword ptr [edi]
    test eax, eax
    jz .invalid
    cmp ecx, 5
    je .diag_return_vtable
    cmp ecx, 6
    je .diag_inner_04
    cmp ecx, 7
    je .diag_inner_08
    cmp ecx, 8
    je .diag_inner_0c
    cmp ecx, 9
    je .diag_inner_10
    cmp ecx, 10
    je .diag_inner_14

    mov edx, dword ptr [eax]
    test edx, edx
    jz .invalid
    cmp ecx, 11
    je .diag_vtable_08
    cmp ecx, 12
    je .diag_vtable_10
    cmp ecx, 13
    je .diag_vtable_14
    jmp .invalid

.diag_return_wrapper:
    mov eax, edi
    jmp .return_raw
.diag_return_inner:
    mov eax, dword ptr [edi]
    jmp .return_raw
.diag_return_children:
    mov eax, dword ptr [edi+4]
    jmp .return_raw
.diag_return_child_count:
    mov eax, dword ptr [edi+8]
    jmp .return_raw
.diag_return_child_capacity:
    mov eax, dword ptr [edi+0x0c]
    jmp .return_raw
.diag_return_vtable:
    mov eax, dword ptr [eax]
    jmp .return_raw
.diag_inner_04:
    mov eax, dword ptr [eax+4]
    jmp .return_raw
.diag_inner_08:
    mov eax, dword ptr [eax+8]
    jmp .return_raw
.diag_inner_0c:
    mov eax, dword ptr [eax+0x0c]
    jmp .return_raw
.diag_inner_10:
    mov eax, dword ptr [eax+0x10]
    jmp .return_raw
.diag_inner_14:
    mov eax, dword ptr [eax+0x14]
    jmp .return_raw
.diag_vtable_08:
    mov eax, dword ptr [edx+8]
    jmp .return_raw
.diag_vtable_10:
    mov eax, dword ptr [edx+0x10]
    jmp .return_raw
.diag_vtable_14:
    mov eax, dword ptr [edx+0x14]
    jmp .return_raw

.diag_vertex_zero:
    mov eax, dword ptr [ebp-0x08]
    cmp dword ptr [eax+0x4c], 1
    jb .invalid
    mov edx, dword ptr [eax+0x48]
    test edx, edx
    jz .invalid
    mov edi, dword ptr [edx]
    jmp .diag_vertex_child

.diag_vertex_one:
    mov eax, dword ptr [ebp-0x08]
    cmp dword ptr [eax+0x4c], 2
    jb .invalid
    mov edx, dword ptr [eax+0x48]
    test edx, edx
    jz .invalid
    mov edi, dword ptr [edx+4]

.diag_vertex_child:
    test edi, edi
    jz .invalid
    cmp dword ptr [edi+8], 1
    jb .invalid
    mov edx, dword ptr [edi+4]
    test edx, edx
    jz .invalid
    mov esi, dword ptr [edx]
    test esi, esi
    jz .invalid
    cmp dword ptr [esi], 0x005f2c08
    jne .invalid
    mov ecx, dword ptr [ebp+0x0c]
    cmp ecx, 52
    jb .diag_vertex_axis0
    sub ecx, 52
    jmp .diag_vertex_field
.diag_vertex_axis0:
    sub ecx, 42
.diag_vertex_field:
    cmp ecx, 0
    je .diag_vertex_return_wrapper
    cmp ecx, 1
    je .diag_vertex_return_vtable
    cmp ecx, 2
    je .diag_vertex_return_vertex_desc
    cmp ecx, 3
    je .diag_vertex_return_face_desc
    mov eax, dword ptr [esi+0x20]
    test eax, eax
    jz .invalid
    cmp ecx, 4
    je .diag_face_count
    cmp ecx, 5
    je .diag_face_buffer
    cmp ecx, 6
    je .diag_face_format
    cmp ecx, 7
    je .diag_face_stride
    mov edx, dword ptr [eax+4]
    test edx, edx
    jz .invalid
    cmp ecx, 8
    je .diag_face_data0
    cmp ecx, 9
    je .diag_face_data1
    jmp .invalid
.diag_vertex_return_wrapper:
    mov eax, esi
    jmp .return_raw
.diag_vertex_return_vtable:
    mov eax, dword ptr [esi]
    jmp .return_raw
.diag_vertex_return_vertex_desc:
    mov eax, dword ptr [esi+0x1c]
    jmp .return_raw
.diag_vertex_return_face_desc:
    mov eax, dword ptr [esi+0x20]
    jmp .return_raw
.diag_face_count:
    mov eax, dword ptr [eax]
    jmp .return_raw
.diag_face_buffer:
    mov eax, dword ptr [eax+4]
    jmp .return_raw
.diag_face_format:
    mov eax, dword ptr [eax+8]
    jmp .return_raw
.diag_face_stride:
    mov eax, dword ptr [eax+0xc]
    jmp .return_raw
.diag_face_data0:
    mov eax, dword ptr [edx]
    jmp .return_raw
.diag_face_data1:
    mov eax, dword ptr [edx+4]
    jmp .return_raw

.normal_axis_dispatch:
    # Diagnostic axes 6..11 return the loader's original root AABB at +0xCC..+0xE0.
    # These stored values are not updated by scaleMesh(); compare them with the
    # freshly scanned CPU-vertex bounds to validate the coordinate frame.
    mov ecx, dword ptr [ebp+0x0c]
    cmp ecx, 6
    jb .scan_vertex_bounds
    cmp ecx, 11
    ja .invalid
    sub ecx, 6
    lea edx, [eax+ecx*4+0xcc]
    fld dword ptr [edx]
    jmp .return_scaled

.scan_vertex_bounds:
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
    jmp .return_scaled
.return_raw:
    jmp .epilogue
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
.diag_render_record_fields:
    # vtable slot +0x0C (0x005433D0) returns handle+0xD8.
    # Native 0x005413C0 reads [that pointer+0x12C] == [handle+0x204]
    # as the first linked render record. Each record stores its key at +0x34,
    # next pointer at +0x04, and the 16-DWORD matrix at +0x54.
    mov eax, dword ptr [esi+0x204]
    test eax, eax
    jz .invalid

    cmp ecx, 120
    je .return_raw
    cmp ecx, 121
    je .diag_render_first_key
    cmp ecx, 122
    je .diag_render_first_next

    cmp ecx, 138
    jbe .diag_render_first_matrix
    cmp ecx, 139
    je .diag_render_second_key
    cmp ecx, 140
    je .diag_render_second_next
    cmp ecx, 156
    ja .invalid

    # Second linked record.
    mov edx, dword ptr [eax+0x04]
    test edx, edx
    jz .invalid
    sub ecx, 141
    mov eax, dword ptr [edx+ecx*4+0x54]
    jmp .return_raw

.diag_render_first_key:
    mov eax, dword ptr [eax+0x34]
    jmp .return_raw

.diag_render_first_next:
    mov eax, dword ptr [eax+0x04]
    jmp .return_raw

.diag_render_first_matrix:
    cmp ecx, 123
    jb .invalid
    sub ecx, 123
    mov eax, dword ptr [eax+ecx*4+0x54]
    jmp .return_raw

.diag_render_second_key:
    mov edx, dword ptr [eax+0x04]
    test edx, edx
    jz .invalid
    mov eax, dword ptr [edx+0x34]
    jmp .return_raw

.diag_render_second_next:
    mov edx, dword ptr [eax+0x04]
    test edx, edx
    jz .invalid
    mov eax, dword ptr [edx+0x04]
    jmp .return_raw

.invalid:
    mov eax, 0x80000000
.epilogue:
    pop edi
    pop esi
    pop ebx
    mov esp, ebp
    pop ebp
    ret 8
