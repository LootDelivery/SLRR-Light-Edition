.intel_syntax noprefix
.code32
.section .text
.global render_diag_dispatch

# Read-only extension dispatcher for the PartPreview AABB/native diagnostics.
# Input state inherited from aabb_helper:
#   ESI = resolved type-3 native ResourceHandle
#   ECX = AABB diagnostic axis
#   EBP = aabb_helper frame
# Returned values are raw DWORDs. No runtime fields are written.

render_diag_dispatch:
    mov ecx, dword ptr [ebp+0x0c]
    cmp dword ptr [esi+0x4c], 3
    jne .mesh_resource

    # Raw INSTANCE_RENDER handle fields +0x00..+0x4C.
    cmp ecx, 100
    jb .invalid
    cmp ecx, 119
    jbe .raw_handle_low

    # Existing linked render-record diagnostics.
    cmp ecx, 156
    jbe .record_diagnostics

    # Outer INSTANCE_RENDER fields +0x50..+0xDC.
    cmp ecx, 160
    jb .invalid
    cmp ecx, 220
    jbe .embedded_or_outer

    # Embedded object fields: outer +0x1D8..+0x230.
    cmp ecx, 230
    jb .check_f4_pointer
    cmp ecx, 250
    jbe .embedded_list

.check_f4_pointer:
    # 251 = INSTANCE_RENDER +0xF4 pointer.
    # 252..264 = pointed object +0x120..+0x150.
    # 265..272 = pointed object header +0x00..+0x1C.
    cmp ecx, 251
    je .outer_f4_pointer
    cmp ecx, 272
    jbe .f4_pointer_fields

    # Bone-list diagnostics based on the exact helper path used by 0x5433E0:
    # 273    = empty/non-empty state of auxiliary list at this+0x134.
    # 274    = first node returned from the list at this+0x1FC.
    # 275..277 = first node name/link fields.
    # 278..293 = first node matrix at +0xB0..+0xEC.
    # 294    = node named "bone00" in the same list.
    # 295..310 = bone00 matrix at +0xB0..+0xEC.
    # 311..318 = first eight DWORDs of the bone00 node.
    cmp ecx, 318
    jbe .bone_list_diagnostics

    # Embedded render object fields +0x54..+0xFC (outer +0x12C..+0x1D4).
    cmp ecx, 361
    jbe .embedded_middle_fields

    # Resource descriptor pointer at embedded +0x84, followed by its header.
    cmp ecx, 362
    je .embedded_resource_pointer
    cmp ecx, 384
    jbe .embedded_resource_fields

    # Secondary collection pointer read by native 0x541FE0 at embedded +0x160.
    cmp ecx, 385
    je .embedded_collection_pointer

    # ResourceRef internal payload: 0x419860 returns ResourceRef +0x80.
    cmp ecx, 417
    jbe .resource_payload_fields
    cmp ecx, 418
    je .resource_child_pointer
    cmp ecx, 439
    jbe .resource_child_header
    cmp ecx, 471
    jbe .resource_child_payload
    cmp ecx, 472
    je .resource_collection_pointer
    cmp ecx, 488
    jbe .resource_collection_header
    cmp ecx, 498
    jbe .bone_upper_fields
    cmp ecx, 500
    jb .invalid
    cmp ecx, 502
    jbe .nested_mesh_center
    jmp .invalid

.f4_pointer_fields:
    mov eax, dword ptr [esi+0xf4]
    test eax, eax
    je .invalid
    cmp ecx, 265
    jae .f4_object_header
    cmp ecx, 252
    jb .invalid
    sub ecx, 252
    mov eax, dword ptr [eax+ecx*4+0x120]
    jmp 0x005e6ec5

.f4_object_header:
    sub ecx, 265
    mov eax, dword ptr [eax+ecx*4]
    jmp 0x005e6ec5

.outer_f4_pointer:
    mov eax, dword ptr [esi+0xf4]
    jmp 0x005e6ec5

.bone_list_diagnostics:
    cmp ecx, 273
    je .bone_aux_list_state
    cmp ecx, 293
    jbe .first_node_fields
    jmp .bone_named_fields

