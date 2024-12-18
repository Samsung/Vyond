/**
* This test demonstrate protection of program code.
* To make it simple, a function (gcd_ref) is targeted to be protected.
* This test checks if the extension of the instruction cache works as expected.
*
* Author: Sungkeun Kim (sk84.kim@samsung.com)
*/
#include <stdio.h>

#include <riscv-pk/encoding.h>
#include <common/wgcore.h>
#include <common/wgmarker.h>
#include <common/wgchecker.h>
#include <common/wgchecker.h>
#include <platform/platform.h>
#include <common/csr.h>

unsigned int gcd_ref(unsigned int x, unsigned int y) {
  while (y != 0) {
    if (x > y)
      x = x - y;
    else
      y = y - x;
  }
  return x;
}

void config_wgchecker()
{
  write_csr(0x391, 3);
  printf("---------------------------------------------\n");
  printf("Configuring WGC_MEMORY ...\n");
  unsigned int (*pFunc)(unsigned int, unsigned int) = gcd_ref;

  uint64_t lgAlign = 6; // cache line aligned
  printf("pFunc: %p\n", pFunc);
  //----------------------------------------------------------------------------
  // No. | Addr           | CFG   | PERM    | Description
  //----------------------------------------------------------------------------
  // 0   | 0x8000.0000    | 0x0   | 0xc0    | n/a
  // 1   | pFunc          | 0x301 | 0xff    | 0x80000000 <= y < pFunc
  // 2   | pFunc + 0x40*3 | 0x301 | 0xff    | pFunc <= y < pFunc + 0x40 * 3
  // 3   | 0x9000.0000    | 0x301 | 0xff    | parr + 0x40 * 3 <= y < 0x9000.0000
  //----------------------------------------------------------------------------
  config_wgc(0, WGC_MEMORY_BASE, MEMORY_BASE,                    0x0,   0x0,  lgAlign);
  config_wgc(1, WGC_MEMORY_BASE, (uint64_t)(pFunc),              0x301, 0xff, lgAlign);
  config_wgc(2, WGC_MEMORY_BASE, (uint64_t)(pFunc + SZ_CL * 3),  0x301, 0xff, lgAlign);
  config_wgc(3, WGC_MEMORY_BASE, MEMORY_TOP,                     0x301, 0xff, lgAlign);
}

void test_icache()
{
  config_wgchecker();
  for (int wid = 0; wid < 4; wid++) {
    write_csr(0x391, wid);
    printf("[wid%d] calling gcd_ref\n", wid);
    gcd_ref(1000, 2000);
  }
}

