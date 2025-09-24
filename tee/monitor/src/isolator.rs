use crate::enclave;
#[cfg(any(feature = "isolator_pmp", feature = "isolator_hybrid"))]
use crate::pmp;
#[cfg(any(feature = "isolator_wg", feature = "isolator_hybrid"))]
use crate::wg;
use crate::{Error, OnceCell};
use core::sync::atomic::compiler_fence;
use core::sync::atomic::Ordering;
use semihosting::{heprintln, hprintln};

pub const SMM_BASE: usize = 0x80000000;
pub const SMM_SIZE: usize = 0x200000;
pub const OSM_BASE: usize = SMM_BASE + SMM_SIZE;

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

pub fn sm_init_done() {
    SM_INIT_DONE.set(true);
}

pub fn sm_wait_for_completion() {
    while !SM_INIT_DONE.get().unwrap() {
        compiler_fence(Ordering::Release);
    }
}

pub fn smm_init<'a>() -> Result<(), Error> {
    #[cfg(feature = "isolator_pmp")]
    {
        let region = pmp::region_init(SMM_BASE, SMM_SIZE, pmp::Priority::Top, false)?;
        SM_REGION_ID.set(region);
        Ok(())
    }
    #[cfg(feature = "isolator_wg")]
    {
        csr_write_custom!(0x390, 2); // Set mlwid

        let flash = wg::WGChecker::new(wg::WGC_FLASH_BASE);
        flash.set_slot_perm(flash.get_nslots() as usize, 0xff);
        let uart = wg::WGChecker::new(wg::WGC_UART_BASE);
        uart.set_slot_perm(uart.get_nslots() as usize, 0xff);

        let region = wg::region_init(SMM_BASE, SMM_SIZE, 3 << (wg::TRUSTED_WID * 2), false)?;
        wg::set_wg(region)?;
        SM_REGION_ID.set(region);

        Ok(())
    }
    #[cfg(feature = "isolator_hybrid")]
    {
        csr_write_custom!(0x390, 2); // Set mlwid

        let flash = wg::WGChecker::new(wg::WGC_FLASH_BASE);
        flash.set_slot_perm(flash.get_nslots() as usize, 0xff);
        let uart = wg::WGChecker::new(wg::WGC_UART_BASE);
        uart.set_slot_perm(uart.get_nslots() as usize, 0xff);

        let region = wg::region_init(SMM_BASE, SMM_SIZE, 3 << (wg::TRUSTED_WID * 2), false)?;
        wg::set_wg(region)?;

        Ok(())
    }
}

pub fn osm_init<'a>() -> Result<(), Error> {
    #[cfg(feature = "isolator_pmp")]
    {
        let region = pmp::region_init(0, usize::MAX, pmp::Priority::Bottom, true)?;
        OS_REGION_ID.set(region);
        Ok(())
    }

    #[cfg(feature = "isolator_wg")]
    {
        // This region will be accessed by both OS and unprotected user processes.
        let region = wg::region_init(
            SMM_BASE + SMM_SIZE,
            usize::MAX,
            (3 << (wg::OS_WID * 2)) | 3,
            false,
        )?;
        wg::set_wg(region)?;
        OS_REGION_ID.set(region);
        Ok(())
    }
    #[cfg(feature = "isolator_hybrid")]
    {
        let region = wg::region_init(OSM_BASE, usize::MAX, 3 << (wg::OS_WID * 2) | 3, false)?;
        wg::set_wg(region)?;

        let region = pmp::region_init(0, usize::MAX, pmp::Priority::Bottom, true)?;
        OS_REGION_ID.set(region);
        Ok(())
    }
}

pub fn region_init(start: usize, size: usize, eid: usize, shared: bool) -> Result<usize, Error> {
    #[cfg(feature = "isolator_pmp")]
    {
        if shared {
            pmp::region_init(start, size, pmp::Priority::Bottom, false)
        } else {
            pmp::region_init(start, size, pmp::Priority::Any, false)
        }
    }
    #[cfg(feature = "isolator_wg")]
    {
        let region_idx = wg::region_init(start, size, 3 << (eid * 2), true)?;
        wg::set_wg(region_idx)?;
        Ok(region_idx)
    }
    #[cfg(feature = "isolator_hybrid")]
    {
        if shared {
            pmp::region_init(start, size, pmp::Priority::Bottom, false)
        } else {
            pmp::region_init(start, size, pmp::Priority::Any, false)
        }
    }
}

pub fn set_isolator(region_idx: usize) -> Result<(), Error> {
    #[cfg(feature = "isolator_pmp")]
    {
        pmp::set_keystone(region_idx, pmp::PMP_ALL_PERM)
    }
    #[cfg(feature = "isolator_wg")]
    {
        Ok(())
        //wg::set_wg(region_idx)
    }
    #[cfg(feature = "isolator_hybrid")]
    {
        pmp::set_keystone(region_idx, pmp::PMP_ALL_PERM)
    }
}

// pub fn set_isolator(region_idx: usize, perm_conf: PermConfig) Result<(), Error> {
//     #[cfg(feature = "isolator_pmp")]
//     {
//         pmp::set_keystone(region_idx, pmp::PMP_ALL_PERM)
//     }
//     #[cfg(feature = "isolator_wg")]
//     {
//         Ok(())
//         //wg::set_wg(region_idx)
//     }
//     #[cfg(feature = "isolator_hybrid")]
//     {
//         pmp::set_keystone(region_idx, pmp::PMP_ALL_PERM)
//     }
// }

pub fn reset_isolator(region_idx: usize) -> Result<(), Error> {
    #[cfg(feature = "isolator_pmp")]
    {
        pmp::set_keystone(region_idx, pmp::PMP_NO_PERM)
    }
    #[cfg(feature = "isolator_wg")]
    {
        Ok(())
        //wg::set_wg(region_idx)
    }
    #[cfg(feature = "isolator_hybrid")]
    {
        pmp::set_keystone(region_idx, pmp::PMP_NO_PERM)
    }
}

pub fn region_free(region_idx: usize) -> Result<(), Error> {
    #[cfg(feature = "isolator_pmp")]
    {
        pmp::region_free(region_idx)
    }
    #[cfg(feature = "isolator_wg")]
    {
        wg::region_free(region_idx)
    }
    #[cfg(feature = "isolator_hybrid")]
    {
        pmp::region_free(region_idx)
    }
}

pub fn display_isolator() {
    #[cfg(feature = "isolator_pmp")]
    {
        pmp::display()
    }
    #[cfg(feature = "isolator_wg")]
    {
        enclave::display();
        wg::display_regions();
    }
    #[cfg(feature = "isolator_hybrid")]
    {
        pmp::display();
        wg::display()
    }
}

pub fn update() -> Result<(), Error> {
    #[cfg(feature = "isolator_pmp")]
    {
        pmp::reset(pmp::PMP_N_REG);
        let _ = pmp::set_keystone(sm_region_id(), pmp::PMP_NO_PERM);
        let _ = pmp::set_keystone(os_region_id(), pmp::PMP_ALL_PERM);
        Ok(())
    }
    #[cfg(feature = "isolator_wg")]
    {
        Ok(())
    }
    #[cfg(feature = "isolator_hybrid")]
    {
        pmp::reset(pmp::PMP_N_REG);
        pmp::set_keystone(os_region_id(), pmp::PMP_ALL_PERM)?;
        Ok(())
    }
}
