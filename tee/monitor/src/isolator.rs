use crate::wg::*;
use crate::{Error, SMM_BASE, SMM_SIZE};
#[cfg(feature = "semihosting")]
use semihosting::hprintln;

pub fn smm_init<'a>() -> Result<usize, Error> {
    #[cfg(feature = "usepmp")]
    return pmp::pmp_region_init(SMM_BASE, SMM_SIZE, pmp::Priority::Top, false);
    #[cfg(feature = "usewg")]
    {
        unsafe {
            WGCHECKERS.dram.set_slot_cfg(
                1,
                WGC_CFG_ER | WGC_CFG_EW | WGC_CFG_IR | WGC_CFG_IW | WGC_CFG_A_NAPOT,
            );
            WGCHECKERS
                .dram
                .set_slot_addr(1, ((SMM_BASE | (SMM_SIZE / 2 - 1)) >> 2) as u64);
            WGCHECKERS.dram.set_slot_perm(1, 0xc0); // RW for w3 only
        }
        return Ok(1);
    }
}

pub fn osm_init<'a>() -> Result<usize, Error> {
    #[cfg(feature = "usepmp")]
    return pmp::pmp_region_init(0, usize::MAX, pmp::Priority::Bottom, true);

    #[cfg(feature = "usewg")]
    {
        unsafe {
            WGCHECKERS
                .dram
                .set_slot_addr(2, (SMM_BASE + SMM_SIZE >> 2) as u64); //
            WGCHECKERS.dram.set_slot_cfg(2, 0x0); //TOR, report all
            WGCHECKERS.dram.set_slot_perm(2, 0x00);

            WGCHECKERS.dram.set_slot_addr(3, u64::MAX >> 3);
            WGCHECKERS.dram.set_slot_cfg(
                3,
                WGC_CFG_ER | WGC_CFG_EW | WGC_CFG_IR | WGC_CFG_IW | WGC_CFG_A_TOR,
            );
            WGCHECKERS.dram.set_slot_perm(3, 0x30); // RW for w2 (OS)
            let nslots = WGCHECKERS.flash.get_nslots();
            WGCHECKERS.flash.set_slot_perm(nslots as usize, 0xf0); // RW for w2, and w1
            let nslots = WGCHECKERS.uart.get_nslots();
            WGCHECKERS.uart.set_slot_perm(nslots as usize, 0xf0); // RW for w2, and w1
        }
        return Ok(2);
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
