#ifndef _WG_CHECKER_H_
#define _WG_CHECKER_H_

#include <stdio.h>
void wgc_print_vendor_reg(uintptr_t base);
void wgc_print_slot_reg(uintptr_t base, int n);
void wgc_print_error_reg(uintptr_t base);
void config_wgc(int n, uint64_t base, uint64_t addr, uint32_t cfg, uint64_t perm, uint64_t lgAlign);

#endif // _WG_CHECKER_H_
