# This file documents the previous registration-only pass-through thunk.
# The current clone probe does not assemble a standalone trampoline: its native
# dispatcher is copied byte-for-byte by patch_bbox_center.py, with seven CALL
# fixups and seven duplicated HIGHLOW relocations.
#
# Kept for reference only; not used by the clone-probe patcher.
.intel_syntax noprefix
.section .text
.code32
.global _start
_start:
    jmp 0x0047DDF0
