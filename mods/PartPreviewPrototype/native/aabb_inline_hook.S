.intel_syntax noprefix
.code32
.section .text
.global aabb_inline_hook

/*
 * Inline dispatch hook at 0x0047DE25, after the stock GameRef.getInfo(II)I
 * parser and receiver resolver have completed.
 *
 * [ESP+0x0C] = first Java int argument (query)
 * [ESP+0x10] = second Java int argument (axis 0..11)
 * EAX        = resolved native GameRef wrapper
 *
 * BBOX only: call aabb_helper(nativeGameRef, axis), which returns the
 * coordinate in signed fixed-point units (coordinate * 1000).
 * Every non-BBOX call replays the original TEST/JZ behavior and resumes
 * at the original dispatcher continuation.
 */
aabb_inline_hook:
    /* Parser outputs Java arguments in declaration order: query at +0x0C, axis at +0x10. */
    cmp DWORD PTR [esp+0x0c], 0x584F4242
    jne .Lnormal_path

    test eax, eax
    je .Linvalid_reference
    cmp DWORD PTR [eax+0x08], 0
    je .Linvalid_reference

    /* stdcall args: push axis first, then native GameRef wrapper. */
    push DWORD PTR [esp+0x10]
    push eax
    call 0x005E6900
    jmp .Lreturn_from_native

.Linvalid_reference:
    mov eax, 0x80000000

.Lreturn_from_native:
    pop edi
    pop esi
    pop ebp
    add esp, 8
    ret

.Lnormal_path:
    /* Original bytes at 0x0047DE25: test eax,eax; jz 0x0047DEF8. */
    test eax, eax
    je 0x0047DEF8
    jmp 0x0047DE2D
