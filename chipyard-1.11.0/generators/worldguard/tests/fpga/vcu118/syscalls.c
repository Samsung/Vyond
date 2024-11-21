// See LICENSE for license details.
#include <stdint.h>

//#include <string.h>
//#include <stdarg.h>
#include <stdio.h>
//#include <limits.h>
//#include <sys/signal.h> 
#include "plic.h"
#include "interrupt.h"
#include "encoding.h"
#include "kprintf.h"

uintptr_t __attribute__((weak)) handle_trap(uintptr_t cause, uintptr_t epc, uintptr_t mtinst, uintptr_t regs[32])
{
  clear_csr(mie, MIP_MEIP);
  kprintf("Something's trapped: mcause: 0x%lx mepc: 0x%lx mtinst: 0x%lx\n",
          cause,
          epc,
          mtinst);

  while (1);
  //set_csr(mie, MIP_MEIP);
}
