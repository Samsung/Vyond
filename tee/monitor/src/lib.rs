#![no_std]
#![no_main]

use core::sync::atomic::compiler_fence;
use core::sync::atomic::Ordering;

use once_cell::OnceCell;

#[cfg(feature = "semihosting")] 
use semihosting::{heprintln, hprintln};
#[cfg(not(feature = "semihosting"))]
use core::ffi::c_char;
#[cfg(not(feature = "semihosting"))]
use crate::api::sbi_printf;

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

    #[cfg(feature = "semihosting")] {
        hprintln!("Initializing ... hart {:#x}\n", hartid);
    }
    #[cfg(not(feature = "semihosting"))] {
        let format = b"Initializing ... hart %d\n\0".as_ptr().cast::<c_char>();
        unsafe { sbi_printf(format, hartid); }
    }

    // initialize SMM
    if cold_boot {
        if let Ok(region) = smm_init() {
            SM_REGION_ID.set(region);
        } else {
            #[cfg(feature = "semihosting")] {
                heprintln!("Intolerable error - failed to initialize SM memory");
            }
            #[cfg(not(feature = "semihosting"))] {
                let format = b"Intolerable error - failed to initialize SM memory 1\n\0".as_ptr().cast::<c_char>();
                unsafe { sbi_printf(format); }
            }
            return -1;
        }

        if let Ok(region) = osm_init() {
            OS_REGION_ID.set(region);
        } else {
            #[cfg(feature = "semihosting")] {
                heprintln!("Inrolerable error - failed to initialize OS memory");
            }
            #[cfg(not(feature = "semihosting"))] {
                let format = b"Intolerable error - failed to initialize SM memory 2\n\0".as_ptr().cast::<c_char>();
                unsafe { sbi_printf(format); }
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

    #[cfg(feature = "semihosting")] {
        hprintln!(
            "Vyond security monitor has been initialized on hart-#{:#x}!\n",
            hartid
        );
    }
    #[cfg(not(feature = "semihosting"))] {
        let format = b"Vyond security monitor has been initialized on hart %d\n\0".as_ptr().cast::<c_char>();
        unsafe { sbi_printf(format); }
    }

    0
}
