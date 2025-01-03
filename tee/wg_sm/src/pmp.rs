#[cfg(not(feature = "semihosting"))]
use crate::api::sbi_printf;
use crate::encoding::{PMP_A_NAPOT, PMP_A_TOR, PMP_R, PMP_W, PMP_X};
use crate::Error;
use crate::PAGE_SIZE;
#[cfg(not(feature = "semihosting"))]
use core::ffi::c_char;

#[derive(PartialEq)]
pub enum Priority {
    Any,
    Top,
    Bottom,
}

pub const PMP_N_REG: usize = 8;
const PMP_PER_GROUP: usize = 8;
const PMP_MAX_N_REGION: usize = 8;

pub const PMP_ALL_PERM: usize = PMP_W | PMP_X | PMP_R;
pub const PMP_NO_PERM: usize = 0;

#[macro_export]
macro_rules! pmp_set {
    ($index:expr, $group:expr, $addr:expr, $pmpc:expr) => {{
        use core::arch::asm;
        let oldcfg = paste::paste!{ csr_read!([<pmpcfg $group>]) };
        $pmpc |= (oldcfg & !(0xff << (8 * ($index % PMP_PER_GROUP))));
        unsafe {
            asm!(concat!("\
                la      t0, 1f\n
                csrrw   t0, mtvec, t0\n
                csrw    ", paste::paste! { stringify!([<pmpaddr $index>]) }, ", {reg0}\n
                csrw    ", paste::paste! { stringify!([<pmpcfg $group>]) },  ", {reg1}\n
                sfence.vma\n
                .align  2\n
                1: csrw mtvec, t0"
            ),
            reg0 = in(reg) $addr,
            reg1 = in(reg) $pmpc,
            lateout("t0") _,
            );
        }
    }}
}

#[macro_export]
macro_rules! pmp_unset {
    ($index:expr, $group:expr) => {{
        use core::arch::asm;
        let mut pmpc = paste::paste! { csr_read!([<pmpcfg $group>]) };
        pmpc &= !(0xff << (8 * ($index % PMP_PER_GROUP)));
        unsafe {
            asm!(concat!("\
                la      t0, 1f\n
                csrrw   t0, mtvec, t0\n
                csrw    ", paste::paste! { stringify!([<pmpaddr $index>]) }, ", {reg0}\n
                csrw    ", paste::paste! { stringify!([<pmpcfg $group>]) },  ", {reg1}\n
                sfence.vma\n
                .align  2\n
                1: csrw mtvec, t0"
            ),
            reg0 = in(reg) 0,
            reg1 = in(reg) pmpc,
            lateout("t0") _,
            );
        }
    }}
}

const INIT_VALUE: Option<Region> = None;

/* PMP region getter/setters */
static mut REGIONS: [Option<Region>; PMP_MAX_N_REGION] = [INIT_VALUE; PMP_MAX_N_REGION];
static mut REG_BITMAP: usize = 0;
static mut REGION_DEF_BITMAP: usize = 0;

/// PMP region type
pub struct Region {
    size: usize,
    mode: usize,
    addr: usize,
    allow_overlap: bool,
    index: usize,
}

impl Region {
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
        self.mode == PMP_A_NAPOT
    }

    pub fn is_tor(&self) -> bool {
        self.mode == PMP_A_TOR
    }

    pub fn needs_two_entries(&self) -> bool {
        self.is_tor() && self.index > 0
    }

    pub fn is_napot_all(&self) -> bool {
        self.addr == usize::MIN && self.size == usize::MAX
    }

    pub fn pmpaddr_val(&self) -> usize {
        if self.is_napot_all() {
            return !0;
        } else if self.is_napot() {
            return (self.addr | (self.size / 2 - 1)) >> 2;
        } else if self.is_tor() {
            return (self.addr + self.size) >> 2;
        }

        0
    }

    pub fn pmpcfg_val(&self, reg_idx: usize, perm: usize) -> usize {
        (self.mode | perm) << (8 * (reg_idx % PMP_PER_GROUP))
    }
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

