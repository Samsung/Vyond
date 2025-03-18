#![no_std]
#![no_main]

use core::sync::atomic::compiler_fence;
use core::sync::atomic::Ordering;

use once_cell::OnceCell;

#[cfg(feature = "semihosting")]
use semihosting::{heprintln, hprintln};

#[macro_use]
pub mod cpu;

pub mod api;
pub mod enclave;
pub mod encoding;
pub mod isolator;
pub mod once_cell;
pub mod panic;
pub mod pmp;
pub mod spinlock;
pub mod thread;
pub mod trap;
pub mod wg;

pub const SMM_BASE: usize = 0x80000000;
pub const SMM_SIZE: usize = 0x200000;

#[derive(Debug)]
pub enum Error {
    Success = 0,
    Unknown = 100000,
    InvalidId,
    Interrupted,
    PmpFailure,
    NotRunnable,
    NotDestroyable,
    RegionOverlaps,
    NotAccessible,
    IllegalArgument,
    NotRunning,
    NotResumable,
    EdgeCallHost,
    NotInitialized,
    NoFreeResource,
    SBIProhibited,
    IllegalPTE,
    NotFresh,
    RegionSizeInvalid = 10020,
    NotPageGranularity,
    NotAligned,
    MaxReached,
    RegionInvalid,
    RegionOverlap,
    ImpossibleTor,
    Deprecated = 100099,
    NotImplemented,
    // Above enum's are identical to sm_err.h in sdk
    Overlap,
    NotSupported,
    Invalid,
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

#[no_mangle]
pub extern "C" fn sm_init(cold_boot: bool) -> isize {
    let hartid = csr_read!(mhartid);
    hprintln!("Initializing ... hart {:#x}\n", hartid);

    // initialize SMM
    if cold_boot {
        if let Ok(region) = isolator::smm_init() {
            SM_REGION_ID.set(region);
        } else {
            heprintln!("Intolerable error - failed to initialize SM memory");
            return -1;
        }

        if let Ok(region) = isolator::osm_init() {
            OS_REGION_ID.set(region);
        } else {
            heprintln!("Inrolerable error - failed to initialize OS memory");
            return -1;
        }

        sm_init_done();
        compiler_fence(Ordering::Release);
    }

    /* wait until cold-boot hart finishes */
    sm_wait_for_completion();

    let _ = isolator::update(sm_region_id());
    let _ = isolator::update(os_region_id());
    isolator::display_isolator();

    hprintln!(
        "Vyond security monitor has been initialized on hart-#{:#x}!\n",
        hartid
    );

    0
}
