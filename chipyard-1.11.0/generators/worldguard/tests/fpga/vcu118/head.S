.section .text.start, "ax", @progbits
.globl _start
_start:
  li sp, 0x81000000
  la t0, trap_entry
  csrw mtvec, t0
  call main

_hang:
  wfi
  j _hang

  .align 2
trap_entry:
  addi sp, sp, -272

  sd x1, 1*8(sp)
  sd x2, 2*8(sp)
  sd x3, 3*8(sp)
  sd x4, 4*8(sp)
  sd x5, 5*8(sp)
  sd x6, 6*8(sp)
  sd x7, 7*8(sp)
  sd x8, 8*8(sp)
  sd x9, 9*8(sp)
  sd x10, 10*8(sp)
  sd x11, 11*8(sp)
  sd x12, 12*8(sp)
  sd x13, 13*8(sp)
  sd x14, 14*8(sp)
  sd x15, 15*8(sp)
  sd x16, 16*8(sp)
  sd x17, 17*8(sp)
  sd x18, 18*8(sp)
  sd x19, 19*8(sp)
  sd x20, 20*8(sp)
  sd x21, 21*8(sp)
  sd x22, 22*8(sp)
  sd x23, 23*8(sp)
  sd x24, 24*8(sp)
  sd x25, 25*8(sp)
  sd x26, 26*8(sp)
  sd x27, 27*8(sp)
  sd x28, 28*8(sp)
  sd x29, 29*8(sp)
  sd x30, 30*8(sp)
  sd x31, 31*8(sp)

  csrr a0, mcause
  csrr a1, mepc
  csrr a2, mtinst
  mv a3, sp
  jal handle_trap
  csrw mepc, a0

  li t0, 0x00001800
  csrs mstatus, t0

  lw x1, 1*8(sp)
  lw x2, 2*8(sp)
  lw x3, 3*8(sp)
  lw x4, 4*8(sp)
  lw x5, 5*8(sp)
  lw x6, 6*8(sp)
  lw x7, 7*8(sp)
  lw x8, 8*8(sp)
  lw x9, 9*8(sp)
  lw x10, 10*8(sp)
  lw x11, 11*8(sp)
  lw x12, 12*8(sp)
  lw x13, 13*8(sp)
  lw x14, 14*8(sp)
  lw x15, 15*8(sp)
  lw x16, 16*8(sp)
  lw x17, 17*8(sp)
  lw x18, 18*8(sp)
  lw x19, 19*8(sp)
  lw x20, 20*8(sp)
  lw x21, 21*8(sp)
  lw x22, 22*8(sp)
  lw x23, 23*8(sp)
  lw x24, 24*8(sp)
  lw x25, 25*8(sp)
  lw x26, 26*8(sp)
  lw x27, 27*8(sp)
  lw x28, 28*8(sp)
  lw x29, 29*8(sp)
  lw x30, 30*8(sp)
  lw x31, 31*8(sp)

  addi sp, sp, 272
  mret
