/**
* This test is similar to the `read_unauthorized_cacheline1` except that the cachelines are filled with different wids every iteration.
* We hope this case find some corner cases can't found by previous cases.
*
* Author: Sungkeun Kim (sk84.kim@samsung.com)
*/
#include <stdio.h>
#include <riscv-pk/encoding.h>

#include <common/include/csr.h>
#include <common/include/init.h>
#include <common/include/wgcore.h>
#include <common/include/wgmarker.h>
#include <common/include/wgchecker.h>
#include <platform/include/platform.h>

void fill_cacheline(uint8_t* parr)
{
  write_csr(0x391, 3);
  for (int cl = 0; cl < 4; cl++)
      for (int i = 0; i < SZ_CL; i++)
        *(parr + SZ_CL * cl + i) = i + 1;
}

void read_unauthorized_cacheline2()
{
  write_csr(0x391, 3);
  printf("---------------------------------------------\n");
  printf("Testing with CFG TOR ..\n");
  uint8_t arr[10 * SZ_CL];  // allocate more than 4 cache lines
  uint8_t* parr = (uint8_t*)(((uint64_t)arr & ~0x3f) + SZ_CL);  // find address of the first (or maybe the second) cacheline.
  uint64_t lgAlign = 6; // cache line aligned
  printf("arr: %p parr: %p\n", arr, parr);
  //----------------------------------------------------------------------------
  // No. | Addr         | CFG   | PERM    | Description
  //----------------------------------------------------------------------------
  // 0   | 0x8000.0000  | 0x0   | 0xc0    | n/a
  // 1   | parr         | 0x301 | 0xff    | 0x80000000 <= y < parr
  // 2   | parr + 0x40*1| 0x001 | 0xfc    | parr            <= y < parr + 0x40 * 1
  // 3   | parr + 0x40*2| 0x001 | 0xf3    | parr + 0x40 * 1 <= y < parr + 0x40 * 2
  // 4   | parr + 0x40*3| 0x001 | 0xcf    | parr + 0x40 * 2 <= y < parr + 0x40 * 3
  // 5   | parr + 0x40*4| 0x001 | 0x3f    | parr + 0x40 * 3 <= y < parr + 0x40 * 4
  // 6   | 0x9000.0000  | 0x301 | 0xff    | parr + 0x40 * 4 <= y < 0x9000.0000
  //----------------------------------------------------------------------------
  config_wgc(0, WGC_MEMORY_BASE, MEMORY_BASE,                    0x0,   0x0,  lgAlign);
  config_wgc(1, WGC_MEMORY_BASE, (uint64_t)(parr),               0x301, 0xff, lgAlign);
  config_wgc(2, WGC_MEMORY_BASE, (uint64_t)(parr + SZ_CL * 1),   0x001, 0xfc, lgAlign);
  config_wgc(3, WGC_MEMORY_BASE, (uint64_t)(parr + SZ_CL * 2),   0x001, 0xf3, lgAlign);
  config_wgc(4, WGC_MEMORY_BASE, (uint64_t)(parr + SZ_CL * 3),   0x001, 0xcf, lgAlign);
  config_wgc(5, WGC_MEMORY_BASE, (uint64_t)(parr + SZ_CL * 4),   0x001, 0x3f, lgAlign);
  config_wgc(6, WGC_MEMORY_BASE, MEMORY_TOP,                     0x301, 0xff, lgAlign);
  

  printf("---------------------------------------------\n");
  printf("After configure for WG_CHECKER\n");
  wgc_print_slot_reg(WGC_MEMORY_BASE, 0);
  wgc_print_slot_reg(WGC_MEMORY_BASE, 1);
  wgc_print_slot_reg(WGC_MEMORY_BASE, 2);
  wgc_print_slot_reg(WGC_MEMORY_BASE, 3);
  wgc_print_slot_reg(WGC_MEMORY_BASE, 4);
  wgc_print_slot_reg(WGC_MEMORY_BASE, 5);
  wgc_print_slot_reg(WGC_MEMORY_BASE, 6);
  

  for (int wid = 0; wid < 3; wid++) {
    fill_cacheline(parr);
    write_csr(0x391, wid);
    for (int cl = 0; cl < 4; cl++) {
      printf("[wid%d][line%d] read lines\n", wid, cl);
      for (int i = 0; i < SZ_CL; i++) printf("%d ", *(parr + SZ_CL * cl + i));
      printf("\n");
    }
  }
}


int main()
{
  printf("---------------------------------------------\n");
  printf("Start Testing WorldGuard Read unauthorized cache line (check eviction due to wid miss match)...\n");
  init_worldguard();
  wgcore_print_regs();

  read_unauthorized_cacheline2();
  
  return 0;
}