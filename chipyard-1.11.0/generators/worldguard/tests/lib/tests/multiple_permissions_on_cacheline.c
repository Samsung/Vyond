/**
 * This test shows that if WGC slots are configured such that 
 * an wid is associated with two memory regions (two array elements in this test) with a different permission
 * on the same cache line, the wid could have access the unauthorized data. 
 * As shown in this test, your memory regions with different permission slist must not be stored in the same cache line.
 * Simple way to achieve this is to align resions with cacheline.
 *
 * Author: Sungkeun Kim (sk84.kim@samsung.com)
*/

#include <stdio.h>
#include <riscv-pk/encoding.h>

#include <common/csr.h>
#include <common/mmio.h>
#include <common/init.h>
#include <common/wgcore.h>
#include <common/wgmarker.h>
#include <common/wgchecker.h>
#include <platform/platform.h>

void multiple_permissions_on_cacheline()
{
  uint32_t arr[2];
  write_csr(0x391, 3);

  //----------------------------------------------------------------------------
  // No. | Addr         | CFG   | PERM   | Description
  //----------------------------------------------------------------------------
  // 0   | 0x8000.0000  | 0x000 | 0xff   | 
  // 1   | &arr[1]      | 0x301 | 0xff   | 
  // 2   | &arr[2]      | 0x301 | 0xf3   | wid1 can't access to arr[1]
  // 3   | 9000.0000    | 0x301 | 0xff   | 
  ////----------------------------------------------------------------------------
  // CFG0
  reg_write64(WGC_MEMORY_BASE + SLOT_N_ADDRESS(0), 0x80000000 >> 6);
  reg_write32(WGC_MEMORY_BASE + SLOT_N_CFG    (0), 0x000);
  reg_write64(WGC_MEMORY_BASE + SLOT_N_PERM   (0), 0xff);
  // CFG1
  reg_write64(WGC_MEMORY_BASE + SLOT_N_ADDRESS(1), (uintptr_t)&arr[1] >> 6);
  reg_write32(WGC_MEMORY_BASE + SLOT_N_CFG    (1), 0x301);
  reg_write64(WGC_MEMORY_BASE + SLOT_N_PERM   (1), 0xff);
  // CFG2
  reg_write64(WGC_MEMORY_BASE + SLOT_N_ADDRESS(2), (uintptr_t)&arr[2] >> 6);
  reg_write32(WGC_MEMORY_BASE + SLOT_N_CFG    (2), 0x301);
  reg_write64(WGC_MEMORY_BASE + SLOT_N_PERM   (2), 0xf3);
  // CFG3
  reg_write64(WGC_MEMORY_BASE + SLOT_N_ADDRESS(3), 0x90000000 >> 6);
  reg_write32(WGC_MEMORY_BASE + SLOT_N_CFG    (3), 0x301);
  reg_write64(WGC_MEMORY_BASE + SLOT_N_PERM   (3), 0xff);

  printf("---------------------------------------------\n");
  printf("After configure for WG_CHECKER\n");
  wgc_print_slot_reg(WGC_MEMORY_BASE, 0);
  wgc_print_slot_reg(WGC_MEMORY_BASE, 1);
  wgc_print_slot_reg(WGC_MEMORY_BASE, 2);
  wgc_print_slot_reg(WGC_MEMORY_BASE, 3);

  printf("arr: %p\n", arr);

  write_csr(0x391, 1);
  arr[0] = 3;
  printf("[wid1] arr[0] = %d\n", arr[0]);

  // wid1 does not have access to arr[1]
  arr[1] = 4;
  printf("[wid1] arr[1] = %d\n", arr[1]);
}
