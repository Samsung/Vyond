#![no_std]
#![no_main]

use core::sync::atomic::compiler_fence;
use core::sync::atomic::Ordering;

use once_cell::OnceCell;

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

    hprintln!("Initializing ... hart {:#x}\n", hartid);

    // initialize SMM
    if cold_boot {
        if let Ok(region) = smm_init() {
            SM_REGION_ID.set(region);
        } else {
            heprintln!("Intolerable error - failed to initialize SM memory");
            return -1;
        }

        if let Ok(region) = osm_init() {
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

    /* below are executed by all harts */
    pmp::reset(pmp::PMP_N_REG);

    let _ = pmp::set_keystone(sm_region_id(), pmp::PMP_NO_PERM);
    let _ = pmp::set_keystone(os_region_id(), pmp::PMP_ALL_PERM);

    hprintln!(
        "Vyond security monitor has been initialized on hart-#{:#x}!\n",
        hartid
    );

    0
}