.bone_aux_list_state:
    # Native 0x5433E0 calls 0x429390 with ECX=this+0x134.
    lea ecx, [esi+0x134]
    call 0x00429390
    jmp 0x005e6ec5

.first_node_fields:
    push ebx
    mov ebx, ecx
    # Same helper the native path uses at 0x5434B5 / 0x5434F1.
    lea ecx, [esi+0x1fc]
    call 0x004293a0
    test eax, eax
    je .first_node_missing

    cmp ebx, 274
    je .first_node_return
    cmp ebx, 275
    je .first_node_name0
    cmp ebx, 276
    je .first_node_name1
    cmp ebx, 277
    je .first_node_next
    sub ebx, 278
    mov eax, dword ptr [eax+ebx*4+0xb0]
    jmp .first_node_return

.first_node_name0:
    mov eax, dword ptr [eax+0x14]
    jmp .first_node_return
.first_node_name1:
    mov eax, dword ptr [eax+0x18]
    jmp .first_node_return
.first_node_next:
    mov eax, dword ptr [eax+0x04]
    jmp .first_node_return
.first_node_missing:
    pop ebx
    jmp .invalid
.first_node_return:
    pop ebx
    jmp 0x005e6ec5

.bone_named_fields:
    push ebx
    push edi
    mov ebx, ecx
    lea ecx, [esi+0x1fc]
    call 0x004293a0
    test eax, eax
    je .bone00_not_found
    mov edi, 64

.bone00_scan:
    # Inline name at node+0x14 must be "bone00".
    cmp dword ptr [eax+0x14], 0x656e6f62
    jne .bone00_next
    cmp dword ptr [eax+0x18], 0x00003030
    je .bone00_found

.bone00_next:
    mov ecx, eax
    # Exact next-node helper used by the native loop at 0x5434DF.
    call 0x0040cfc0
    test eax, eax
    je .bone00_not_found
    dec edi
    jnz .bone00_scan
    jmp .bone00_not_found

.bone00_found:
    cmp ebx, 294
    je .bone00_return_pointer
    cmp ebx, 310
    ja .bone00_header
    sub ebx, 295
    mov eax, dword ptr [eax+ebx*4+0xb0]
    jmp .bone00_return

.bone00_header:
    sub ebx, 311
    mov eax, dword ptr [eax+ebx*4]
    jmp .bone00_return

.bone00_return_pointer:
.bone00_return:
    pop edi
    pop ebx
    jmp 0x005e6ec5

.bone00_not_found:
    pop edi
    pop ebx
    jmp .invalid

.embedded_middle_fields:
    sub ecx, 319
    add ecx, 21
    mov eax, dword ptr [esi+ecx*4+0xd8]
    jmp 0x005e6ec5

.embedded_resource_pointer:
    mov eax, dword ptr [esi+0x15c]
    jmp 0x005e6ec5

.embedded_resource_fields:
    mov edx, dword ptr [esi+0x15c]
    test edx, edx
    je .invalid
    sub ecx, 363
    mov eax, dword ptr [edx+ecx*4]
    jmp 0x005e6ec5

.embedded_collection_pointer:
    mov eax, dword ptr [esi+0x238]
    jmp 0x005e6ec5

.resource_payload_fields:
    # [ResourceRef+0x80+(axis-386)*4], inner object returned by vtable +0x0C.
    mov edx, dword ptr [esi+0x15c]
    test edx, edx
    je .invalid
    sub ecx, 386
    mov eax, dword ptr [edx+ecx*4+0x80]
    jmp 0x005e6ec5

.resource_child_pointer:
    mov edx, dword ptr [esi+0x15c]
    test edx, edx
    je .invalid
    mov eax, dword ptr [edx+0x98]
    jmp 0x005e6ec5

.resource_child_header:
    mov edx, dword ptr [esi+0x15c]
    test edx, edx
    je .invalid
    mov edx, dword ptr [edx+0x98]
    test edx, edx
    je .invalid
    sub ecx, 419
    mov eax, dword ptr [edx+ecx*4]
    jmp 0x005e6ec5

