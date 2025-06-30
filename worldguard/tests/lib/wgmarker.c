
#include <common/wgmarker.h>
#include <platform/platform.h>
#include <common/mmio.h>
#ifdef BARE_METAL
#include "kprintf.h"
#endif

//--------------------------------------------------------------------------------------------------
// Functions for WGMarker
//--------------------------------------------------------------------------------------------------
void wgm_print_vendor_reg(uintptr_t base) {
#ifdef BARE_METAL
  kprintf("[WGM] VENDOR                   : 0x%lx\n", reg_read32(base + WGM_VENDOR));
  kprintf("[WGM] IMPID                    : 0x%lx\n", reg_read16(base + WGM_IMPID));
  kprintf("[WGM] WID                      : 0x%lx\n", reg_read8 (base + WGM_WID));
  kprintf("[WGM] LOCK_VALID               : 0x%lx\n", reg_read8 (base + WGM_LOCK_VALID));
#else
  printf("[WGM] VENDOR                   : %#x\n", reg_read32(base + WGM_VENDOR));
  printf("[WGM] IMPID                    : %#x\n", reg_read16(base + WGM_IMPID));
  printf("[WGM] WID                      : %#x\n", reg_read8 (base + WGM_WID));
  printf("[WGM] LOCK_VALID               : %#x\n", reg_read8 (base + WGM_LOCK_VALID));
#endif
}

void wgm_set_wid(uintptr_t base, uint8_t wid) {
  reg_write8(base + WGM_WID, wid);
}

uint8_t wgm_get_wid(uintptr_t base) {
  return reg_read8(base + WGM_WID);
}
