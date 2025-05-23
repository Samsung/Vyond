/*
 * SPDX-License-Identifier: BSD-2-Clause
 *
 * Copyright (c) 2019 Western Digital Corporation or its affiliates.
 *
 * Authors:
 *   Anup Patel <anup.patel@wdc.com>
 */

#include "fw_base.S"

	.section .entry, "ax", %progbits
	.align 3
	.global fw_boot_hart
	/*
	 * This function is called very early even before
	 * fw_save_info() is called.
	 * We can only use a0, a1, and a2 registers here.
	 * The boot HART id should be returned in 'a0'.
	 */
fw_boot_hart:
	li	a0, -1
	ret

	.section .entry, "ax", %progbits
	.align 3
	.global fw_save_info
	/*
	 * We can only use a0, a1, a2, a3, and a4 registers here.
	 * The a0, a1, and a2 registers will be same as passed by
	 * previous booting stage.
	 * Nothing to be returned here.
	 */
fw_save_info:
	ret

	.section .entry, "ax", %progbits
	.align 3
	.global fw_next_arg1
	/*
	 * We can only use a0, a1, and a2 registers here.
	 * The a0, a1, and a2 registers will be same as passed by
	 * previous booting stage.
	 * The next arg1 should be returned in 'a0'.
	 */
fw_next_arg1:
#ifdef FW_PAYLOAD_FDT_ADDR
	li	a0, FW_PAYLOAD_FDT_ADDR
#else
	add	a0, a1, zero
#endif
	ret

	.section .entry, "ax", %progbits
	.align 3
	.global fw_next_addr
	/*
	 * We can only use a0, a1, and a2 registers here.
	 * The next address should be returned in 'a0'.
	 */
fw_next_addr:
	lla	a0, payload_bin
	ret

	.section .entry, "ax", %progbits
	.align 3
	.global fw_next_mode
	/*
	 * We can only use a0, a1, and a2 registers here.
	 * The next address should be returned in 'a0'.
	 */
fw_next_mode:
	li	a0, PRV_S
	ret

	.section .entry, "ax", %progbits
	.align 3
	.global fw_options
	/*
	 * We can only use a0, a1, and a2 registers here.
	 * The 'a4' register will have default options.
	 * The next address should be returned in 'a0'.
	 */
fw_options:
	add	a0, zero, zero
	ret

	.section .payload, "ax", %progbits
	.align 4
	.globl payload_bin
payload_bin:
# Disable sm test code
#	li  a7, 0x08424b45
#	li  a6, 2001 /* create enclave */
#	li  a0, 0x12345000 /* [TBD] Partition base */
#	li  a1, 0x07654000 /* [TBD] Partition size */
#	li  a2, 0x80400010 /* Enclave entry */
#	ecall
#
#start_secos:
#	li  a7, 0x08424b45
#	li  a6, 2003
#	li  a0, 0x0        /* a0 : hartid */
#	li  a1, 0x82200000 /* a1 : dtb addr */
#	ecall
#
#start_linux:
#	li  a0, 0x0        /* a0 : hartid */
#	li  a1, 0x82200000 /* a1 : dtb addr */
#	li  ra, 0x84200000 /* ra : Linux kernel entry */ 
#	ret
#
#1:
#    j   1b
#ifndef FW_PAYLOAD_PATH
	wfi
	j	payload_bin
#else
	.incbin	FW_PAYLOAD_PATH
#endif
