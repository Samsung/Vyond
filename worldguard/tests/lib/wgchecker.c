
#include <stdio.h>
#include <common/mmio.h>
#include <common/wgchecker.h>
#include <platform/platform.h>

#ifdef BARE_METAL
#include "kprintf.h"
#endif


//--------------------------------------------------------------------------------------------------
// Functions for WGChecker
//--------------------------------------------------------------------------------------------------
void wgc_print_vendor_reg(uintptr_t base) {
#ifdef BARE_METAL
  kprintf("[WGC] VENDOR                   : 0x%x\n", reg_read32(base + WGC_VENDOR));
  kprintf("[WGC] IMPID                    : 0x%x\n", reg_read16(base + WGC_IMPID));
  kprintf("[WGC] NSLOTS                   : 0x%x\n", reg_read32(base + WGC_NSLOTS));
#else
  printf("[WGC] VENDOR                   : %#x\n", reg_read32(base + WGC_VENDOR));
  printf("[WGC] IMPID                    : %#x\n", reg_read16(base + WGC_IMPID));
  printf("[WGC] NSLOTS                   : %#x\n", reg_read32(base + WGC_NSLOTS));
#endif
}

void wgc_print_slot_reg(uintptr_t base, int n) {
#ifdef BARE_METAL
  kprintf("[WGC][Slot-%d] ADDRESS         : 0x%lx\n", n, reg_read64(base + SLOT_N_ADDRESS(n)));
  kprintf("[WGC][Slot-%d] PERM            : 0x%lx\n", n, reg_read64(base + SLOT_N_PERM(n)));
  kprintf("[WGC][Slot-%d] CONFIG          : 0x%x\n", n, reg_read32(base + SLOT_N_CFG(n)));
#else
  printf("[WGC][Slot-%d] ADDRESS         : %#lx\n", n, reg_read64(base + SLOT_N_ADDRESS(n)));
  printf("[WGC][Slot-%d] PERM            : %#lx\n", n, reg_read64(base + SLOT_N_PERM(n)));
  printf("[WGC][Slot-%d] CONFIG          : %#x\n", n, reg_read32(base + SLOT_N_CFG(n)));
#endif
}

void wgc_print_error_reg(uintptr_t base) {
#ifdef BARE_METAL
  kprintf("[WGC] ERROR_CAUSE_SLOT_WID_RW  : 0x%x\n", reg_read16(base + WGC_ERROR_CAUSE_SLOT_WID_RW));
  kprintf("[WGC] ERROR_CAUSE_SLOT_BE_IP   : 0x%x\n", reg_read32(base + WGC_ERROR_CAUSE_SLOT_BE_IP));
  kprintf("[WGC] ERROR_ADDRESS            : 0x%x\n", reg_read32(base + WGC_ERROR_ADDR));
#else
  printf("[WGC] ERROR_CAUSE_SLOT_WID_RW  : %#x\n", reg_read16(base + WGC_ERROR_CAUSE_SLOT_WID_RW));
  printf("[WGC] ERROR_CAUSE_SLOT_BE_IP   : %#x\n", reg_read32(base + WGC_ERROR_CAUSE_SLOT_BE_IP));
  printf("[WGC] ERROR_ADDRESS            : %#x\n", reg_read32(base + WGC_ERROR_ADDR));
#endif
}

void config_wgc(int n, uint64_t base, uint64_t addr, uint32_t cfg, uint64_t perm, uint64_t lgAlign) {
  reg_write64(base + SLOT_N_ADDRESS(n), addr >> lgAlign);
  reg_write64(base + SLOT_N_PERM(n), perm);
  reg_write64(base + SLOT_N_CFG(n), cfg);
}