pub fn get_free_region_idx() -> Option<usize> {
    return search_rightmost_unset(unsafe { REGION_DEF_BITMAP }, PMP_MAX_N_REGION, 0x1);
}

pub fn get_free_reg_idx() -> Option<usize> {
    return search_rightmost_unset(unsafe { REG_BITMAP }, PMP_N_REG, 0x1);
}

pub fn get_conseq_free_reg_idx() -> Option<usize> {
    return search_rightmost_unset(unsafe { REG_BITMAP }, PMP_N_REG, 0x3);
}

/* We do an integery overflow safety check here for the inputs (addr +
 * size).  We do NOT do a safety check on epm_base + epm_size, since
 * only valid region should have been created previously.
 *
 * On a failed addr + size overflow, we return failure, since this
 * cannot be a valid addr and size anyway.
 */
pub fn detect_region_overlap(addr: usize, size: usize) -> bool {
    let mut region_overlap = false;
    let input_end = addr + size;

    (0..PMP_MAX_N_REGION).for_each(|index| {
        if is_pmp_region_valid(index) {
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

pub fn pmp_detect_region_overlap_atomic(addr: usize, size: usize) -> bool {
    detect_region_overlap(addr, size)
}

pub fn reset(count: usize) {
    (0..count).for_each(|index| match index {
        0 => pmp_unset!(0, 0),
        1 => pmp_unset!(1, 0),
        2 => pmp_unset!(2, 0),
        3 => pmp_unset!(3, 0),
        4 => pmp_unset!(4, 0),
        5 => pmp_unset!(5, 0),
        6 => pmp_unset!(6, 0),
        7 => pmp_unset!(7, 0),
        8 => pmp_unset!(8, 2),
        9 => pmp_unset!(9, 2),
        10 => pmp_unset!(10, 2),
        11 => pmp_unset!(11, 2),
        12 => pmp_unset!(12, 2),
        13 => pmp_unset!(13, 2),
        14 => pmp_unset!(14, 2),
        15 => pmp_unset!(15, 2),
        _ => (),
    })
}

pub fn pmp_region_free(region_idx: usize) -> Result<(), Error> {
    if !is_pmp_region_valid(region_idx) {
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

pub fn pmp_region_init<'a>(
    start: usize,
    size: usize,
    priority: Priority,
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

    /* PMP granularity check */
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
        return napot_region_init(start, size, priority, allow_overlap);
    } else {
        if (priority != Priority::Any) && ((priority != Priority::Top) || (start != 0)) {
            return Err(Error::Invalid);
        }

        return tor_region_init(start, size, priority, allow_overlap);
    }
}

pub fn is_pmp_region_valid(region_idx: usize) -> bool {
    return (unsafe { REGION_DEF_BITMAP } & (1 << region_idx)) != 0;
}

pub fn set_keystone(region_idx: usize, perm: usize) -> Result<(), Error> {
    if !is_pmp_region_valid(region_idx) {
        return Err(Error::Invalid);
    }

    let region = unsafe { REGIONS[region_idx].as_ref().unwrap() };
    let reg_idx = region.index();
    let mut pmpcfg = region.pmpcfg_val(reg_idx, perm & PMP_ALL_PERM);
    let pmpaddr = region.pmpaddr_val();

    match reg_idx {
        0 => pmp_set!(0, 0, pmpaddr, pmpcfg),
        1 => pmp_set!(1, 0, pmpaddr, pmpcfg),
        2 => pmp_set!(2, 0, pmpaddr, pmpcfg),
        3 => pmp_set!(3, 0, pmpaddr, pmpcfg),
        4 => pmp_set!(4, 0, pmpaddr, pmpcfg),
        5 => pmp_set!(5, 0, pmpaddr, pmpcfg),
        6 => pmp_set!(6, 0, pmpaddr, pmpcfg),
        7 => pmp_set!(7, 0, pmpaddr, pmpcfg),
        8 => pmp_set!(8, 2, pmpaddr, pmpcfg),
        9 => pmp_set!(9, 2, pmpaddr, pmpcfg),
        10 => pmp_set!(10, 2, pmpaddr, pmpcfg),
        11 => pmp_set!(11, 2, pmpaddr, pmpcfg),
        12 => pmp_set!(12, 2, pmpaddr, pmpcfg),
        13 => pmp_set!(13, 2, pmpaddr, pmpcfg),
        14 => pmp_set!(14, 2, pmpaddr, pmpcfg),
        15 => pmp_set!(15, 2, pmpaddr, pmpcfg),
        _ => return Err(Error::Invalid),
    }

    Ok(())
}

pub fn tor_region_init<'a>(
    start: usize,
    size: usize,
    priority: Priority,
    allow_overlap: bool,
) -> Result<usize, Error> {
    let region_idx = get_free_region_idx();
    if region_idx.is_none() {
        return Err(Error::MaxReached);
    }

    let reg_idx = match priority {
        Priority::Any => {
            let reg_idx = get_conseq_free_reg_idx().unwrap();
            if ((unsafe { REG_BITMAP } & (1 << reg_idx)) != 0)
                || ((unsafe { REG_BITMAP } & (1 << reg_idx + 1)) != 0)
                || (reg_idx + 1 > PMP_N_REG)
            {
                return Err(Error::MaxReached);
            }
            reg_idx
        }
        Priority::Top => {
            //sm_assert(start == 0);
            let reg_idx = 0;
            if (unsafe { REG_BITMAP } & (1 << reg_idx)) != 0 {
                return Err(Error::MaxReached);
            }
            reg_idx
        }
        _ => {
            return Err(Error::Invalid);
        }
    };

    let region_idx = region_idx.unwrap();

    #[cfg(not(feature = "semihosting"))]
    {
        let format =
            b"initialize the region_idx: %d reg_idx: %d start addr: %#x size: %#x mode: TOR,\n\0"
                .as_ptr()
                .cast::<c_char>();
        unsafe {
            sbi_printf(format, region_idx, reg_idx, start, size);
        }
    }

    // initialize the region
    unsafe {
        REGIONS[region_idx] = Some(Region {
            size: size,
            mode: PMP_A_TOR,
            addr: start,
            allow_overlap: allow_overlap,
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

pub fn napot_region_init<'a>(
    start: usize,
    size: usize,
    priority: Priority,
    allow_overlap: bool,
) -> Result<usize, Error> {
    //find avaiable pmp region idx
    let region_idx = get_free_region_idx();
    if region_idx.is_none() {
        return Err(Error::MaxReached);
    }

    let region_idx = region_idx.unwrap();

    let reg_idx = match priority {
        Priority::Any => {
            let reg_idx = get_free_reg_idx().unwrap();

            if ((unsafe { REG_BITMAP } & (1 << reg_idx)) != 0) || (reg_idx >= PMP_N_REG) {
                return Err(Error::MaxReached);
            }
            reg_idx
        }
        Priority::Top => {
            let reg_idx = 0;
            if (unsafe { REG_BITMAP } & (1 << reg_idx)) != 0 {
                return Err(Error::MaxReached);
            }
            reg_idx
        }
        Priority::Bottom => {
            /* the bottom register can be used by multiple regions,
             * so we don't check its availability */
            PMP_N_REG - 1
        }
    };

    #[cfg(not(feature = "semihosting"))]
    {
        let format =
            b"initialize the region_idx: %d reg_idx: %d start addr: %#x size: %#x mode: NAPOT,\n\0"
                .as_ptr()
                .cast::<c_char>();
        unsafe {
            sbi_printf(format, region_idx, reg_idx, start, size);
        }
    }

    // initialize the region
    unsafe {
        REGIONS[region_idx] = Some(Region {
            size: size,
            mode: PMP_A_NAPOT,
            addr: start,
            allow_overlap: allow_overlap,
            index: reg_idx,
        });
    };

    unsafe {
        REGION_DEF_BITMAP |= 1 << region_idx;
        REG_BITMAP |= 1 << reg_idx;
    }

    Ok(region_idx)
}
