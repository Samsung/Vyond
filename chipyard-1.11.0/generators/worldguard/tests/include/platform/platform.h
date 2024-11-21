#ifndef PLATFORM_H
#define PLATFORM_H

//----------------------------------------------------------
// WorldGuard-Awre Core
//----------------------------------------------------------
#define WG_CSR_MLWID 0x390
#define WG_CSR_SLWID 0x190
#define WG_CSR_MWIDDELEG 0x748

//----------------------------------------------------------
// Cache/Memory Config
//----------------------------------------------------------
#define SZ_CL (0x40)
#define MEMORY_BASE (0x80000000)
#define MEMORY_TOP  (0x90000000)

#define WG_NWORLDS    (4)
#define WG_TRUSTEDWID (3)
#define LOG_ALIGN_CACHE (6)

//----------------------------------------------------------
// WorldGuard Marker
//----------------------------------------------------------
#define WGM_VENDOR                    (0x00000000) 
#define WGM_IMPID                     (0x00000004)
#define WGM_WID                       (0x00000008)
#define WGM_LOCK_VALID                (0x0000000C)

//----------------------------------------------------------
// WorldGuard Checker
//----------------------------------------------------------
#define WGC_VENDOR                    (0x00000000) 
#define WGC_IMPID                     (0x00000004)
#define WGC_NSLOTS                    (0x00000008)
#define WGC_ERROR_CAUSE_SLOT_WID_RW   (0x00000010)
#define WGC_ERROR_CAUSE_SLOT_BE_IP    (0x00000014)
#define WGC_ERROR_ADDR                (0x00000018)
#define WGC_SLOT_BASE                 (0x00000020)

#define SLOT_N_ADDRESS(n) (WGC_SLOT_BASE + 0x20 * (n + 1))
#define SLOT_N_PERM(n)    (WGC_SLOT_BASE + 0x20 * (n + 1) + 0x8)
#define SLOT_N_CFG(n)     (WGC_SLOT_BASE + 0x20 * (n + 1) + 0x10)

#define WG_MARKER_ROCKET_BASE     (0x2100000) 

#define WGP_PERIPHERY_BASE        (0x2020000)
#define WGC_PERIPHERY_BASE        (0x2030000)

#define WGC_PLIC_BASE             (0x2040000)
#define WGC_BOOTROM_BASE          (0x2050000)

#define WGC_MEMORY_BASE       (0x2060000)
#define MAX_WGCHECKERS            (4)

#endif /* PLATFORM_H*/
