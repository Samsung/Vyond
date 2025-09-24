use crate::enclave;
use bitflags::bitflags;

bitflags! {
    #[derive(Debug, Clone, Copy, PartialEq, Eq)]
    pub struct Perm: i8 {
        const NULL = 0;
        const R = 1;
        const W = 2;
        const X = 4;
        const FULL = Self::R.bits() | Self::W.bits() | Self::X.bits();
    }
}

impl From<i8> for Perm {
    fn from(value: i8) -> Self {
        Perm::from_bits_truncate(value)
    }
}

impl From<Perm> for i8 {
    fn from(p: Perm) -> i8 {
        p.bits()
    }
}

#[derive(Clone, Copy)]
pub struct PermConfig {
    pub eid: usize,
    pub dyn_perm: Perm,
    pub st_perm: Perm,
    pub maps: isize,
}

impl Default for PermConfig {
    fn default() -> Self {
        PermConfig {
            eid: 0,
            dyn_perm: Perm::NULL,
            st_perm: Perm::NULL,
            maps: 0,
        }
    }
}

impl PermConfig {
    pub fn update_dyn_perm(&mut self, new_dyn_perm: Perm) -> bool {
        if self.st_perm.contains(new_dyn_perm) {
            self.dyn_perm = new_dyn_perm;
            return true;
        }
        false
    }
    pub fn increment_map(&mut self) {
        self.maps += 1
    }

    pub fn decrement_map(&mut self) {
        self.maps -= 1
    }
}

#[derive(Clone, Copy, Default)]
pub struct RegionPermConfig {
    pub owner_id: usize,
    pub conf_list: [Option<PermConfig>; enclave::MAX_ENCLAVES],
}

impl RegionPermConfig {
    pub fn get_perm(&self, eid: usize) -> Option<&PermConfig> {
        for conf in self.conf_list.iter() {
            if let Some(cfg) = conf {
                if cfg.eid == eid {
                    return Some(cfg);
                }
            }
        }
        None
    }

    pub fn get_perm_mut(&mut self, eid: usize) -> Option<&mut PermConfig> {
        for conf in self.conf_list.iter_mut() {
            if let Some(cfg) = conf {
                if cfg.eid == eid {
                    return Some(cfg);
                }
            }
        }
        None
    }

    pub fn insert_perm(&mut self, conf: PermConfig) -> bool {
        for slot in self.conf_list.iter_mut() {
            if slot.is_none() {
                *slot = Some(conf);
                return true;
            }
        }
        false
    }
}
