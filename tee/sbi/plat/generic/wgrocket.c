/*
 * SPDX-License-Identifier: BSD-2-Clause
 *
 * Copyright (c) 2020 Western Digital Corporation or its affiliates.
 *
 * Authors:
 *   Anup Patel <anup.patel@wdc.com>
 */

#include <platform_override.h>
#include <sbi_utils/fdt/fdt_helper.h>
#include <sbi_utils/fdt/fdt_fixup.h>

//static u64 wgrocket_tlbr_flush_limit(const struct fdt_match *match)
//{
//	/*
//	 * The sfence.vma by virtual address does not work on
//	 * SiFive FU540 so we return remote TLB flush limit as zero.
//	 */
//	return 0;
//}
//
//static int wgrocket_fdt_fixup(void *fdt, const struct fdt_match *match)
//{
//	/*
//	 * SiFive Freedom U540 has an erratum that prevents S-mode software
//	 * to access a PMP protected region using 1GB page table mapping, so
//	 * always add the no-map attribute on this platform.
//	 */
//	fdt_reserved_memory_nomap_fixup(fdt);
//
//	return 0;
//}

static const struct fdt_match wgrocket_match[] = {
	{ .compatible = "usb-bar,chipyard-dev" },
};

const struct platform_override wgrocket = {
	.match_table = wgrocket_match,
	//.tlbr_flush_limit = wgrocket_tlbr_flush_limit,
	//.fdt_fixup = wgrocket_fdt_fixup,
};
