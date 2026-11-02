.intel_syntax noprefix
.code32
.section .text
.global bone_upper_gate

# Optional exact-case probe for the inline node name "Bone00".
# ECX is the requested AABB_QUERY axis; ESI is the type-3 INSTANCE_RENDER.
# Axes 489..498, 510..515 and 516..535 are served here. Every other request
# returns through the original route, preserving live-AABB center axes 500..502.
bone_upper_gate:
    cmp ecx, 489
    jb .check_native_pivot
    cmp ecx, 498
    jbe .probe

.check_second_bone:
    # Read-only access to the next node in the same list walked by native
    # 0x5433E0. Axes 516..535 expose node #1 (normally bone01):
    # 516 pointer; 517..518 name; 519..534 matrix; 535 next pointer.
    cmp ecx, 516
    jb .check_native_pivot
    cmp ecx, 535
    jbe .second_bone

.check_native_pivot:
    cmp ecx, 510
    jb .check_native_scale
    cmp ecx, 512
    jbe .native_pivot

.check_native_scale:
    cmp ecx, 513
    jb .pass_through
    cmp ecx, 515
    jbe .native_scale

.pass_through:
    # Restore the original axis-500 comparison flags for the stock route.
    cmp ecx, 500
    jmp 0x007E17CD

.second_bone:
    push ebx
    push edi
    mov ebx, ecx

    # Use the exact next-node helper used by native 0x5433E0.
    lea ecx, [esi+0x1FC]
    call 0x004293A0
    test eax, eax
    je .not_found
    mov ecx, eax
    call 0x0040CFC0
    test eax, eax
    je .not_found

    cmp ebx, 516
    je .second_bone_return
    cmp ebx, 517
    je .second_bone_name0
    cmp ebx, 518
    je .second_bone_name1
    cmp ebx, 535
    je .second_bone_next

    sub ebx, 519
    cmp ebx, 15
    ja .not_found
    mov eax, dword ptr [eax+ebx*4+0xB0]
    jmp .second_bone_return

.second_bone_name0:
    mov eax, dword ptr [eax+0x14]
    jmp .second_bone_return
.second_bone_name1:
    mov eax, dword ptr [eax+0x18]
    jmp .second_bone_return
.second_bone_next:
    mov ecx, eax
    call 0x0040CFC0
    test eax, eax
    je .not_found
.second_bone_return:
    pop edi
    pop ebx
    jmp 0x005E6EC5

.native_pivot:
    # Axes 510..512 return INSTANCE_RENDER.position XYZ as signed fixed-point
    # world coordinates (*1000), distinct from the geometry center at 500..502.
    sub ecx, 510
    fld dword ptr [esi+ecx*4+0x84]
    push 1000
    fimul dword ptr [esp]
    add esp, 4
    sub esp, 4
    fistp dword ptr [esp]
    mov eax, dword ptr [esp]
    add esp, 4
    jmp 0x005E6EC5

.native_scale:
    # Axes 513..515 return INSTANCE_RENDER scale XYZ as fixed-point *1000.
    # Java uses these values with each component's live local mesh AABB to
    # calculate a conservative assembly bounding sphere.
    sub ecx, 513
    fld dword ptr [esi+ecx*4+0x90]
    push 1000
    fimul dword ptr [esp]
    add esp, 4
    sub esp, 4
    fistp dword ptr [esp]
    mov eax, dword ptr [esp]
    add esp, 4
    jmp 0x005E6EC5

.probe:
    push ebx
    push edi
    mov ebx, ecx

    lea ecx, [esi+0x1FC]
    call 0x004293A0
    test eax, eax
    je .not_found
    mov edi, 64

.scan:
    cmp dword ptr [eax+0x14], 0x656E6F42
    jne .next
    cmp word ptr [eax+0x18], 0x3030
    je .found

.next:
    mov ecx, eax
    call 0x0040CFC0
    test eax, eax
    je .not_found
    dec edi
    jnz .scan
    jmp .not_found

.found:
    cmp ebx, 489
    je .return_pointer
    cmp ebx, 490
    je .m00
    cmp ebx, 491
    je .m01
    cmp ebx, 492
    je .m02
    cmp ebx, 493
    je .m10
    cmp ebx, 494
    je .m11
    cmp ebx, 495
    je .m12
    cmp ebx, 496
    je .m20
    cmp ebx, 497
    je .m21
    cmp ebx, 498
    je .m22
    jmp .not_found

.m00:
    mov eax, dword ptr [eax+0xB0]
    jmp .success
.m01:
    mov eax, dword ptr [eax+0xC0]
    jmp .success
.m02:
    mov eax, dword ptr [eax+0xD0]
    jmp .success
.m10:
    mov eax, dword ptr [eax+0xB4]
    jmp .success
.m11:
    mov eax, dword ptr [eax+0xC4]
    jmp .success
.m12:
    mov eax, dword ptr [eax+0xD4]
    jmp .success
.m20:
    mov eax, dword ptr [eax+0xB8]
    jmp .success
.m21:
    mov eax, dword ptr [eax+0xC8]
    jmp .success
.m22:
    mov eax, dword ptr [eax+0xD8]
    jmp .success

.return_pointer:
.success:
    pop edi
    pop ebx
    jmp 0x005E6EC5

.not_found:
    pop edi
    pop ebx
    jmp 0x005E6EDD
