/**
 * This case shows the unintended overwriting to stackframe 
 * if stack variables to be protected and other variables such as return address are allocated in the same cacheblock.
 * Specifically, with WGChecker for memory hierarchy configured not to raise interrupt or bus error, 
 * if protected data is accessed with unauthorized wid, the entire cacheline would be refilled with zer,
 * which ends up with zeroing other variables in the same cacheline such as return address.
 * WorldGuard does not define how cache controller handles zero data due to unauthorized data.
 * Therefore, we strongly recommend for users to manage the data to be procted with cacheline granularity.
 *
 * Author: Sungkeun Kim (sk84.kim@samsung.com)
 */

#include <stdio.h>
#include <riscv-pk/encoding.h>

#include <common/include/csr.h>
#include <common/include/mmio.h>
#include <common/include/init.h>
#include <common/include/wgcore.h>
#include <common/include/wgmarker.h>
#include <common/include/wgchecker.h>
#include <platform/include/platform.h>

void overwrite_stackframe()
{
  uint8_t val;
  write_csr(0x391, 3);

  //----------------------------------------------------------------------------
  // No. | Addr           | CFG   | PERM   | Description
  //----------------------------------------------------------------------------
  // 0   | 0x8000.0000    | 0x000 | 0xff   | 
  // 1   | &val & ~mask   | 0x301 | 0xff   | cacheline addr of &val
  // 2   | (&val&~mask)+64| 0x001 | 0xf3   | 
  // 3   | 9000.0000      | 0x301 | 0xff   | 
  //----------------------------------------------------------------------------
  // CFG0
  reg_write64(WGC_MEMORY_BASE + SLOT_N_ADDRESS(0), 0x80000000 >> 6);
  reg_write64(WGC_MEMORY_BASE + SLOT_N_CFG    (0), 0x000);
  reg_write64(WGC_MEMORY_BASE + SLOT_N_PERM   (0), 0xff);
  // CFG1
  uintptr_t mask = 0x3f;
  uintptr_t addr = (uintptr_t)&val & ~mask;
  uintptr_t addr2 = addr + 64;
  reg_write64(WGC_MEMORY_BASE + SLOT_N_ADDRESS(1), addr >> 6);
  reg_write64(WGC_MEMORY_BASE + SLOT_N_CFG    (1), 0x301);
  reg_write64(WGC_MEMORY_BASE + SLOT_N_PERM   (1), 0xff);
  // CFG2
  reg_write64(WGC_MEMORY_BASE + SLOT_N_ADDRESS(2), (addr2) >> 6);
  //reg_write64(WGC_MEMORY_BASE + SLOT_N_ADDRESS(2), (uintptr_t)(&val + 1) >> 6);
  reg_write64(WGC_MEMORY_BASE + SLOT_N_CFG    (2), 0x001);
  reg_write64(WGC_MEMORY_BASE + SLOT_N_PERM   (2), 0xf3);
  // CFG3
  reg_write64(WGC_MEMORY_BASE + SLOT_N_ADDRESS(3), 0x90000000 >> 6);
  reg_write64(WGC_MEMORY_BASE + SLOT_N_CFG    (3), 0x301);
  reg_write64(WGC_MEMORY_BASE + SLOT_N_PERM   (3), 0xff);

  //printf("---------------------------------------------\n");
  //printf("After configure for WG_CHECKER\n");
  //wgc_print_slot_reg(WGC_MEMORY_BASE, 0);
  //wgc_print_slot_reg(WGC_MEMORY_BASE, 1);
  //wgc_print_slot_reg(WGC_MEMORY_BASE, 2);
  //wgc_print_slot_reg(WGC_MEMORY_BASE, 3);

  printf("&val = %p aligned addr: %#lx &val + 1 = %#lx\n", &val, addr, addr2);
  val = 3;
  printf("[wid%ld] val = %d\n", read_csr(0x391), val);

  write_csr(0x391, 1);   // <-- overwrite variables on the same cacheline (potentially return address) with zero values!!!
  printf("[wid%ld] val = %d\n", read_csr(0x391), val);
  // won't be able to return to caller if the return address is zeroed-out.
}


int main()
{
  printf("---------------------------------------------\n");
  printf("WorldGuard Test - overwrite stackframe\n");
  init_worldguard();
  //wgcore_print_regs();

  overwrite_stackframe();
  
  return 0;
}
