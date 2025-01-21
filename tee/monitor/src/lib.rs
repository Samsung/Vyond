#![no_std]
#![no_main]

use core::sync::atomic::compiler_fence;
use core::sync::atomic::Ordering;

use once_cell::OnceCell;

#[cfg(not(feature = "semihosting"))]
use crate::api::sbi_printf;
#[cfg(not(feature = "semihosting"))]
use core::ffi::c_char;
#[cfg(feature = "semihosting")]
use semihosting::{heprintln, hprintln};

#[macro_use]
pub mod cpu;

pub mod api;
pub mod enclave;
pub mod encoding;
pub mod once_cell;
pub mod panic;
pub mod pmp;
pub mod spinlock;
pub mod thread;
pub mod trap;
pub mod wg;
use crate::wg::*;

pub const SMM_BASE: usize = 0x80000000;
pub const SMM_SIZE: usize = 0x200000;

#[derive(Debug)]
pub enum Error {
    Invalid,
    InvalidId,
    NotPageGranularity,
    NotAligned,
    MaxReached,
    Overlap,
    ImpossibleTor,
    Interrupted,
    PmpFailure,
    NotRunnable,
    NotDestroyable,
    RegionOverlaps,
    NotAccessible,
    IllegalArgument,
    NotRunning,
    NotResumable,
    NotInitialized,
    NoFreeResource,
    NotFresh,
    NotImplemented,
    NotSupported,
    Unknown,
}

unsafe impl<T> Sync for OnceCell<T> {}

pub const PAGE_SIZE: usize = 4096;

static SM_INIT_DONE: OnceCell<bool> = OnceCell::new();
static SM_REGION_ID: OnceCell<usize> = OnceCell::new();
static OS_REGION_ID: OnceCell<usize> = OnceCell::new();

pub fn os_region_id() -> usize {
    *OS_REGION_ID.get().unwrap()
}

pub fn sm_region_id() -> usize {
    *SM_REGION_ID.get().unwrap()
}

fn sm_init_done() {
    SM_INIT_DONE.set(true);
}

fn sm_wait_for_completion() {
    while !SM_INIT_DONE.get().unwrap() {
        compiler_fence(Ordering::Release);
    }
}

pub fn smm_init<'a>() -> Result<usize, Error> {
    pmp::pmp_region_init(SMM_BASE, SMM_SIZE, pmp::Priority::Top, false)
}

pub fn osm_init<'a>() -> Result<usize, Error> {
    pmp::pmp_region_init(0, usize::MAX, pmp::Priority::Bottom, true)
}

#[no_mangle]
pub extern "C" fn sm_init(cold_boot: bool) -> isize {
    let hartid = csr_read!(mhartid);

    #[cfg(feature = "semihosting")]
    {
        hprintln!("Initializing ... hart {:#x}\n", hartid);
    }
    #[cfg(not(feature = "semihosting"))]
    {
        let format = b"Initializing ... hart %d\n\0".as_ptr().cast::<c_char>();
        unsafe {
            sbi_printf(format, hartid);
        }
    }

    // initialize SMM
    if cold_boot {
        if let Ok(region) = smm_init() {
            SM_REGION_ID.set(region);
        } else {
            #[cfg(feature = "semihosting")]
            {
                heprintln!("Intolerable error - failed to initialize SM memory");
            }
            #[cfg(not(feature = "semihosting"))]
            {
                let format = b"Intolerable error - failed to initialize SM memory 1\n\0"
                    .as_ptr()
                    .cast::<c_char>();
                unsafe {
                    sbi_printf(format);
                }
            }
            return -1;
        }

        if let Ok(region) = osm_init() {
            OS_REGION_ID.set(region);
        } else {
            #[cfg(feature = "semihosting")]
            {
                heprintln!("Inrolerable error - failed to initialize OS memory");
            }
            #[cfg(not(feature = "semihosting"))]
            {
                let format = b"Intolerable error - failed to initialize SM memory 2\n\0"
                    .as_ptr()
                    .cast::<c_char>();
                unsafe {
                    sbi_printf(format);
                }
            }
            return -1;
        }

        sm_init_done();

        compiler_fence(Ordering::Release);
    }

    /* wait until cold-boot hart finishes */
    sm_wait_for_completion();

    /* below are executed by all harts */
    pmp::reset(pmp::PMP_N_REG);

    let _ = pmp::set_keystone(sm_region_id(), pmp::PMP_NO_PERM);
    let _ = pmp::set_keystone(os_region_id(), pmp::PMP_ALL_PERM);

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
        .set_slot_addr(1, ((SMM_BASE | (SMM_SIZE / 2 - 1)) >> 2) as u64);
    wgcheckers.dram.set_slot_perm(1, 0xc0); // RW for w3 only
                                            //
    wgcheckers.dram.set_slot_cfg(
        2,
        WGC_CFG_ER | WGC_CFG_EW | WGC_CFG_IR | WGC_CFG_IW | WGC_CFG_A_TOR,
    ); //TOR, report all
    wgcheckers
        .dram
        .set_slot_addr(2, (SMM_BASE + SMM_SIZE >> 2) as u64); //
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

    #[cfg(feature = "semihosting")]
    {
        hprintln!(
            "Vyond security monitor has been initialized on hart-#{:#x}!\n",
            hartid
        );
    }
    #[cfg(not(feature = "semihosting"))]
    {
        let format = b"Vyond security monitor has been initialized on hart %d\n\0"
            .as_ptr()
            .cast::<c_char>();
        unsafe {
            sbi_printf(format);
        }
    }

    0
}
