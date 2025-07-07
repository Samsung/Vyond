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
pub mod isolator;
pub mod once_cell;
pub mod panic;
#[cfg(any(feature = "isolator_pmp", feature = "isolator_hybrid"))]
pub mod pmp;
pub mod spinlock;
pub mod thread;
pub mod trap;
#[cfg(any(feature = "isolator_wg", feature = "isolator_hybrid"))]
pub mod wg;


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

#[no_mangle]
pub extern "C" fn sm_init(cold_boot: bool) -> isize {
    let hartid = csr_read!(mhartid);
    hprintln!("Initializing ... hart {:#x}\n", hartid);

    // initialize SMM
    if cold_boot {
        if let Err(e) = isolator::smm_init() {
            heprintln!("Intolerable error - failed to initialize SM memory: {:?}", e);
            return -1;
        }

        if let Err(e) = isolator::osm_init() {
            heprintln!("Inrolerable error - failed to initialize OS memory: {:?}", e);
            return -1;
        }

        isolator::sm_init_done();

        isolator::display_isolator();
        compiler_fence(Ordering::Release);
    }

    /* wait until cold-boot hart finishes */
    isolator::sm_wait_for_completion();

    /* below are executed by all harts */
    if let Err(e) = isolator::update() {
        heprintln!("Intolerable error - failed update isolator: {:?}", e);
        return -1;
    }
    isolator::display_isolator();

    hprintln!(
        "Vyond security monitor has been initialized on hart-#{:#x}!\n",
        hartid
    );

    0
}
