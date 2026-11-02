.intel_syntax noprefix
.code32
.section .text
.global render_diag_dispatch

# Extension dispatcher for the already-tested render-matrix AABB EXE.
# Input state is inherited from aabb_helper:
#   ESI = resolved type-3 native ResourceHandle
#   ECX = BBOX diagnostic axis
#   EBP = aabb_helper frame
# All returned values are raw DWORDs; pointer values are never dereferenced
# by this diagnostic extension.
render_diag_dispatch:
    mov ecx, dword ptr [ebp+0x0c]
    cmp dword ptr [esi+0x4c], 3
    jne .mesh_resource

    # R05: outer INSTANCE_RENDER fields +0x00..+0x4C.
    cmp ecx, 100
    jb .invalid
    cmp ecx, 119
    jbe .raw_handle_low

    # R06: keep the previously-tested linked record / matrix diagnostics.
    cmp ecx, 156
    jbe .record_diagnostics

    # R07: outer fields +0x50..+0xDC.
    cmp ecx, 160
    jb .invalid
    cmp ecx, 220
    jbe .embedded_or_outer

    # R09: embedded render-list fields +0x100..+0x150.
    cmp ecx, 230
    jb .invalid
    cmp ecx, 250
    ja .invalid
    sub ecx, 230
    add ecx, 64
    mov eax, dword ptr [esi+ecx*4+0xd8]
    jmp 0x005e6ec5

.embedded_or_outer:
    # R08: embedded object +0x00..+0x50.
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