.resource_child_payload:
    mov edx, dword ptr [esi+0x15c]
    test edx, edx
    je .invalid
    mov edx, dword ptr [edx+0x98]
    test edx, edx
    je .invalid
    sub ecx, 440
    mov eax, dword ptr [edx+ecx*4+0x80]
    jmp 0x005e6ec5

.resource_collection_pointer:
    mov edx, dword ptr [esi+0x15c]
    test edx, edx
    je .invalid
    mov eax, dword ptr [edx+0xb8]
    jmp 0x005e6ec5

.resource_collection_header:
    mov edx, dword ptr [esi+0x15c]
    test edx, edx
    je .invalid
    mov edx, dword ptr [edx+0xb8]
    test edx, edx
    je .invalid
    sub ecx, 473
    mov eax, dword ptr [edx+ecx*4]
    jmp 0x005e6ec5

# Read-only exact-case probe for the inline spelling "Bone00".
# Axis 489 returns the matching node pointer when that exact spelling exists.
# AABB_INVALID means this node list does not contain an inline uppercase match.
# Axes 490..498 return the nine matrix terms used by the center transform:
# [b0,c0,d0,b1,c1,d1,b2,c2,d2] at node offsets B0,C0,D0,B4,C4,D4,B8,C8,D8.
# The existing lowercase "bone00" lookup at axes 294..310 is unchanged.
.bone_upper_fields:
    push ebx
    push edi
    mov ebx, ecx

    lea ecx, [esi+0x1fc]
    call 0x004293a0
    test eax, eax
    je .bone_upper_not_found
    mov edi, 64

.bone_upper_scan:
    # Inline name "Bone00" at node+0x14 / node+0x18.
    cmp dword ptr [eax+0x14], 0x656e6f42
    jne .bone_upper_next
    cmp dword ptr [eax+0x18], 0x00003030
    je .bone_upper_found

.bone_upper_next:
    mov ecx, eax
    call 0x0040cfc0
    test eax, eax
    je .bone_upper_not_found
    dec edi
    jnz .bone_upper_scan
    jmp .bone_upper_not_found

.bone_upper_found:
    cmp ebx, 489
    je .bone_upper_return_pointer
    cmp ebx, 490
    je .bone_upper_m00
    cmp ebx, 491
    je .bone_upper_m01
    cmp ebx, 492
    je .bone_upper_m02
    cmp ebx, 493
    je .bone_upper_m10
    cmp ebx, 494
    je .bone_upper_m11
    cmp ebx, 495
    je .bone_upper_m12
    cmp ebx, 496
    je .bone_upper_m20
    cmp ebx, 497
    je .bone_upper_m21
    cmp ebx, 498
    je .bone_upper_m22
    jmp .bone_upper_not_found

.bone_upper_m00:
    mov eax, dword ptr [eax+0xb0]
    jmp .bone_upper_return
.bone_upper_m01:
    mov eax, dword ptr [eax+0xc0]
    jmp .bone_upper_return
.bone_upper_m02:
    mov eax, dword ptr [eax+0xd0]
    jmp .bone_upper_return
.bone_upper_m10:
    mov eax, dword ptr [eax+0xb4]
    jmp .bone_upper_return
.bone_upper_m11:
    mov eax, dword ptr [eax+0xc4]
    jmp .bone_upper_return
.bone_upper_m12:
    mov eax, dword ptr [eax+0xd4]
    jmp .bone_upper_return
.bone_upper_m20:
    mov eax, dword ptr [eax+0xb8]
    jmp .bone_upper_return
.bone_upper_m21:
    mov eax, dword ptr [eax+0xc8]
    jmp .bone_upper_return
.bone_upper_m22:
    mov eax, dword ptr [eax+0xd8]
    jmp .bone_upper_return

.bone_upper_return_pointer:
.bone_upper_return:
    pop edi
    pop ebx
    jmp 0x005e6ec5

.bone_upper_not_found:
    pop edi
    pop ebx
    jmp .invalid

