
/**
* This test checks if WorldGuard implementation in cache hierarchy evict the cache line 
* if wid in metadata of matched cache line and wid in the request are different.
* In the test, it refille the lines with wid 3 then tries to access them with other wids without permissions.
*
* Author: Sungkeun Kim (sk84.kim@samsung.com)
*/
#include <stdio.h>
#include <riscv-pk/encoding.h>

#include <common/csr.h>
#include <common/init.h>
#include <common/wgcore.h>
#include <common/wgmarker.h>
#include <common/wgchecker.h>
#include <platform/platform.h>


void fill_cacheline1(uint8_t* parr)
{
  write_csr(0x391, 3);
  for (int cl = 0; cl < 4; cl++)
      for (int i = 0; i < SZ_CL; i++)
        *(parr + SZ_CL * cl + i) = i + 1;
}

void read_unauthorized_cacheline1()
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
  

  for (int cl = 0; cl < 4; cl++) {
    fill_cacheline1(parr);
    for (int wid = 0; wid < 3; wid++) {
      write_csr(0x391, wid);
      printf("[wid%d][line%d] read lines\n", wid, cl);
      for (int i = 0; i < SZ_CL; i++) printf("%d ", *(parr + SZ_CL * cl + i));
      printf("\n");
    }
  }
}
