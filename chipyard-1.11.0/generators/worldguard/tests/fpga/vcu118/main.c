#include <stdint.h>
#include <stdlib.h>


#include <common/init.h>
#include <common/wgcore.h>
#include <common/wgmarker.h>
#include <common/wgchecker.h>
#include <common/wgchecker.h>
#include <common/csr.h>
#include <platform/platform.h>

#include <common/tests/read_unauthorized_cacheline1.h>
#include <common/tests/read_unauthorized_cacheline2.h>
#include <common/tests/icache.h>
#include <common/tests/multiple_permissions_on_cacheline.h>
#include <common/tests/overwrite_stackframe.h>
#include <common/tests/raw_cacheline.h>


#include "encoding.h"
#include "kprintf.h"
#include "plic.h"
#include "uart.h"

plic_instance_t plic0;

int main(void)
{

  kprintf("Starting boot.bin try 12...\n");
	init_uart();

  kprintf("init plic...\n");
  PLIC_init(&plic0, 0x0c000000, 1, 1);
	plic_source wgc_wgdevice = 5;
  PLIC_enable_interrupt(&plic0, wgc_wgdevice);
  PLIC_set_priority(&plic0, wgc_wgdevice, 1);

  kprintf("Enable External Interrupt and disable all others\n");
	set_csr(mstatus, MSTATUS_MIE);
  set_csr(mie,MIP_MEIP);
  kprintf("mstatus: 0x%lx misa: 0x%lx mie: 0x%lx mip: 0x%lx \n",
          read_csr(mstatus),
          read_csr(misa),
          read_csr(mie),
          read_csr(mip)
          );


  init_worldguard();
  read_unauthorized_cacheline1();
  read_unauthorized_cacheline2();
  multiple_permissions_on_cacheline();
  overwrite_stackframe();
  raw_cacheline();

  while (1) {
    kprintf("spining...\n");
    for (int i = 0; i < 20000000; i++);
  }
  return 0;

  return 0;
}