# Axes 500..502 return the runtime center of the embedded mesh in world
# coordinates, fixed-point * 1000. The nested RESOURCE_MESH AABB is read via
# the existing helper; no Java float-bit decoding or object writes are used.
.nested_mesh_center:
    push ebx
    push edi
    sub esp, 0x50
    mov dword ptr [esp+0x4c], ecx

    # Resolve embedded ResourceRef(type 14) -> child ResourceRef(type 5).
    mov edx, dword ptr [esi+0x15c]
    test edx, edx
    je .nested_center_fail
    mov edx, dword ptr [edx+0x98]
    test edx, edx
    je .nested_center_fail
    cmp dword ptr [edx+0x4c], 5
    jne .nested_center_fail

    # Build a temporary GameRef-shaped receiver for the existing AABB helper.
    # Only +0x08 and +0x0C are read by aabb_helper.
    mov dword ptr [esp+0x08], 1
    mov dword ptr [esp+0x0c], edx

    # Read the six stored root-AABB coordinates (axes 6..11) in fixed-point.
    push 6
    lea eax, [esp+4]
    push eax
    call 0x005e6900
    cmp eax, 0x80000000
    je .nested_center_fail
    mov dword ptr [esp+0x10], eax

    push 7
    lea eax, [esp+4]
    push eax
    call 0x005e6900
    cmp eax, 0x80000000
    je .nested_center_fail
    mov dword ptr [esp+0x14], eax

    push 8
    lea eax, [esp+4]
    push eax
    call 0x005e6900
    cmp eax, 0x80000000
    je .nested_center_fail
    mov dword ptr [esp+0x18], eax

    push 9
    lea eax, [esp+4]
    push eax
    call 0x005e6900
    cmp eax, 0x80000000
    je .nested_center_fail
    mov dword ptr [esp+0x1c], eax

    push 10
    lea eax, [esp+4]
    push eax
    call 0x005e6900
    cmp eax, 0x80000000
    je .nested_center_fail
    mov dword ptr [esp+0x20], eax

    push 11
    lea eax, [esp+4]
    push eax
    call 0x005e6900
    cmp eax, 0x80000000
    je .nested_center_fail
    mov dword ptr [esp+0x24], eax

    # Midpoints remain in the same fixed-point scale (*1000).
    mov eax, dword ptr [esp+0x10]
    add eax, dword ptr [esp+0x1c]
    jo .nested_center_fail
    sar eax, 1
    mov dword ptr [esp+0x28], eax

    mov eax, dword ptr [esp+0x14]
    add eax, dword ptr [esp+0x20]
    jo .nested_center_fail
    sar eax, 1
    mov dword ptr [esp+0x2c], eax

    mov eax, dword ptr [esp+0x18]
    add eax, dword ptr [esp+0x24]
    jo .nested_center_fail
    sar eax, 1
    mov dword ptr [esp+0x30], eax

    # Match the node selection in native 0x5433E0 exactly:
    # an empty auxiliary list uses the first node; otherwise it scans the
    # sorted +0x1FC list for "bone00". The F4 path is also supported: native
    # 0x5433E0 copies its final position/scale into this INSTANCE_RENDER, so
    # the fields read below already contain the post-F4 values.
    lea ecx, [esi+0x134]
    call 0x00429390
    test eax, eax
    jnz .nested_first_node

    lea ecx, [esi+0x1fc]
    call 0x004293a0
    test eax, eax
    je .nested_center_fail
    mov edi, eax
    mov dword ptr [esp+0x10], 64
.nested_find_bone00:
    cmp dword ptr [edi+0x14], 0x656e6f62
    jne .nested_next_bone
    cmp dword ptr [edi+0x18], 0x00003030
    je .nested_node_ready
.nested_next_bone:
    mov ecx, edi
    call 0x0040cfc0
    test eax, eax
    je .nested_center_fail
    mov edi, eax
    dec dword ptr [esp+0x10]
    jnz .nested_find_bone00
    jmp .nested_center_fail

.nested_first_node:
    lea ecx, [esi+0x1fc]
    call 0x004293a0
    test eax, eax
    je .nested_center_fail
    mov edi, eax

