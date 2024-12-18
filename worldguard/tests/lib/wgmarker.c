
#include <common/wgmarker.h>
#include <platform/platform.h>
#include <common/mmio.h>

//--------------------------------------------------------------------------------------------------
// Functions for WGMarker
//--------------------------------------------------------------------------------------------------
void wgm_print_vendor_reg(uintptr_t base) {
  printf("[WGM] VENDOR                   : %#x\n", reg_read32(base + WGM_VENDOR));
  printf("[WGM] IMPID                    : %#x\n", reg_read16(base + WGM_IMPID));
  printf("[WGM] WID                      : %#x\n", reg_read8 (base + WGM_WID));
  printf("[WGM] LOCK_VALID               : %#x\n", reg_read8 (base + WGM_LOCK_VALID));
}

