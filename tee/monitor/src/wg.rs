use crate::Error;
use crate::PAGE_SIZE;
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

pub const WGC_ALL_PERM: usize = (1 << (NWORLDS * 2)) - 1;

pub const WGC_DRAM_BASE: usize = 0x600_0000;
pub const WGC_FLASH_BASE: usize = 0x600_1000;
pub const WGC_UART_BASE: usize = 0x600_2000;

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
    p_wgc: &'static mut WGCRegisterBlock,
    p_slot_base: usize,
}

impl WGChecker {
    pub fn new(base: usize) -> WGChecker {
        WGChecker {
            p_wgc: unsafe { &mut *(base as *mut WGCRegisterBlock) },
            p_slot_base: base + WGC_SLOT_OFFSET,
        }
    }

    #[inline]
    pub fn get_vendor(&self) -> u32 {
        self.p_wgc.vendor.read()
    }

    #[inline]
    pub fn get_impid(&self) -> u32 {
        self.p_wgc.impid.read()
    }

    #[inline]
    pub fn get_nslots(&self) -> u32 {
        self.p_wgc.nslots.read()
    }

    #[inline]
    pub fn get_errcause(&self) -> u64 {
        self.p_wgc.errcause.read()
    }

    #[inline]
    pub fn set_errcause(&self, wid: u8, r: bool, w: bool, be: bool, ip: bool) {
        unsafe {
            self.p_wgc.errcause.modify(|v| {
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
        self.p_wgc.erraddr.read()
    }

    #[inline]
    pub fn set_erraddr(&self, addr: u64) {
        unsafe { self.p_wgc.erraddr.write(addr) }
    }

    #[inline]
    pub fn get_slot_addr(&self, idx: usize) -> u64 {
        let ptr = (self.p_slot_base + idx * WGC_SLOT_SIZE) as *const WGCSlot;
        unsafe { (*ptr).addr.read() }
    }

    #[inline]
    pub fn set_slot_addr(&self, idx: usize, addr: u64) {
        let ptr = (self.p_slot_base + idx * WGC_SLOT_SIZE) as *const WGCSlot;
        unsafe { (*ptr).addr.write(addr) }
    }

    #[inline]
    pub fn get_slot_perm(&self, idx: usize) -> u64 {
        let ptr = (self.p_slot_base + idx * WGC_SLOT_SIZE) as *const WGCSlot;
        unsafe { (*ptr).perm.read() }
    }

    #[inline]
    pub fn set_slot_perm(&self, idx: usize, perm: u64) {
        let ptr = (self.p_slot_base + idx * WGC_SLOT_SIZE) as *const WGCSlot;
        unsafe { (*ptr).perm.write(perm) }
    }

    #[inline]
    pub fn get_slot_cfg(&self, idx: usize) -> u32 {
        let ptr = (self.p_slot_base + idx * WGC_SLOT_SIZE) as *const WGCSlot;
        unsafe { (*ptr).cfg.read() }
    }

    #[inline]
    pub fn set_slot_cfg(&self, idx: usize, cfg: u32) {
        let ptr = (self.p_slot_base + idx * WGC_SLOT_SIZE) as *const WGCSlot;
        unsafe { (*ptr).cfg.write(cfg) }
    }
}

pub fn wg_region_init(
    start: usize,
    size: usize,
    perm: u64,
    allow_overlap: bool,
) -> Result<usize, Error> {
    if size == 0 {
        return Err(Error::Invalid);
    }
    /* overlap detection */
    if allow_overlap == false {
        if detect_region_overlap(start, size) {
            return Err(Error::Overlap);
        }
    }

    /* WG granularity check */
    if (size != usize::MAX) && ((size & (PAGE_SIZE - 1)) != 0) {
        return Err(Error::NotPageGranularity);
    }

    if (start & (PAGE_SIZE - 1)) != 0 {
        return Err(Error::NotPageGranularity);
    }

    /* if the address covers the entire RAM or it's NAPOT */
    if (size == usize::MAX && start == 0)
        || (((size & (size - 1)) == 0) && ((start & (size - 1)) == 0))
    {
        return napot_region_init(start, size, perm, allow_overlap);
    } else {
        return tor_region_init(start, size, perm, allow_overlap);
    }
}

pub fn wg_region_free(region_idx: usize) -> Result<(), Error> {
    if !is_wg_region_valid(region_idx) {
        return Err(Error::Invalid);
    }

    let region = unsafe { REGIONS[region_idx].as_ref().unwrap() };
    let reg_idx = region.index();
    unsafe {
        REGION_DEF_BITMAP &= !(1 << region_idx);
        REG_BITMAP &= !(1 << reg_idx);
    }
    if region.needs_two_entries() {
        unsafe { REG_BITMAP &= !(1 << (reg_idx - 1)) };
    }

    unsafe { REGIONS[region_idx] = None }

    Ok(())
}

pub fn detect_region_overlap(addr: usize, size: usize) -> bool {
    let mut region_overlap = false;
    //let input_end = addr + size;
    let input_end = match addr.checked_add(size) {
        Some(sum) => sum,
        None => usize::MAX,
    };

    (1..WG_MAX_N_REGION).for_each(|index| {
        if is_wg_region_valid(index) {
            let region = unsafe { REGIONS[index].as_ref().unwrap() };
            if !region.allows_overlap() {
                let epm_base = region.addr();
                let epm_size = region.size();

                // Only looking at valid regions, no need to check epm_base+size
                region_overlap |= (epm_base < input_end) && (epm_base + epm_size > addr);
            }
        }
    });

    region_overlap
}

pub fn is_wg_region_valid(region_idx: usize) -> bool {
    return (unsafe { REGION_DEF_BITMAP } & (1 << region_idx)) != 0;
}

pub const WG_MAX_N_REGION: usize = 16;
pub const NWORLDS: u64 = 4;
pub const TRUSTED_WID: u64 = NWORLDS - 1;
pub const OS_WID: u64 = NWORLDS - 2;
const INIT_VALUE: Option<Region> = None;

/* PMP region getter/setters */
static mut REGIONS: [Option<Region>; WG_MAX_N_REGION] = [INIT_VALUE; WG_MAX_N_REGION];
static mut REG_BITMAP: usize = 1;
static mut REGION_DEF_BITMAP: usize = 1; // slot[0] is a special rule slot so we don't use it.

/// PMP region type
pub struct Region {
    size: usize,
    mode: u32,
    addr: usize,
    perm: u64,
    allow_overlap: bool,
    index: usize,
}

impl Region {
    pub fn mode(&self) -> u32 {
        self.mode
    }

    pub fn index(&self) -> usize {
        self.index
    }

    pub fn allows_overlap(&self) -> bool {
        self.allow_overlap
    }

    pub fn addr(&self) -> usize {
        self.addr
    }

    pub fn size(&self) -> usize {
        self.size
    }

    pub fn is_napot(&self) -> bool {
        self.mode == WGC_CFG_A_NAPOT
    }

    pub fn is_tor(&self) -> bool {
        self.mode == WGC_CFG_A_TOR
    }

    pub fn needs_two_entries(&self) -> bool {
        self.is_tor() && self.index > 0
    }

    pub fn is_napot_all(&self) -> bool {
        self.addr == usize::MIN && self.size == usize::MAX
    }

    pub fn wgaddr_val(&self) -> u64 {
        if self.is_napot_all() {
            return !0;
        } else if self.is_napot() {
            return ((self.addr | (self.size / 2 - 1)) >> 2) as u64;
        } else if self.is_tor() {
            if self.size == usize::MAX {
                return u64::MAX >> 3;
            } else {
                return ((self.addr + self.size) >> 2) as u64;
            }
        }

        0
    }
}

pub fn napot_region_init<'a>(
    start: usize,
    size: usize,
    perm: u64,
    allow_overlap: bool,
) -> Result<usize, Error> {
    //find available wg region idx
    let region_idx = get_free_region_idx();
    if region_idx.is_none() {
        return Err(Error::MaxReached);
    }

    let region_idx = region_idx.unwrap();
    let reg_idx = get_free_reg_idx().unwrap();

    if ((unsafe { REG_BITMAP } & (1 << reg_idx)) != 0) || (reg_idx >= WG_MAX_N_REGION) {
        return Err(Error::MaxReached);
    }

    // initialize the region
    unsafe {
        REGIONS[region_idx] = Some(Region {
            size,
            mode: WGC_CFG_A_NAPOT,
            addr: start,
            perm,
            allow_overlap,
            index: reg_idx,
        });
    };

    unsafe {
        REGION_DEF_BITMAP |= 1 << region_idx;
        REG_BITMAP |= 1 << reg_idx;
    }

    Ok(region_idx)
}

pub fn get_free_region_idx() -> Option<usize> {
    return search_rightmost_unset(unsafe { REGION_DEF_BITMAP }, WG_MAX_N_REGION, 0x1);
}

pub fn get_free_reg_idx() -> Option<usize> {
    return search_rightmost_unset(unsafe { REG_BITMAP }, WG_MAX_N_REGION, 0x1);
}

pub fn get_conseq_free_reg_idx() -> Option<usize> {
    return search_rightmost_unset(unsafe { REG_BITMAP }, WG_MAX_N_REGION, 0x3);
}

fn search_rightmost_unset(bitmap: usize, max: usize, mask: usize) -> Option<usize> {
    let mut i = 0;
    let mut mask = mask;

    while mask < (1 << max) {
        if (!bitmap & mask) == mask {
            return Some(i);
        }

        mask = mask << 1;
        i += 1;
    }

    None
}

pub fn tor_region_init<'a>(
    start: usize,
    size: usize,
    perm: u64,
    allow_overlap: bool,
) -> Result<usize, Error> {
    let region_idx = get_free_region_idx();
    if region_idx.is_none() {
        return Err(Error::MaxReached);
    }

    let reg_idx = get_conseq_free_reg_idx().unwrap();
    if ((unsafe { REG_BITMAP } & (1 << reg_idx)) != 0)
        || ((unsafe { REG_BITMAP } & (1 << reg_idx + 1)) != 0)
        || (reg_idx + 1 > WG_MAX_N_REGION)
    {
        return Err(Error::MaxReached);
    }

    let region_idx = region_idx.unwrap();

    // FIXME: looks incorrect logic below.
    // initialize the region
    unsafe {
        REGIONS[region_idx] = Some(Region {
            size,
            mode: WGC_CFG_A_TOR,
            addr: start,
            perm,
            allow_overlap,
            index: reg_idx,
        });

        REGION_DEF_BITMAP |= 1 << region_idx;
        REG_BITMAP |= 1 << reg_idx;
    }
    if reg_idx > 0 {
        unsafe { REG_BITMAP |= 1 << (reg_idx + 1) };
    }

    Ok(region_idx)
}

// not used
pub fn set_wg(region_idx: usize) -> Result<(), Error> {
    if !is_wg_region_valid(region_idx) {
        return Err(Error::Invalid);
    }

    let region = unsafe { REGIONS[region_idx].as_ref().unwrap() };
    let reg_idx = if region.is_tor() {
        region.index() + 1
    } else {
        region.index()
    };

    let dram = WGChecker::new(WGC_DRAM_BASE);
    if region.is_tor() {
        dram.set_slot_cfg(reg_idx - 1, 0x0);
        dram.set_slot_addr(reg_idx - 1, (region.addr() >> 2) as u64);
        dram.set_slot_perm(reg_idx - 1, 0); // RW for w3 only
    }
    dram.set_slot_cfg(
        reg_idx,
        WGC_CFG_ER | WGC_CFG_EW | WGC_CFG_IR | WGC_CFG_IW | region.mode,
    );
    dram.set_slot_addr(reg_idx, region.wgaddr_val());
    dram.set_slot_perm(reg_idx, region.perm); // RW for w3 only

    Ok(())
}

pub fn reset_wg(region_idx: usize) -> Result<(), Error> {
    if !is_wg_region_valid(region_idx) {
        return Err(Error::Invalid);
    }

    let region = unsafe { REGIONS[region_idx].as_ref().unwrap() };
    let reg_idx = region.index();

    let dram = WGChecker::new(WGC_DRAM_BASE);
    dram.set_slot_cfg(
        reg_idx,
        WGC_CFG_ER | WGC_CFG_EW | WGC_CFG_IR | WGC_CFG_IW | region.mode,
    );
    dram.set_slot_addr(reg_idx, region.wgaddr_val());
    dram.set_slot_perm(reg_idx, 0); // RW for w3 only

    Ok(())
}