.nested_node_ready:
    # The native render update has not initialized the pose if its uniform
    # scale fields are still zero. Do not mistake an uninitialized all-zero
    # transform for a valid world-space center.
    cmp dword ptr [esi+0x90], 0
    je .nested_center_fail
    cmp dword ptr [esi+0x94], 0
    je .nested_center_fail
    cmp dword ptr [esi+0x98], 0
    je .nested_center_fail

    # Scale each local AABB-center component by INSTANCE_RENDER scale.
    fild dword ptr [esp+0x28]
    push 1000
    fidiv dword ptr [esp]
    add esp, 4
    fmul dword ptr [esi+0x90]
    fstp dword ptr [esp+0x34]

    fild dword ptr [esp+0x2c]
    push 1000
    fidiv dword ptr [esp]
    add esp, 4
    fmul dword ptr [esi+0x94]
    fstp dword ptr [esp+0x38]

    fild dword ptr [esp+0x30]
    push 1000
    fidiv dword ptr [esp]
    add esp, 4
    fmul dword ptr [esi+0x98]
    fstp dword ptr [esp+0x3c]

    # WorldCenter = INSTANCE_RENDER.position + selectedNodeMatrix * scaledLocalCenter.
    # selectedNode follows the same branch and name selection as 0x5433E0.
    fld dword ptr [esp+0x34]
    fmul dword ptr [edi+0xb0]
    fld dword ptr [esp+0x38]
    fmul dword ptr [edi+0xc0]
    faddp st(1), st(0)
    fld dword ptr [esp+0x3c]
    fmul dword ptr [edi+0xd0]
    faddp st(1), st(0)
    fadd dword ptr [esi+0x84]
    push 1000
    fimul dword ptr [esp]
    add esp, 4
    fistp dword ptr [esp+0x40]

    fld dword ptr [esp+0x34]
    fmul dword ptr [edi+0xb4]
    fld dword ptr [esp+0x38]
    fmul dword ptr [edi+0xc4]
    faddp st(1), st(0)
    fld dword ptr [esp+0x3c]
    fmul dword ptr [edi+0xd4]
    faddp st(1), st(0)
    fadd dword ptr [esi+0x88]
    push 1000
    fimul dword ptr [esp]
    add esp, 4
    fistp dword ptr [esp+0x44]

    fld dword ptr [esp+0x34]
    fmul dword ptr [edi+0xb8]
    fld dword ptr [esp+0x38]
    fmul dword ptr [edi+0xc8]
    faddp st(1), st(0)
    fld dword ptr [esp+0x3c]
    fmul dword ptr [edi+0xd8]
    faddp st(1), st(0)
    fadd dword ptr [esi+0x8c]
    push 1000
    fimul dword ptr [esp]
    add esp, 4
    fistp dword ptr [esp+0x48]

    mov ecx, dword ptr [esp+0x4c]
    cmp ecx, 500
    je .nested_center_return_x
    cmp ecx, 501
    je .nested_center_return_y
    mov eax, dword ptr [esp+0x48]
    jmp .nested_center_return
.nested_center_return_x:
    mov eax, dword ptr [esp+0x40]
    jmp .nested_center_return
.nested_center_return_y:
    mov eax, dword ptr [esp+0x44]
    jmp .nested_center_return
.nested_center_fail:
    mov eax, 0x80000000
.nested_center_return:
    add esp, 0x50
    pop edi
    pop ebx
    jmp 0x005e6ec5

.embedded_list:
    sub ecx, 230
    add ecx, 64
    mov eax, dword ptr [esi+ecx*4+0xd8]
    jmp 0x005e6ec5

.embedded_or_outer:
    cmp ecx, 195
    jbe .outer_handle_high
    cmp ecx, 200
    jb .invalid
    sub ecx, 200
    mov eax, dword ptr [esi+ecx*4+0xd8]
    jmp 0x005e6ec5

.outer_handle_high:
    sub ecx, 160
    add ecx, 20
    mov eax, dword ptr [esi+ecx*4]
    jmp 0x005e6ec5

.raw_handle_low:
    sub ecx, 100
    mov eax, dword ptr [esi+ecx*4]
    jmp 0x005e6ec5

.record_diagnostics:
    jmp 0x005e6f40

.mesh_resource:
    cmp dword ptr [esi+0x4c], 5
    jne .invalid
    jmp 0x005e693a

.invalid:
    jmp 0x005e6edd