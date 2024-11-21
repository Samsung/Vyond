// See LICENSE for license details.

#include <stdint.h>
#include <stdio.h>
#include <inttypes.h>
#include "plic.h"
#include "kprintf.h"

extern plic_instance_t plic0;

void user_interrupt_handler(){
  plic_source source = PLIC_claim_interrupt(&plic0);
  kprintf("get interrupt %d\n",source);
  /*do anything you need according to interrupt source number*/
  PLIC_complete_interrupt(&plic0, source);
  return;
}
