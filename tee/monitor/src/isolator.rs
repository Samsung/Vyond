use crate::pmp;
use crate::wg::*;
use crate::{os_region_id, sm_region_id};
//use crate::{Error, ENC_BASE, ENC_SIZE, SMM_BASE, SMM_SIZE};
use crate::{Error, SMM_BASE, SMM_SIZE};
use core::sync::atomic::compiler_fence;
use core::sync::atomic::Ordering;
#[cfg(feature = "semihosting")]
use semihosting::hprintln;

pub fn smm_init<'a>() -> Result<usize, Error> {
    #[cfg(feature = "usepmp")]
    return pmp::pmp_region_init(SMM_BASE, SMM_SIZE, pmp::Priority::Top, false);
    #[cfg(feature = "usewg")]
    {
        let mlwid = csr_read_custom!(0x390);
        csr_write_custom!(0x390, 2);
        //csr_write_custom!(0x748, 0xc); // mwiddeleg

        return wg_region_init(SMM_BASE, SMM_SIZE, 3 << (TRUSTED_WID * 2), false);
    }
}

pub fn osm_init<'a>() -> Result<usize, Error> {
    #[cfg(feature = "usepmp")]
    return pmp::pmp_region_init(0, usize::MAX, pmp::Priority::Bottom, true);

    #[cfg(feature = "usewg")]
    {
        unsafe {
            let nslots = WGCHECKERS.flash.get_nslots();
            WGCHECKERS.flash.set_slot_perm(nslots as usize, 0xf0); // RW for w2, and w1
            let nslots = WGCHECKERS.uart.get_nslots();
            WGCHECKERS.uart.set_slot_perm(nslots as usize, 0xf0); // RW for w2, and w1
        }
        // This region will be accessed by both OS and unprotected user processes.
        return wg_region_init(
            //ENC_BASE + ENC_SIZE,
            SMM_BASE + SMM_SIZE,
            usize::MAX,
            (3 << (OS_WID * 2)) | 3,
            false,
        );
    }
}

pub fn region_init(start: usize, size: usize, eid: usize, shared: bool) -> Result<usize, Error> {
    #[cfg(feature = "usepmp")]
    {
        if shared {
            pmp::pmp_region_init(start, size, pmp::Priority::Bottom, false)
        } else {
            pmp::pmp_region_init(start, size, pmp::Priority::Any, false)
        }
    }
    #[cfg(feature = "usewg")]
    {
        wg_region_init(start, size, 3 << (eid * 2), true)
    }
}

pub fn set_isolator(region_idx: usize) -> Result<(), Error> {
    #[cfg(feature = "usepmp")]
    {
        pmp::set_keystone(region_idx, pmp::PMP_ALL_PERM)
    }
    #[cfg(feature = "usewg")]
    {
        set_wg(region_idx)
    }
}

pub fn reset_isolator(region_idx: usize) -> Result<(), Error> {
    #[cfg(feature = "usepmp")]
    {
        pmp::set_keystone(region_idx, pmp::PMP_NO_PERM)
    }
    #[cfg(feature = "usewg")]
    {
        set_wg(region_idx)
    }
}

pub fn region_free(region_idx: usize) -> Result<(), Error> {
    #[cfg(feature = "usepmp")]
    {
        pmp::pmp_region_free(region_idx)
    }
    #[cfg(feature = "usewg")]
    {
        wg_region_free(region_idx)
    }
}

pub fn display_isolator() {
    #[cfg(feature = "usewg")]
    {
        unsafe {
            let vendor = WGCHECKERS.dram.get_vendor();
            let impid = WGCHECKERS.dram.get_impid();
            let nslots = WGCHECKERS.dram.get_nslots();
            let errcause = WGCHECKERS.dram.get_errcause();
            let erraddr = WGCHECKERS.dram.get_erraddr();

            hprintln!(
                "[WGC][DRAM] REGs vendor: {} impid: {} nslots: {} errcause: {:#x} erraddr: {:#x}",
                vendor,
                impid,
                nslots,
                errcause,
                erraddr
            );
            let vendor = WGCHECKERS.dram.get_vendor();
            let impid = WGCHECKERS.dram.get_impid();
            let nslots = WGCHECKERS.dram.get_nslots();
            let errcause = WGCHECKERS.dram.get_errcause();
            let erraddr = WGCHECKERS.dram.get_erraddr();

            hprintln!(
                "[WGC][DRAM] REGs vendor: {} impid: {} nslots: {} errcause: {:#x} erraddr: {:#x}",
                vendor,
                impid,
                nslots,
                errcause,
                erraddr
            );

            for idx in 0..(nslots + 1) {
                let addr = WGCHECKERS.dram.get_slot_addr(idx as usize);
                let cfg = WGCHECKERS.dram.get_slot_cfg(idx as usize);
                let perm = WGCHECKERS.dram.get_slot_perm(idx as usize);

                hprintln!(
                    "[WGC][DRAM][Slot-{}] cfg: {:#x} addr: {:#x} perm: {:#x}",
                    idx as usize,
                    cfg,
                    addr,
                    perm
                );
            }
        }
    }
}

pub fn update(region_id: usize) -> Result<(), Error> {
    #[cfg(feature = "usepmp")]
    {
        /* below are executed by all harts */
        pmp::reset(pmp::PMP_N_REG);
        let _ = pmp::set_keystone(sm_region_id(), pmp::PMP_NO_PERM);
        let _ = pmp::set_keystone(os_region_id(), pmp::PMP_ALL_PERM);
        pmp::display_pmp();
        Ok(())
    }
    #[cfg(feature = "usewg")]
    {
        set_wg(region_id)
    }
}

pub fn enter_context(eid: usize) {
    #[cfg(feature = "usewg")]
    {
        let mlwid = csr_read_custom!(0x390);
        csr_write_custom!(0x390, eid);
        compiler_fence(Ordering::Release);
        let mlwid = csr_read_custom!(0x390);
    }
}
