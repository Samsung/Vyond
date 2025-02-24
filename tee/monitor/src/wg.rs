use volatile_register::{RO, RW};

/// General WGC
pub const WGC_SLOT_OFFSET: usize = 0x20;
pub const WGC_SLOT_SIZE: usize = 0x20;

pub const WGC_CFG_A_OFF: u32 = 0x0;
pub const WGC_CFG_A_TOR: u32 = 0x1;
pub const WGC_CFG_A_NA4: u32 = 0x2;
pub const WGC_CFG_A_NAPOT: u32 = 0x3;
pub const WGC_CFG_ER: u32 = 1 << 8;
pub const WGC_CFG_EW: u32 = 1 << 9;
pub const WGC_CFG_IR: u32 = 1 << 10;
pub const WGC_CFG_IW: u32 = 1 << 11;
pub const WGC_CFG_L: u32 = 1 << 31;

pub const WGC_ERRCAUSE_R_SHIFT: u8 = 8;
pub const WGC_ERRCAUSE_W_SHIFT: u8 = 9;
pub const WGC_ERRCAUSE_BE_SHIFT: u8 = 62;
pub const WGC_ERRCAUSE_IP_SHIFT: u8 = 63;

/// WGC for Memory
#[repr(C)]
pub struct WGCRegisterBlock {
    pub vendor: RO<u32>,
    pub impid: RO<u32>,
    pub nslots: RO<u32>,
    reserved: RO<u32>,
    pub errcause: RW<u64>,
    pub erraddr: RW<u64>,
    //pub slots: [WGCSlot; NUM_N_SLOTS + 1],     // FIXME: this does not work.. why?
}

#[repr(C)]
pub struct WGCSlot {
    pub addr: RW<u64>,
    pub perm: RW<u64>,
    pub cfg: RW<u32>,
    pub reserved1: RW<u64>,
    pub reserved2: RW<u32>,
}

pub struct WGChecker {
    pub base: usize,
}

impl WGChecker {
    #[inline]
    pub fn get_vendor(&self) -> u32 {
        let ptr = self.base as *const WGCRegisterBlock;
        unsafe { (*ptr).vendor.read() }
    }

    #[inline]
    pub fn get_impid(&self) -> u32 {
        let ptr = self.base as *const WGCRegisterBlock;
        unsafe { (*ptr).impid.read() }
    }

    #[inline]
    pub fn get_nslots(&self) -> u32 {
        let ptr = self.base as *const WGCRegisterBlock;
        unsafe { (*ptr).nslots.read() }
    }

    #[inline]
    pub fn get_errcause(&self) -> u64 {
        let ptr = self.base as *const WGCRegisterBlock;
        unsafe { (*ptr).errcause.read() }
    }

    #[inline]
    pub fn set_errcause(&mut self, wid: u8, r: bool, w: bool, be: bool, ip: bool) {
        let ptr = self.base as *mut WGCRegisterBlock;
        unsafe {
            (*ptr).errcause.modify(|v| {
                v | ((wid as u64)
                    | (r as u64) << WGC_ERRCAUSE_R_SHIFT
                    | (w as u64) << WGC_ERRCAUSE_W_SHIFT
                    | (be as u64) << WGC_ERRCAUSE_BE_SHIFT
                    | (ip as u64) << WGC_ERRCAUSE_IP_SHIFT)
            })
        }
    }

    #[inline]
    pub fn get_erraddr(&self) -> u64 {
        let ptr = self.base as *const WGCRegisterBlock;
        unsafe { (*ptr).erraddr.read() }
    }

    #[inline]
    pub fn set_erraddr(&mut self, addr: u64) {
        let ptr = self.base as *mut WGCRegisterBlock;
        unsafe { (*ptr).erraddr.write(addr) }
    }

    #[inline]
    pub fn get_slot_addr(&self, idx: usize) -> u64 {
        // FIXME: This does not work.. why?
        //let ptr = self.base as *mut WGCRegisterBlock;
        //unsafe { (*ptr).slots[idx].addr.read() }
        let ptr = (self.base + WGC_SLOT_OFFSET + idx * WGC_SLOT_SIZE) as *const WGCSlot;
        unsafe { (*ptr).addr.read() }
    }
    #[inline]
    pub fn set_slot_addr(&mut self, idx: usize, addr: u64) {
        let ptr = (self.base + WGC_SLOT_OFFSET + idx * WGC_SLOT_SIZE) as *const WGCSlot;
        unsafe { (*ptr).addr.write(addr) }
    }
    #[inline]
    pub fn get_slot_perm(&self, idx: usize) -> u64 {
        let ptr = (self.base + WGC_SLOT_OFFSET + idx * WGC_SLOT_SIZE) as *const WGCSlot;
        unsafe { (*ptr).perm.read() }
    }
    #[inline]
    pub fn set_slot_perm(&mut self, idx: usize, perm: u64) {
        let ptr = (self.base + WGC_SLOT_OFFSET + idx * WGC_SLOT_SIZE) as *const WGCSlot;
        unsafe { (*ptr).perm.write(perm) }
    }
    #[inline]
    pub fn get_slot_cfg(&self, idx: usize) -> u32 {
        let ptr = (self.base + WGC_SLOT_OFFSET + idx * WGC_SLOT_SIZE) as *const WGCSlot;
        unsafe { (*ptr).cfg.read() }
    }
    #[inline]
    pub fn set_slot_cfg(&mut self, idx: usize, cfg: u32) {
        let ptr = (self.base + WGC_SLOT_OFFSET + idx * WGC_SLOT_SIZE) as *const WGCSlot;
        unsafe { (*ptr).cfg.write(cfg) }
    }
}

pub struct WGCheckers {
    pub dram: WGChecker,
    pub flash: WGChecker,
    pub uart: WGChecker,
}

pub static mut WGCHECKERS: WGCheckers = WGCheckers {
    dram: WGChecker {
        base: 0x600_0000,
    },
    flash: WGChecker {
        base: 0x600_1000,
    },
    uart: WGChecker {
        base: 0x600_2000,
    },
};
