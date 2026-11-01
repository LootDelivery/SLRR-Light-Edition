.intel_syntax noprefix
.section .text
.code32
.global _start

# Registration-only pass-through thunk for GameRef.getInfo(II)I.
# No registers, flags, arguments, or stack contents are modified.
_start:
    jmp 0x0047DDF0
