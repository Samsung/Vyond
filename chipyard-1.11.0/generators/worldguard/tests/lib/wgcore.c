#include <stdio.h>
#include <riscv-pk/encoding.h>

#include <common/csr.h>
#include <common/init.h>
#include <common/wgcore.h>
#include <common/wgmarker.h>
#include <common/wgchecker.h>
#include <platform/platform.h>



//--------------------------------------------------------------------------------------------------
// Functions for WGRocket
//--------------------------------------------------------------------------------------------------
void wgcore_init_regs() {
  SET_CSR(WG_CSR_MLWID, 0);
  SET_CSR(WG_CSR_MWIDDELEG, 0x6);
  SET_CSR(WG_CSR_SLWID, 0);
}

void wgcore_print_regs() {
  uintptr_t mlwid, slwid, mwiddeleg;
  GET_CSR(WG_CSR_MLWID, mlwid);
  GET_CSR(WG_CSR_SLWID, slwid);
  GET_CSR(WG_CSR_MWIDDELEG, mwiddeleg);
  printf("[WGCore] MLWID                  : %#lx\n", mlwid);
  printf("[WGCore] MWIDDELEG              : %#lx\n", mwiddeleg);
  printf("[WGCore] SLWID                  : %#lx\n", slwid);
}
