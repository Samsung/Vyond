use volatile_register::{RO, RW};

/// WorldGuard-Awre Core
pub const MLWID: usize = 0x390;
pub const SLWID: usize = 0x190;
pub const MWIDDELEG: usize = 0x748;

/// General WGC
const NUM_N_SLOTS: usize = 16;

/// WGC for Memory
#[repr(C)]
pub struct WGCRegisterBlock {
    pub vendor: RO<u32>,
    pub impid: RO<u32>,
    pub nslots: RO<u32>,
    reserved: RO<u32>,
    pub errcause: RW<u64>,
    pub erraddr: RW<u64>,
    pub slots: [WGCSlot; NUM_N_SLOTS + 1],
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
                    | (r as u64) << 8
                    | (w as u64) << 9
                    | (be as u64) << 62
                    | (ip as u64) << 63)
            })
        }
    }

    #[inline]
    pub fn get_erraddr(&self) -> u64 {
        let ptr = self.base as *const WGCRegisterBlock;
        unsafe { (*ptr).errcause.read() }
    }

    #[inline]
    pub fn set_erraddr(&mut self, addr: u64) {
        let ptr = self.base as *mut WGCRegisterBlock;
        unsafe { (*ptr).erraddr.write(addr) }
    }

    #[inline]
    pub fn get_slot_addr(&self, idx: usize) -> u64 {
        let ptr = self.base as *const WGCRegisterBlock;
        unsafe { (*ptr).slots[idx].addr.read() }
    }
    #[inline]
    pub fn set_slot_addr(&mut self, idx: usize, addr: u64) {
        let ptr = self.base as *mut WGCRegisterBlock;
        unsafe { (*ptr).slots[idx].addr.write(addr) }
    }
    #[inline]
    pub fn get_slot_perm(&self, idx: usize) -> u64 {
        let ptr = self.base as *const WGCRegisterBlock;
        unsafe { (*ptr).slots[idx].perm.read() }
    }
    #[inline]
    pub fn set_slot_perm(&mut self, idx: usize, perm: u64) {
        let ptr = self.base as *mut WGCRegisterBlock;
        unsafe { (*ptr).slots[idx].perm.write(perm) }
    }
    #[inline]
    pub fn get_slot_cfg(&self, idx: usize) -> u32 {
        let ptr = self.base as *const WGCRegisterBlock;
        unsafe { (*ptr).slots[idx].cfg.read() }
    }
    #[inline]
    pub fn set_slot_cfg(&mut self, idx: usize, cfg: u32) {
        let ptr = self.base as *mut WGCRegisterBlock;
        unsafe { (*ptr).slots[idx].cfg.write(cfg) }
    }
}

pub struct WGCheckers {
    pub dmem: WGChecker,
    pub flash: WGChecker,
    pub uart: WGChecker,
}

/// Set to `true` when `take` or `steal` was called to make `WGCheckers` a singletone.
static mut TAKEN_WG_CHECKERS: bool = false;

impl WGCheckers {
    /// Returns all the WG Checkers *once*
    #[inline]
    pub fn take() -> Option<Self> {
        if unsafe { TAKEN_WG_CHECKERS } {
            None
        } else {
            Some(unsafe { WGCheckers::steal() })
        }
    }

    /// Unchecked version of `WGCheckers::take`
    #[inline]
    pub unsafe fn steal() -> Self {
        TAKEN_WG_CHECKERS = true;

        WGCheckers {
            dmem: WGChecker { base: 0x600_0000 },
            flash: WGChecker { base: 0x600_1000 },
            uart: WGChecker { base: 0x600_2000 },
        }
    }
}
