#![no_std]
#![no_main]

#[cfg(not(feature = "semihosting"))]
use crate::api::sbi_printf;
#[cfg(not(feature = "semihosting"))]
use core::ffi::c_char;
#[cfg(feature = "semihosting")]
use semihosting::{heprintln, hprintln};

#[macro_use]
pub mod cpu;

pub mod api;
pub mod encoding;
pub mod panic;
pub mod trap;
pub mod wg;

use crate::wg::*;

const SM_BASE: usize = 0x8000_0000;
const SM_SIZE: usize = 0x20_0000;

#[no_mangle]
pub extern "C" fn sm_init(cold_boot: bool) -> isize {
    let hartid = csr_read!(mhartid);
    #[cfg(not(feature = "semihosting"))]
    {
        let format = b"Initializing ... hart %d\n\0".as_ptr().cast::<c_char>();
        unsafe {
            sbi_printf(format, hartid);
        }
    }

    if cold_boot {
        wgcsr_write!(0x390, 2);
        let mlwid = wgcsr_read!(0x390);
        let mwiddeleg = wgcsr_read!(0x748);
        #[cfg(not(feature = "semihosting"))]
        {
            let format = b"initial wgcsrs mlwid: %d mwiddeleg: %#x\n\0"
                .as_ptr()
                .cast::<c_char>();
            unsafe {
                sbi_printf(format, mlwid, mwiddeleg);
            }
        }

        //-----------------------------------------------------
        // Initialize DRAM
        //-----------------------------------------------------
        let mut wgcheckers = WGCheckers::take().unwrap();
        let vendor = wgcheckers.dram.get_vendor();
        let impid = wgcheckers.dram.get_impid();
        let nslots = wgcheckers.dram.get_nslots();
        let errcause = wgcheckers.dram.get_errcause();
        let erraddr = wgcheckers.dram.get_erraddr();

        #[cfg(not(feature = "semihosting"))]
        {
            let format =
                b"[WGC][DRAM] REGs vendor: %d impid: %d nslots: %d errcause: %#x erraddr: %#x\n\0"
                    .as_ptr()
                    .cast::<c_char>();
            unsafe {
                sbi_printf(format, vendor, impid, nslots, errcause, erraddr);
            }
        }

        wgcheckers.dram.set_slot_cfg(
            1,
            WGC_CFG_ER | WGC_CFG_EW | WGC_CFG_IR | WGC_CFG_IW | WGC_CFG_A_NAPOT,
        );
        wgcheckers
            .dram
            .set_slot_addr(1, ((SM_BASE | (SM_SIZE / 2 - 1)) >> 2) as u64);
        wgcheckers.dram.set_slot_perm(1, 0xc0); // RW for w3 only
                                                //
        wgcheckers.dram.set_slot_cfg(
            2,
            WGC_CFG_ER | WGC_CFG_EW | WGC_CFG_IR | WGC_CFG_IW | WGC_CFG_A_TOR,
        ); //TOR, report all
        wgcheckers
            .dram
            .set_slot_addr(2, (SM_BASE + SM_SIZE >> 2) as u64); //
        wgcheckers.dram.set_slot_perm(2, 0xf0); // RW for w2, and w1
        wgcheckers.dram.set_slot_cfg(
            3,
            WGC_CFG_ER | WGC_CFG_EW | WGC_CFG_IR | WGC_CFG_IW | WGC_CFG_A_TOR,
        );
        wgcheckers.dram.set_slot_addr(3, u64::MAX >> 3);
        wgcheckers.dram.set_slot_perm(3, 0xf0); // RW for w2, and w1

        for idx in 0..(nslots + 1) {
            let addr = wgcheckers.dram.get_slot_addr(idx as usize);
            let cfg = wgcheckers.dram.get_slot_cfg(idx as usize);
            let perm = wgcheckers.dram.get_slot_perm(idx as usize);

            #[cfg(not(feature = "semihosting"))]
            {
                let format = b"[WGC][DRAM][Slot-%d] cfg: %#x addr: %#x perm: %#x\n\0"
                    .as_ptr()
                    .cast::<c_char>();
                unsafe {
                    sbi_printf(format, idx as usize, cfg, addr, perm);
                }
            }
        }

        //-----------------------------------------------------
        // Initialize FLASH
        //-----------------------------------------------------
        let vendor = wgcheckers.flash.get_vendor();
        let impid = wgcheckers.flash.get_impid();
        let nslots = wgcheckers.flash.get_nslots();
        let errcause = wgcheckers.flash.get_errcause();
        let erraddr = wgcheckers.flash.get_erraddr();

        #[cfg(not(feature = "semihosting"))]
        {
            let format =
                b"[WGC][FLASH] REGs vendor: %d impid: %d nslots: %d errcause: %#x erraddr: %#x\n\0"
                    .as_ptr()
                    .cast::<c_char>();
            unsafe {
                sbi_printf(format, vendor, impid, nslots, errcause, erraddr);
            }
        }

        wgcheckers.flash.set_slot_perm(nslots as usize, 0xf0); // RW for w2, and w1

        for idx in 0..(nslots + 1) {
            let addr = wgcheckers.flash.get_slot_addr(idx as usize);
            let cfg = wgcheckers.flash.get_slot_cfg(idx as usize);
            let perm = wgcheckers.flash.get_slot_perm(idx as usize);

            #[cfg(not(feature = "semihosting"))]
            {
                let format = b"[WGC][FLASH][Slot-%d] cfg: %#x addr: %#x perm: %#x\n\0"
                    .as_ptr()
                    .cast::<c_char>();
                unsafe {
                    sbi_printf(format, idx as usize, cfg, addr, perm);
                }
            }
        }
        //-----------------------------------------------------
        // Initialize UART
        //-----------------------------------------------------
        let vendor = wgcheckers.uart.get_vendor();
        let impid = wgcheckers.uart.get_impid();
        let nslots = wgcheckers.uart.get_nslots();
        let errcause = wgcheckers.uart.get_errcause();
        let erraddr = wgcheckers.uart.get_erraddr();

        #[cfg(not(feature = "semihosting"))]
        {
            let format =
                b"[WGC][UART] REGs vendor: %d impid: %d nslots: %d errcause: %#x erraddr: %#x\n\0"
                    .as_ptr()
                    .cast::<c_char>();
            unsafe {
                sbi_printf(format, vendor, impid, nslots, errcause, erraddr);
            }
        }

        wgcheckers.uart.set_slot_perm(nslots as usize, 0xf0); // RW for w2, and w1

        for idx in 0..(nslots + 1) {
            let addr = wgcheckers.uart.get_slot_addr(idx as usize);
            let cfg = wgcheckers.uart.get_slot_cfg(idx as usize);
            let perm = wgcheckers.uart.get_slot_perm(idx as usize);

            #[cfg(not(feature = "semihosting"))]
            {
                let format = b"[WGC][UART][Slot-%d] cfg: %#x addr: %#x perm: %#x\n\0"
                    .as_ptr()
                    .cast::<c_char>();
                unsafe {
                    sbi_printf(format, idx as usize, cfg, addr, perm);
                }
            }
        }
    }
    0
}
