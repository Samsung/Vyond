
#include <common/include/init.h>
#include <common/include/csr.h>
#include <platform/include/platform.h>

void init_worldguard(void)
{
    SET_CSR(WG_CSR_MLWID, 0);
    SET_CSR(WG_CSR_MWIDDELEG, 0x6);
    SET_CSR(WG_CSR_SLWID, 0);
}