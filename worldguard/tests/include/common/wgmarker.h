
#ifndef _WG_MARKER_H_
#define _WG_MARKER_H_

#include <stdio.h>
void wgm_print_vendor_reg(uintptr_t base);
void wgm_set_wid(uintptr_t base, uint8_t wid);
uint8_t wgm_get_wid(uintptr_t base);

#endif // _WG_MARKERE_H_
