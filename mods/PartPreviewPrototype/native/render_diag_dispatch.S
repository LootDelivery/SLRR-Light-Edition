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