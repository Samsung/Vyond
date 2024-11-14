
#include <stdio.h>
#include <common/include/mmio.h>
#include <common/include/wgchecker.h>
#include <platform/include/platform.h>


//--------------------------------------------------------------------------------------------------
// Functions for WGChecker
//--------------------------------------------------------------------------------------------------
void wgc_print_vendor_reg(uintptr_t base) {
  printf("[WGC] VENDOR                   : %#x\n", reg_read32(base + WGC_VENDOR));
  printf("[WGC] IMPID                    : %#x\n", reg_read16(base + WGC_IMPID));
  printf("[WGC] NSLOTS                   : %#x\n", reg_read32(base + WGC_NSLOTS));
}

void wgc_print_slot_reg(uintptr_t base, int n) {
  printf("[WGC][Slot-%d] ADDRESS         : %#lx\n", n, reg_read64(base + SLOT_N_ADDRESS(n)));
  printf("[WGC][Slot-%d] PERM            : %#lx\n", n, reg_read64(base + SLOT_N_PERM(n)));
  printf("[WGC][Slot-%d] CONFIG          : %#x\n", n, reg_read32(base + SLOT_N_CFG(n)));
}

void wgc_print_error_reg(uintptr_t base) {
  printf("[WGC] ERROR_CAUSE_SLOT_WID_RW  : %#x\n", reg_read16(base + WGC_ERROR_CAUSE_SLOT_WID_RW));
  printf("[WGC] ERROR_CAUSE_SLOT_BE_IP   : %#x\n", reg_read32(base + WGC_ERROR_CAUSE_SLOT_BE_IP));
  printf("[WGC] ERROR_ADDRESS            : %#x\n", reg_read32(base + WGC_ERROR_ADDR));
}

void config_wgc(int n, uint64_t base, uint64_t addr, uint32_t cfg, uint64_t perm, uint64_t lgAlign) {
  reg_write64(base + SLOT_N_ADDRESS(n), addr >> lgAlign);
  reg_write64(base + SLOT_N_PERM(n), perm);
  reg_write64(base + SLOT_N_CFG(n), cfg);
}
