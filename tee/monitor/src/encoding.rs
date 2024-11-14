/* clang-format off */
pub const MSTATUS_SIE: usize = 0x00000002;
pub const MSTATUS_MIE: usize = 0x00000008;
pub const MSTATUS_SPIE_SHIFT: usize = 5;
pub const MSTATUS_SPIE: usize = 1 << MSTATUS_SPIE_SHIFT;
pub const MSTATUS_UBE: usize = 0x00000040;
pub const MSTATUS_MPIE: usize = 0x00000080;
pub const MSTATUS_SPP_SHIFT: usize = 8;
pub const MSTATUS_SPP: usize = 1 << MSTATUS_SPP_SHIFT;
pub const MSTATUS_MPP_SHIFT: usize = 11;
pub const MSTATUS_MPP: usize = 3 << MSTATUS_MPP_SHIFT;
pub const MSTATUS_FS: usize = 0x00006000;
pub const MSTATUS_XS: usize = 0x00018000;
pub const MSTATUS_VS: usize = 0x00000600;
pub const MSTATUS_MPRV: usize = 0x00020000;
pub const MSTATUS_SUM: usize = 0x00040000;
pub const MSTATUS_MXR: usize = 0x00080000;
pub const MSTATUS_TVM: usize = 0x00100000;
pub const MSTATUS_TW: usize = 0x00200000;
pub const MSTATUS_TSR: usize = 0x00400000;
//#if __riscv_xlen == 64
pub const MSTATUS_UXL: usize = 0x0000000300000000;
pub const MSTATUS_SXL: usize = 0x0000000C00000000;
pub const MSTATUS_SBE: usize = 0x0000001000000000;
pub const MSTATUS_MBE: usize = 0x0000002000000000;
pub const MSTATUS_GVA: usize = 0x0000004000000000;
pub const MSTATUS_GVA_SHIFT: usize = 38;
pub const MSTATUS_MPV: usize = 0x0000008000000000;
//#endif
pub const MSTATUS32_SD: usize = 0x80000000;
pub const MSTATUS64_SD: usize = 0x8000000000000000;

pub const SSTATUS_SIE: usize = MSTATUS_SIE;
pub const SSTATUS_SPIE_SHIFT: usize = MSTATUS_SPIE_SHIFT;
pub const SSTATUS_SPIE: usize = MSTATUS_SPIE;
pub const SSTATUS_SPP_SHIFT: usize = MSTATUS_SPP_SHIFT;
pub const SSTATUS_SPP: usize = MSTATUS_SPP;
pub const SSTATUS_FS: usize = MSTATUS_FS;
pub const SSTATUS_XS: usize = MSTATUS_XS;
pub const SSTATUS_VS: usize = MSTATUS_VS;
pub const SSTATUS_SUM: usize = MSTATUS_SUM;
pub const SSTATUS_MXR: usize = MSTATUS_MXR;
pub const SSTATUS32_SD: usize = MSTATUS32_SD;
pub const SSTATUS64_UXL: usize = MSTATUS_UXL;
pub const SSTATUS64_SD: usize = MSTATUS64_SD;

//#if __riscv_xlen == 64
pub const HSTATUS_VSXL: usize = 0x300000000;
pub const HSTATUS_VSXL_SHIFT: usize = 32;
//#endif
pub const HSTATUS_VTSR: usize = 0x00400000;
pub const HSTATUS_VTW: usize = 0x00200000;
pub const HSTATUS_VTVM: usize = 0x00100000;
pub const HSTATUS_VGEIN: usize = 0x0003f000;
pub const HSTATUS_VGEIN_SHIFT: usize = 12;
pub const HSTATUS_HU: usize = 0x00000200;
pub const HSTATUS_SPVP: usize = 0x00000100;
pub const HSTATUS_SPV: usize = 0x00000080;
pub const HSTATUS_GVA: usize = 0x00000040;
pub const HSTATUS_VSBE: usize = 0x00000020;

pub const IRQ_S_SOFT: usize = 1;
pub const IRQ_VS_SOFT: usize = 2;
pub const IRQ_M_SOFT: usize = 3;
pub const IRQ_S_TIMER: usize = 5;
pub const IRQ_VS_TIMER: usize = 6;
pub const IRQ_M_TIMER: usize = 7;
pub const IRQ_S_EXT: usize = 9;
pub const IRQ_VS_EXT: usize = 10;
pub const IRQ_M_EXT: usize = 11;
pub const IRQ_S_GEXT: usize = 12;
pub const IRQ_PMU_OVF: usize = 13;

pub const MIP_SSIP: usize = 1 << IRQ_S_SOFT;
pub const MIP_VSSIP: usize = 1 << IRQ_VS_SOFT;
pub const MIP_MSIP: usize = 1 << IRQ_M_SOFT;
pub const MIP_STIP: usize = 1 << IRQ_S_TIMER;
pub const MIP_VSTIP: usize = 1 << IRQ_VS_TIMER;
pub const MIP_MTIP: usize = 1 << IRQ_M_TIMER;
pub const MIP_SEIP: usize = 1 << IRQ_S_EXT;
pub const MIP_VSEIP: usize = 1 << IRQ_VS_EXT;
pub const MIP_MEIP: usize = 1 << IRQ_M_EXT;
pub const MIP_SGEIP: usize = 1 << IRQ_S_GEXT;
pub const MIP_LCOFIP: usize = 1 << IRQ_PMU_OVF;

pub const SIP_SSIP: usize = MIP_SSIP;
pub const SIP_STIP: usize = MIP_STIP;

pub const PRV_U: usize = 0;
pub const PRV_S: usize = 1;
pub const PRV_M: usize = 3;

pub const SATP32_MODE: usize = 0x80000000;
pub const SATP32_ASID: usize = 0x7FC00000;
pub const SATP32_PPN: usize = 0x003FFFFF;
pub const SATP64_MODE: usize = 0xF000000000000000;
pub const SATP64_ASID: usize = 0x0FFFF00000000000;
pub const SATP64_PPN: usize = 0x00000FFFFFFFFFFF;

pub const SATP_MODE_OFF: usize = 0;
pub const SATP_MODE_SV32: usize = 1;
pub const SATP_MODE_SV39: usize = 8;
pub const SATP_MODE_SV48: usize = 9;
pub const SATP_MODE_SV57: usize = 10;
pub const SATP_MODE_SV64: usize = 11;

pub const HGATP_MODE_OFF: usize = 0;
pub const HGATP_MODE_SV32X4: usize = 1;
pub const HGATP_MODE_SV39X4: usize = 8;
pub const HGATP_MODE_SV48X4: usize = 9;

pub const HGATP32_MODE_SHIFT: usize = 31;
pub const HGATP32_VMID_SHIFT: usize = 22;
pub const HGATP32_VMID_MASK: usize = 0x1FC00000;
pub const HGATP32_PPN: usize = 0x003FFFFF;

pub const HGATP64_MODE_SHIFT: usize = 60;
pub const HGATP64_VMID_SHIFT: usize = 44;
pub const HGATP64_VMID_MASK: usize = 0x03FFF00000000000;
pub const HGATP64_PPN: usize = 0x00000FFFFFFFFFFF;

pub const PMP_R: usize = 0x01;
pub const PMP_W: usize = 0x02;
pub const PMP_X: usize = 0x04;
pub const PMP_A: usize = 0x18;
pub const PMP_A_TOR: usize = 0x08;
pub const PMP_A_NA4: usize = 0x10;
pub const PMP_A_NAPOT: usize = 0x18;
pub const PMP_L: usize = 0x80;

pub const PMP_SHIFT: usize = 2;
pub const PMP_COUNT: usize = 64;

//#if __riscv_xlen == 64
pub const PMP_ADDR_MASK: usize = (0x1 << 54) - 1;
//#endif

//#if __riscv_xlen == 64
pub const MSTATUS_SD: usize = MSTATUS64_SD;
pub const SSTATUS_SD: usize = SSTATUS64_SD;
pub const SATP_MODE: usize = SATP64_MODE;

pub const HGATP_PPN: usize = HGATP64_PPN;
pub const HGATP_VMID_SHIFT: usize = HGATP64_VMID_SHIFT;
pub const HGATP_VMID_MASK: usize = HGATP64_VMID_MASK;
pub const HGATP_MODE_SHIFT: usize = HGATP64_MODE_SHIFT;
//#endif

pub const TOPI_IID_SHIFT: usize = 16;
pub const TOPI_IID_MASK: usize = 0xfff;
pub const TOPI_IPRIO_MASK: usize = 0xff;

//#if __riscv_xlen == 64
pub const MHPMEVENT_OF: usize = 1 << 63;
pub const MHPMEVENT_MINH: usize = 1 << 62;
pub const MHPMEVENT_SINH: usize = 1 << 61;
pub const MHPMEVENT_UINH: usize = 1 << 60;
pub const MHPMEVENT_VSINH: usize = 1 << 59;
pub const MHPMEVENT_VUINH: usize = 1 << 58;
//#endif

pub const MHPMEVENT_SSCOF_MASK: usize = 0xFFFF000000000000;

//#if __riscv_xlen > 32
pub const ENVCFGH_STCE: usize = 1 << 31;
pub const ENVCFGH_PBMTE: usize = 1 << 30;
//#endif
pub const ENVCFG_CBZE: usize = 1 << 7;
pub const ENVCFG_CBCFE: usize = 1 << 6;
pub const ENVCFG_CBIE_SHIFT: usize = 4;
pub const ENVCFG_CBIE: usize = 0x3 << ENVCFG_CBIE_SHIFT;
pub const ENVCFG_CBIE_ILL: usize = 0x0;
pub const ENVCFG_CBIE_FLUSH: usize = 0x1;
pub const ENVCFG_CBIE_INV: usize = 0x3;
pub const ENVCFG_FIOM: usize = 0x1;

/* ===== User-level CSRs ===== */

/* User Trap Setup (N-extension) */
pub const CSR_USTATUS: usize = 0x000;
pub const CSR_UIE: usize = 0x004;
pub const CSR_UTVEC: usize = 0x005;

/* User Trap Handling (N-extension) */
pub const CSR_USCRATCH: usize = 0x040;
pub const CSR_UEPC: usize = 0x041;
pub const CSR_UCAUSE: usize = 0x042;
pub const CSR_UTVAL: usize = 0x043;
pub const CSR_UIP: usize = 0x044;

/* User Floating-point CSRs */
pub const CSR_FFLAGS: usize = 0x001;
pub const CSR_FRM: usize = 0x002;
pub const CSR_FCSR: usize = 0x003;

/* User Counters/Timers */
pub const CSR_CYCLE: usize = 0xc00;
pub const CSR_TIME: usize = 0xc01;
pub const CSR_INSTRET: usize = 0xc02;
pub const CSR_HPMCOUNTER3: usize = 0xc03;
pub const CSR_HPMCOUNTER4: usize = 0xc04;
pub const CSR_HPMCOUNTER5: usize = 0xc05;
pub const CSR_HPMCOUNTER6: usize = 0xc06;
pub const CSR_HPMCOUNTER7: usize = 0xc07;
pub const CSR_HPMCOUNTER8: usize = 0xc08;
pub const CSR_HPMCOUNTER9: usize = 0xc09;
pub const CSR_HPMCOUNTER10: usize = 0xc0a;
pub const CSR_HPMCOUNTER11: usize = 0xc0b;
pub const CSR_HPMCOUNTER12: usize = 0xc0c;
pub const CSR_HPMCOUNTER13: usize = 0xc0d;
pub const CSR_HPMCOUNTER14: usize = 0xc0e;
pub const CSR_HPMCOUNTER15: usize = 0xc0f;
pub const CSR_HPMCOUNTER16: usize = 0xc10;
pub const CSR_HPMCOUNTER17: usize = 0xc11;
pub const CSR_HPMCOUNTER18: usize = 0xc12;
pub const CSR_HPMCOUNTER19: usize = 0xc13;
pub const CSR_HPMCOUNTER20: usize = 0xc14;
pub const CSR_HPMCOUNTER21: usize = 0xc15;
pub const CSR_HPMCOUNTER22: usize = 0xc16;
pub const CSR_HPMCOUNTER23: usize = 0xc17;
pub const CSR_HPMCOUNTER24: usize = 0xc18;
pub const CSR_HPMCOUNTER25: usize = 0xc19;
pub const CSR_HPMCOUNTER26: usize = 0xc1a;
pub const CSR_HPMCOUNTER27: usize = 0xc1b;
pub const CSR_HPMCOUNTER28: usize = 0xc1c;
pub const CSR_HPMCOUNTER29: usize = 0xc1d;
pub const CSR_HPMCOUNTER30: usize = 0xc1e;
pub const CSR_HPMCOUNTER31: usize = 0xc1f;
pub const CSR_CYCLEH: usize = 0xc80;
pub const CSR_TIMEH: usize = 0xc81;
pub const CSR_INSTRETH: usize = 0xc82;
pub const CSR_HPMCOUNTER3H: usize = 0xc83;
pub const CSR_HPMCOUNTER4H: usize = 0xc84;
pub const CSR_HPMCOUNTER5H: usize = 0xc85;
pub const CSR_HPMCOUNTER6H: usize = 0xc86;
pub const CSR_HPMCOUNTER7H: usize = 0xc87;
pub const CSR_HPMCOUNTER8H: usize = 0xc88;
pub const CSR_HPMCOUNTER9H: usize = 0xc89;
pub const CSR_HPMCOUNTER10H: usize = 0xc8a;
pub const CSR_HPMCOUNTER11H: usize = 0xc8b;
pub const CSR_HPMCOUNTER12H: usize = 0xc8c;
pub const CSR_HPMCOUNTER13H: usize = 0xc8d;
pub const CSR_HPMCOUNTER14H: usize = 0xc8e;
pub const CSR_HPMCOUNTER15H: usize = 0xc8f;
pub const CSR_HPMCOUNTER16H: usize = 0xc90;
pub const CSR_HPMCOUNTER17H: usize = 0xc91;
pub const CSR_HPMCOUNTER18H: usize = 0xc92;
pub const CSR_HPMCOUNTER19H: usize = 0xc93;
pub const CSR_HPMCOUNTER20H: usize = 0xc94;
pub const CSR_HPMCOUNTER21H: usize = 0xc95;
pub const CSR_HPMCOUNTER22H: usize = 0xc96;
pub const CSR_HPMCOUNTER23H: usize = 0xc97;
pub const CSR_HPMCOUNTER24H: usize = 0xc98;
pub const CSR_HPMCOUNTER25H: usize = 0xc99;
pub const CSR_HPMCOUNTER26H: usize = 0xc9a;
pub const CSR_HPMCOUNTER27H: usize = 0xc9b;
pub const CSR_HPMCOUNTER28H: usize = 0xc9c;
pub const CSR_HPMCOUNTER29H: usize = 0xc9d;
pub const CSR_HPMCOUNTER30H: usize = 0xc9e;
pub const CSR_HPMCOUNTER31H: usize = 0xc9f;

/* ===== Supervisor-level CSRs ===== */

/* Supervisor Trap Setup */
pub const CSR_SSTATUS: usize = 0x100;
pub const CSR_SEDELEG: usize = 0x102;
pub const CSR_SIDELEG: usize = 0x103;
pub const CSR_SIE: usize = 0x104;
pub const CSR_STVEC: usize = 0x105;
pub const CSR_SCOUNTEREN: usize = 0x106;

/* Supervisor Configuration */
pub const CSR_SENVCFG: usize = 0x10a;

/* Supervisor Trap Handling */
pub const CSR_SSCRATCH: usize = 0x140;
pub const CSR_SEPC: usize = 0x141;
pub const CSR_SCAUSE: usize = 0x142;
pub const CSR_STVAL: usize = 0x143;
pub const CSR_SIP: usize = 0x144;

/* Sstc extension */
pub const CSR_STIMECMP: usize = 0x14D;
pub const CSR_STIMECMPH: usize = 0x15D;

/* Supervisor Protection and Translation */
pub const CSR_SATP: usize = 0x180;

/* Supervisor-Level Window to Indirectly Accessed Registers (AIA) */
pub const CSR_SISELECT: usize = 0x150;
pub const CSR_SIREG: usize = 0x151;

/* Supervisor-Level Interrupts (AIA) */
pub const CSR_STOPEI: usize = 0x15c;
pub const CSR_STOPI: usize = 0xdb0;

/* Supervisor-Level High-Half CSRs (AIA) */
pub const CSR_SIEH: usize = 0x114;
pub const CSR_SIPH: usize = 0x154;

/* Supervisor stateen CSRs */
pub const CSR_SSTATEEN0: usize = 0x10C;
pub const CSR_SSTATEEN1: usize = 0x10D;
pub const CSR_SSTATEEN2: usize = 0x10E;
pub const CSR_SSTATEEN3: usize = 0x10F;

/* ===== Hypervisor-level CSRs ===== */

/* Hypervisor Trap Setup (H-extension) */
pub const CSR_HSTATUS: usize = 0x600;
pub const CSR_HEDELEG: usize = 0x602;
pub const CSR_HIDELEG: usize = 0x603;
pub const CSR_HIE: usize = 0x604;
pub const CSR_HCOUNTEREN: usize = 0x606;
pub const CSR_HGEIE: usize = 0x607;

/* Hypervisor Configuration */
pub const CSR_HENVCFG: usize = 0x60a;
pub const CSR_HENVCFGH: usize = 0x61a;

/* Hypervisor Trap Handling (H-extension) */
pub const CSR_HTVAL: usize = 0x643;
pub const CSR_HIP: usize = 0x644;
pub const CSR_HVIP: usize = 0x645;
pub const CSR_HTINST: usize = 0x64a;
pub const CSR_HGEIP: usize = 0xe12;

/* Hypervisor Protection and Translation (H-extension) */
pub const CSR_HGATP: usize = 0x680;

/* Hypervisor Counter/Timer Virtualization Registers (H-extension) */
pub const CSR_HTIMEDELTA: usize = 0x605;
pub const CSR_HTIMEDELTAH: usize = 0x615;

/* Virtual Supervisor Registers (H-extension) */
pub const CSR_VSSTATUS: usize = 0x200;
pub const CSR_VSIE: usize = 0x204;
pub const CSR_VSTVEC: usize = 0x205;
pub const CSR_VSSCRATCH: usize = 0x240;
pub const CSR_VSEPC: usize = 0x241;
pub const CSR_VSCAUSE: usize = 0x242;
pub const CSR_VSTVAL: usize = 0x243;
pub const CSR_VSIP: usize = 0x244;
pub const CSR_VSATP: usize = 0x280;

/* Virtual Interrupts and Interrupt Priorities (H-extension with AIA) */
pub const CSR_HVIEN: usize = 0x608;
pub const CSR_HVICTL: usize = 0x609;
pub const CSR_HVIPRIO1: usize = 0x646;
pub const CSR_HVIPRIO2: usize = 0x647;

/* VS-Level Window to Indirectly Accessed Registers (H-extension with AIA) */
pub const CSR_VSISELECT: usize = 0x250;
pub const CSR_VSIREG: usize = 0x251;

/* VS-Level Interrupts (H-extension with AIA) */
pub const CSR_VSTOPEI: usize = 0x25c;
pub const CSR_VSTOPI: usize = 0xeb0;

/* Hypervisor and VS-Level High-Half CSRs (H-extension with AIA) */
pub const CSR_HIDELEGH: usize = 0x613;
pub const CSR_HVIENH: usize = 0x618;
pub const CSR_HVIPH: usize = 0x655;
pub const CSR_HVIPRIO1H: usize = 0x656;
pub const CSR_HVIPRIO2H: usize = 0x657;
pub const CSR_VSIEH: usize = 0x214;
pub const CSR_VSIPH: usize = 0x254;

/* Hypervisor stateen CSRs */
pub const CSR_HSTATEEN0: usize = 0x60C;
pub const CSR_HSTATEEN0H: usize = 0x61C;
pub const CSR_HSTATEEN1: usize = 0x60D;
pub const CSR_HSTATEEN1H: usize = 0x61D;
pub const CSR_HSTATEEN2: usize = 0x60E;
pub const CSR_HSTATEEN2H: usize = 0x61E;
pub const CSR_HSTATEEN3: usize = 0x60F;
pub const CSR_HSTATEEN3H: usize = 0x61F;

/* ===== Machine-level CSRs ===== */

/* Machine Information Registers */
pub const CSR_MVENDORID: usize = 0xf11;
pub const CSR_MARCHID: usize = 0xf12;
pub const CSR_MIMPID: usize = 0xf13;
pub const CSR_MHARTID: usize = 0xf14;

/* Machine Trap Setup */
pub const CSR_MSTATUS: usize = 0x300;
pub const CSR_MISA: usize = 0x301;
pub const CSR_MEDELEG: usize = 0x302;
pub const CSR_MIDELEG: usize = 0x303;
pub const CSR_MIE: usize = 0x304;
pub const CSR_MTVEC: usize = 0x305;
pub const CSR_MCOUNTEREN: usize = 0x306;
pub const CSR_MSTATUSH: usize = 0x310;

/* Machine Configuration */
pub const CSR_MENVCFG: usize = 0x30a;
pub const CSR_MENVCFGH: usize = 0x31a;

/* Machine Trap Handling */
pub const CSR_MSCRATCH: usize = 0x340;
pub const CSR_MEPC: usize = 0x341;
pub const CSR_MCAUSE: usize = 0x342;
pub const CSR_MTVAL: usize = 0x343;
pub const CSR_MIP: usize = 0x344;
pub const CSR_MTINST: usize = 0x34a;
pub const CSR_MTVAL2: usize = 0x34b;

/* Machine Memory Protection */
pub const CSR_PMPCFG0: usize = 0x3a0;
pub const CSR_PMPCFG1: usize = 0x3a1;
pub const CSR_PMPCFG2: usize = 0x3a2;
pub const CSR_PMPCFG3: usize = 0x3a3;
pub const CSR_PMPCFG4: usize = 0x3a4;
pub const CSR_PMPCFG5: usize = 0x3a5;
pub const CSR_PMPCFG6: usize = 0x3a6;
pub const CSR_PMPCFG7: usize = 0x3a7;
pub const CSR_PMPCFG8: usize = 0x3a8;
pub const CSR_PMPCFG9: usize = 0x3a9;
pub const CSR_PMPCFG10: usize = 0x3aa;
pub const CSR_PMPCFG11: usize = 0x3ab;
pub const CSR_PMPCFG12: usize = 0x3ac;
pub const CSR_PMPCFG13: usize = 0x3ad;
pub const CSR_PMPCFG14: usize = 0x3ae;
pub const CSR_PMPCFG15: usize = 0x3af;
pub const CSR_PMPADDR0: usize = 0x3b0;
pub const CSR_PMPADDR1: usize = 0x3b1;
pub const CSR_PMPADDR2: usize = 0x3b2;
pub const CSR_PMPADDR3: usize = 0x3b3;
pub const CSR_PMPADDR4: usize = 0x3b4;
pub const CSR_PMPADDR5: usize = 0x3b5;
pub const CSR_PMPADDR6: usize = 0x3b6;
pub const CSR_PMPADDR7: usize = 0x3b7;
pub const CSR_PMPADDR8: usize = 0x3b8;
pub const CSR_PMPADDR9: usize = 0x3b9;
pub const CSR_PMPADDR10: usize = 0x3ba;
pub const CSR_PMPADDR11: usize = 0x3bb;
pub const CSR_PMPADDR12: usize = 0x3bc;
pub const CSR_PMPADDR13: usize = 0x3bd;
pub const CSR_PMPADDR14: usize = 0x3be;
pub const CSR_PMPADDR15: usize = 0x3bf;
pub const CSR_PMPADDR16: usize = 0x3c0;
pub const CSR_PMPADDR17: usize = 0x3c1;
pub const CSR_PMPADDR18: usize = 0x3c2;
pub const CSR_PMPADDR19: usize = 0x3c3;
pub const CSR_PMPADDR20: usize = 0x3c4;
pub const CSR_PMPADDR21: usize = 0x3c5;
pub const CSR_PMPADDR22: usize = 0x3c6;
pub const CSR_PMPADDR23: usize = 0x3c7;
pub const CSR_PMPADDR24: usize = 0x3c8;
pub const CSR_PMPADDR25: usize = 0x3c9;
pub const CSR_PMPADDR26: usize = 0x3ca;
pub const CSR_PMPADDR27: usize = 0x3cb;
pub const CSR_PMPADDR28: usize = 0x3cc;
pub const CSR_PMPADDR29: usize = 0x3cd;
pub const CSR_PMPADDR30: usize = 0x3ce;
pub const CSR_PMPADDR31: usize = 0x3cf;
pub const CSR_PMPADDR32: usize = 0x3d0;
pub const CSR_PMPADDR33: usize = 0x3d1;
pub const CSR_PMPADDR34: usize = 0x3d2;
pub const CSR_PMPADDR35: usize = 0x3d3;
pub const CSR_PMPADDR36: usize = 0x3d4;
pub const CSR_PMPADDR37: usize = 0x3d5;
pub const CSR_PMPADDR38: usize = 0x3d6;
pub const CSR_PMPADDR39: usize = 0x3d7;
pub const CSR_PMPADDR40: usize = 0x3d8;
pub const CSR_PMPADDR41: usize = 0x3d9;
pub const CSR_PMPADDR42: usize = 0x3da;
pub const CSR_PMPADDR43: usize = 0x3db;
pub const CSR_PMPADDR44: usize = 0x3dc;
pub const CSR_PMPADDR45: usize = 0x3dd;
pub const CSR_PMPADDR46: usize = 0x3de;
pub const CSR_PMPADDR47: usize = 0x3df;
pub const CSR_PMPADDR48: usize = 0x3e0;
pub const CSR_PMPADDR49: usize = 0x3e1;
pub const CSR_PMPADDR50: usize = 0x3e2;
pub const CSR_PMPADDR51: usize = 0x3e3;
pub const CSR_PMPADDR52: usize = 0x3e4;
pub const CSR_PMPADDR53: usize = 0x3e5;
pub const CSR_PMPADDR54: usize = 0x3e6;
pub const CSR_PMPADDR55: usize = 0x3e7;
pub const CSR_PMPADDR56: usize = 0x3e8;
pub const CSR_PMPADDR57: usize = 0x3e9;
pub const CSR_PMPADDR58: usize = 0x3ea;
pub const CSR_PMPADDR59: usize = 0x3eb;
pub const CSR_PMPADDR60: usize = 0x3ec;
pub const CSR_PMPADDR61: usize = 0x3ed;
pub const CSR_PMPADDR62: usize = 0x3ee;
pub const CSR_PMPADDR63: usize = 0x3ef;

/* Machine Counters/Timers */
pub const CSR_MCYCLE: usize = 0xb00;
pub const CSR_MINSTRET: usize = 0xb02;
pub const CSR_MHPMCOUNTER3: usize = 0xb03;
pub const CSR_MHPMCOUNTER4: usize = 0xb04;
pub const CSR_MHPMCOUNTER5: usize = 0xb05;
pub const CSR_MHPMCOUNTER6: usize = 0xb06;
pub const CSR_MHPMCOUNTER7: usize = 0xb07;
pub const CSR_MHPMCOUNTER8: usize = 0xb08;
pub const CSR_MHPMCOUNTER9: usize = 0xb09;
pub const CSR_MHPMCOUNTER10: usize = 0xb0a;
pub const CSR_MHPMCOUNTER11: usize = 0xb0b;
pub const CSR_MHPMCOUNTER12: usize = 0xb0c;
pub const CSR_MHPMCOUNTER13: usize = 0xb0d;
pub const CSR_MHPMCOUNTER14: usize = 0xb0e;
pub const CSR_MHPMCOUNTER15: usize = 0xb0f;
pub const CSR_MHPMCOUNTER16: usize = 0xb10;
pub const CSR_MHPMCOUNTER17: usize = 0xb11;
pub const CSR_MHPMCOUNTER18: usize = 0xb12;
pub const CSR_MHPMCOUNTER19: usize = 0xb13;
pub const CSR_MHPMCOUNTER20: usize = 0xb14;
pub const CSR_MHPMCOUNTER21: usize = 0xb15;
pub const CSR_MHPMCOUNTER22: usize = 0xb16;
pub const CSR_MHPMCOUNTER23: usize = 0xb17;
pub const CSR_MHPMCOUNTER24: usize = 0xb18;
pub const CSR_MHPMCOUNTER25: usize = 0xb19;
pub const CSR_MHPMCOUNTER26: usize = 0xb1a;
pub const CSR_MHPMCOUNTER27: usize = 0xb1b;
pub const CSR_MHPMCOUNTER28: usize = 0xb1c;
pub const CSR_MHPMCOUNTER29: usize = 0xb1d;
pub const CSR_MHPMCOUNTER30: usize = 0xb1e;
pub const CSR_MHPMCOUNTER31: usize = 0xb1f;
pub const CSR_MCYCLEH: usize = 0xb80;
pub const CSR_MINSTRETH: usize = 0xb82;
pub const CSR_MHPMCOUNTER3H: usize = 0xb83;
pub const CSR_MHPMCOUNTER4H: usize = 0xb84;
pub const CSR_MHPMCOUNTER5H: usize = 0xb85;
pub const CSR_MHPMCOUNTER6H: usize = 0xb86;
pub const CSR_MHPMCOUNTER7H: usize = 0xb87;
pub const CSR_MHPMCOUNTER8H: usize = 0xb88;
pub const CSR_MHPMCOUNTER9H: usize = 0xb89;
pub const CSR_MHPMCOUNTER10H: usize = 0xb8a;
pub const CSR_MHPMCOUNTER11H: usize = 0xb8b;
pub const CSR_MHPMCOUNTER12H: usize = 0xb8c;
pub const CSR_MHPMCOUNTER13H: usize = 0xb8d;
pub const CSR_MHPMCOUNTER14H: usize = 0xb8e;
pub const CSR_MHPMCOUNTER15H: usize = 0xb8f;
pub const CSR_MHPMCOUNTER16H: usize = 0xb90;
pub const CSR_MHPMCOUNTER17H: usize = 0xb91;
pub const CSR_MHPMCOUNTER18H: usize = 0xb92;
pub const CSR_MHPMCOUNTER19H: usize = 0xb93;
pub const CSR_MHPMCOUNTER20H: usize = 0xb94;
pub const CSR_MHPMCOUNTER21H: usize = 0xb95;
pub const CSR_MHPMCOUNTER22H: usize = 0xb96;
pub const CSR_MHPMCOUNTER23H: usize = 0xb97;
pub const CSR_MHPMCOUNTER24H: usize = 0xb98;
pub const CSR_MHPMCOUNTER25H: usize = 0xb99;
pub const CSR_MHPMCOUNTER26H: usize = 0xb9a;
pub const CSR_MHPMCOUNTER27H: usize = 0xb9b;
pub const CSR_MHPMCOUNTER28H: usize = 0xb9c;
pub const CSR_MHPMCOUNTER29H: usize = 0xb9d;
pub const CSR_MHPMCOUNTER30H: usize = 0xb9e;
pub const CSR_MHPMCOUNTER31H: usize = 0xb9f;

/* Machine Counter Setup */
pub const CSR_MCOUNTINHIBIT: usize = 0x320;
pub const CSR_MHPMEVENT3: usize = 0x323;
pub const CSR_MHPMEVENT4: usize = 0x324;
pub const CSR_MHPMEVENT5: usize = 0x325;
pub const CSR_MHPMEVENT6: usize = 0x326;
pub const CSR_MHPMEVENT7: usize = 0x327;
pub const CSR_MHPMEVENT8: usize = 0x328;
pub const CSR_MHPMEVENT9: usize = 0x329;
pub const CSR_MHPMEVENT10: usize = 0x32a;
pub const CSR_MHPMEVENT11: usize = 0x32b;
pub const CSR_MHPMEVENT12: usize = 0x32c;
pub const CSR_MHPMEVENT13: usize = 0x32d;
pub const CSR_MHPMEVENT14: usize = 0x32e;
pub const CSR_MHPMEVENT15: usize = 0x32f;
pub const CSR_MHPMEVENT16: usize = 0x330;
pub const CSR_MHPMEVENT17: usize = 0x331;
pub const CSR_MHPMEVENT18: usize = 0x332;
pub const CSR_MHPMEVENT19: usize = 0x333;
pub const CSR_MHPMEVENT20: usize = 0x334;
pub const CSR_MHPMEVENT21: usize = 0x335;
pub const CSR_MHPMEVENT22: usize = 0x336;
pub const CSR_MHPMEVENT23: usize = 0x337;
pub const CSR_MHPMEVENT24: usize = 0x338;
pub const CSR_MHPMEVENT25: usize = 0x339;
pub const CSR_MHPMEVENT26: usize = 0x33a;
pub const CSR_MHPMEVENT27: usize = 0x33b;
pub const CSR_MHPMEVENT28: usize = 0x33c;
pub const CSR_MHPMEVENT29: usize = 0x33d;
pub const CSR_MHPMEVENT30: usize = 0x33e;
pub const CSR_MHPMEVENT31: usize = 0x33f;

/* For RV32 */
pub const CSR_MHPMEVENT3H: usize = 0x723;
pub const CSR_MHPMEVENT4H: usize = 0x724;
pub const CSR_MHPMEVENT5H: usize = 0x725;
pub const CSR_MHPMEVENT6H: usize = 0x726;
pub const CSR_MHPMEVENT7H: usize = 0x727;
pub const CSR_MHPMEVENT8H: usize = 0x728;
pub const CSR_MHPMEVENT9H: usize = 0x729;
pub const CSR_MHPMEVENT10H: usize = 0x72a;
pub const CSR_MHPMEVENT11H: usize = 0x72b;
pub const CSR_MHPMEVENT12H: usize = 0x72c;
pub const CSR_MHPMEVENT13H: usize = 0x72d;
pub const CSR_MHPMEVENT14H: usize = 0x72e;
pub const CSR_MHPMEVENT15H: usize = 0x72f;
pub const CSR_MHPMEVENT16H: usize = 0x730;
pub const CSR_MHPMEVENT17H: usize = 0x731;
pub const CSR_MHPMEVENT18H: usize = 0x732;
pub const CSR_MHPMEVENT19H: usize = 0x733;
pub const CSR_MHPMEVENT20H: usize = 0x734;
pub const CSR_MHPMEVENT21H: usize = 0x735;
pub const CSR_MHPMEVENT22H: usize = 0x736;
pub const CSR_MHPMEVENT23H: usize = 0x737;
pub const CSR_MHPMEVENT24H: usize = 0x738;
pub const CSR_MHPMEVENT25H: usize = 0x739;
pub const CSR_MHPMEVENT26H: usize = 0x73a;
pub const CSR_MHPMEVENT27H: usize = 0x73b;
pub const CSR_MHPMEVENT28H: usize = 0x73c;
pub const CSR_MHPMEVENT29H: usize = 0x73d;
pub const CSR_MHPMEVENT30H: usize = 0x73e;
pub const CSR_MHPMEVENT31H: usize = 0x73f;

/* Counter Overflow CSR */
pub const CSR_SCOUNTOVF: usize = 0xda0;

/* Debug/Trace Registers */
pub const CSR_TSELECT: usize = 0x7a0;
pub const CSR_TDATA1: usize = 0x7a1;
pub const CSR_TDATA2: usize = 0x7a2;
pub const CSR_TDATA3: usize = 0x7a3;

/* Debug Mode Registers */
pub const CSR_DCSR: usize = 0x7b0;
pub const CSR_DPC: usize = 0x7b1;
pub const CSR_DSCRATCH0: usize = 0x7b2;
pub const CSR_DSCRATCH1: usize = 0x7b3;

/* Machine-Level Window to Indirectly Accessed Registers (AIA) */
pub const CSR_MISELECT: usize = 0x350;
pub const CSR_MIREG: usize = 0x351;

/* Machine-Level Interrupts (AIA) */
pub const CSR_MTOPEI: usize = 0x35c;
pub const CSR_MTOPI: usize = 0xfb0;

/* Virtual Interrupts for Supervisor Level (AIA) */
pub const CSR_MVIEN: usize = 0x308;
pub const CSR_MVIP: usize = 0x309;

/* Smstateen extension registers */
/* Machine stateen CSRs */
pub const CSR_MSTATEEN0: usize = 0x30C;
pub const CSR_MSTATEEN0H: usize = 0x31C;
pub const CSR_MSTATEEN1: usize = 0x30D;
pub const CSR_MSTATEEN1H: usize = 0x31D;
pub const CSR_MSTATEEN2: usize = 0x30E;
pub const CSR_MSTATEEN2H: usize = 0x31E;
pub const CSR_MSTATEEN3: usize = 0x30F;
pub const CSR_MSTATEEN3H: usize = 0x31F;

/* Machine-Level High-Half CSRs (AIA) */
pub const CSR_MIDELEGH: usize = 0x313;
pub const CSR_MIEH: usize = 0x314;
pub const CSR_MVIENH: usize = 0x318;
pub const CSR_MVIPH: usize = 0x319;
pub const CSR_MIPH: usize = 0x354;

/* ===== Trap/Exception Causes ===== */

pub const CAUSE_MISALIGNED_FETCH: usize = 0x0;
pub const CAUSE_FETCH_ACCESS: usize = 0x1;
pub const CAUSE_ILLEGAL_INSTRUCTION: usize = 0x2;
pub const CAUSE_BREAKPOINT: usize = 0x3;
pub const CAUSE_MISALIGNED_LOAD: usize = 0x4;
pub const CAUSE_LOAD_ACCESS: usize = 0x5;
pub const CAUSE_MISALIGNED_STORE: usize = 0x6;
pub const CAUSE_STORE_ACCESS: usize = 0x7;
pub const CAUSE_USER_ECALL: usize = 0x8;
pub const CAUSE_SUPERVISOR_ECALL: usize = 0x9;
pub const CAUSE_VIRTUAL_SUPERVISOR_ECALL: usize = 0xa;
pub const CAUSE_MACHINE_ECALL: usize = 0xb;
pub const CAUSE_FETCH_PAGE_FAULT: usize = 0xc;
pub const CAUSE_LOAD_PAGE_FAULT: usize = 0xd;
pub const CAUSE_STORE_PAGE_FAULT: usize = 0xf;
pub const CAUSE_FETCH_GUEST_PAGE_FAULT: usize = 0x14;
pub const CAUSE_LOAD_GUEST_PAGE_FAULT: usize = 0x15;
pub const CAUSE_VIRTUAL_INST_FAULT: usize = 0x16;
pub const CAUSE_STORE_GUEST_PAGE_FAULT: usize = 0x17;

/* Common defines for all smstateen */
pub const SMSTATEEN_MAX_COUNT: usize = 4;
pub const SMSTATEEN0_CS_SHIFT: usize = 0;
pub const SMSTATEEN0_CS: usize = 1 << SMSTATEEN0_CS_SHIFT;
pub const SMSTATEEN0_FCSR_SHIFT: usize = 1;
pub const SMSTATEEN0_FCSR: usize = 1 << SMSTATEEN0_FCSR_SHIFT;
pub const SMSTATEEN0_CONTEXT_SHIFT: usize = 57;
pub const SMSTATEEN0_CONTEXT: usize = 1 << SMSTATEEN0_CONTEXT_SHIFT;
pub const SMSTATEEN0_IMSIC_SHIFT: usize = 58;
pub const SMSTATEEN0_IMSIC: usize = 1 << SMSTATEEN0_IMSIC_SHIFT;
pub const SMSTATEEN0_AIA_SHIFT: usize = 59;
pub const SMSTATEEN0_AIA: usize = 1 << SMSTATEEN0_AIA_SHIFT;
pub const SMSTATEEN0_SVSLCT_SHIFT: usize = 60;
pub const SMSTATEEN0_SVSLCT: usize = 1 << SMSTATEEN0_SVSLCT_SHIFT;
pub const SMSTATEEN0_HSENVCFG_SHIFT: usize = 62;
pub const SMSTATEEN0_HSENVCFG: usize = 1 << SMSTATEEN0_HSENVCFG_SHIFT;
pub const SMSTATEEN_STATEN_SHIFT: usize = 63;
pub const SMSTATEEN_STATEN: usize = 1 << SMSTATEEN_STATEN_SHIFT;

/* ===== Instruction Encodings ===== */

pub const INSN_MATCH_LB: usize = 0x3;
pub const INSN_MASK_LB: usize = 0x707f;
pub const INSN_MATCH_LH: usize = 0x1003;
pub const INSN_MASK_LH: usize = 0x707f;
pub const INSN_MATCH_LW: usize = 0x2003;
pub const INSN_MASK_LW: usize = 0x707f;
pub const INSN_MATCH_LD: usize = 0x3003;
pub const INSN_MASK_LD: usize = 0x707f;
pub const INSN_MATCH_LBU: usize = 0x4003;
pub const INSN_MASK_LBU: usize = 0x707f;
pub const INSN_MATCH_LHU: usize = 0x5003;
pub const INSN_MASK_LHU: usize = 0x707f;
pub const INSN_MATCH_LWU: usize = 0x6003;
pub const INSN_MASK_LWU: usize = 0x707f;
pub const INSN_MATCH_SB: usize = 0x23;
pub const INSN_MASK_SB: usize = 0x707f;
pub const INSN_MATCH_SH: usize = 0x1023;
pub const INSN_MASK_SH: usize = 0x707f;
pub const INSN_MATCH_SW: usize = 0x2023;
pub const INSN_MASK_SW: usize = 0x707f;
pub const INSN_MATCH_SD: usize = 0x3023;
pub const INSN_MASK_SD: usize = 0x707f;

pub const INSN_MATCH_FLW: usize = 0x2007;
pub const INSN_MASK_FLW: usize = 0x707f;
pub const INSN_MATCH_FLD: usize = 0x3007;
pub const INSN_MASK_FLD: usize = 0x707f;
pub const INSN_MATCH_FLQ: usize = 0x4007;
pub const INSN_MASK_FLQ: usize = 0x707f;
pub const INSN_MATCH_FSW: usize = 0x2027;
pub const INSN_MASK_FSW: usize = 0x707f;
pub const INSN_MATCH_FSD: usize = 0x3027;
pub const INSN_MASK_FSD: usize = 0x707f;
pub const INSN_MATCH_FSQ: usize = 0x4027;
pub const INSN_MASK_FSQ: usize = 0x707f;

pub const INSN_MATCH_C_LD: usize = 0x6000;
pub const INSN_MASK_C_LD: usize = 0xe003;
pub const INSN_MATCH_C_SD: usize = 0xe000;
pub const INSN_MASK_C_SD: usize = 0xe003;
pub const INSN_MATCH_C_LW: usize = 0x4000;
pub const INSN_MASK_C_LW: usize = 0xe003;
pub const INSN_MATCH_C_SW: usize = 0xc000;
pub const INSN_MASK_C_SW: usize = 0xe003;
pub const INSN_MATCH_C_LDSP: usize = 0x6002;
pub const INSN_MASK_C_LDSP: usize = 0xe003;
pub const INSN_MATCH_C_SDSP: usize = 0xe002;
pub const INSN_MASK_C_SDSP: usize = 0xe003;
pub const INSN_MATCH_C_LWSP: usize = 0x4002;
pub const INSN_MASK_C_LWSP: usize = 0xe003;
pub const INSN_MATCH_C_SWSP: usize = 0xc002;
pub const INSN_MASK_C_SWSP: usize = 0xe003;

pub const INSN_MATCH_C_FLD: usize = 0x2000;
pub const INSN_MASK_C_FLD: usize = 0xe003;
pub const INSN_MATCH_C_FLW: usize = 0x6000;
pub const INSN_MASK_C_FLW: usize = 0xe003;
pub const INSN_MATCH_C_FSD: usize = 0xa000;
pub const INSN_MASK_C_FSD: usize = 0xe003;
pub const INSN_MATCH_C_FSW: usize = 0xe000;
pub const INSN_MASK_C_FSW: usize = 0xe003;
pub const INSN_MATCH_C_FLDSP: usize = 0x2002;
pub const INSN_MASK_C_FLDSP: usize = 0xe003;
pub const INSN_MATCH_C_FSDSP: usize = 0xa002;
pub const INSN_MASK_C_FSDSP: usize = 0xe003;
pub const INSN_MATCH_C_FLWSP: usize = 0x6002;
pub const INSN_MASK_C_FLWSP: usize = 0xe003;
pub const INSN_MATCH_C_FSWSP: usize = 0xe002;
pub const INSN_MASK_C_FSWSP: usize = 0xe003;

pub const INSN_MASK_WFI: usize = 0xffffff00;
pub const INSN_MATCH_WFI: usize = 0x10500000;

pub const INSN_MASK_FENCE_TSO: usize = 0xffffffff;
pub const INSN_MATCH_FENCE_TSO: usize = 0x8330000f;

//#if __riscv_xlen == 64

/* 64-bit read for VS-stage address translation (RV64) */
pub const INSN_PSEUDO_VS_LOAD: usize = 0x00003000;

/* 64-bit write for VS-stage address translation (RV64) */
pub const INSN_PSEUDO_VS_STORE: usize = 0x00003020;

//#endif

pub const INSN_16BIT_MASK: usize = 0x3;
pub const INSN_32BIT_MASK: usize = 0x1c;

//#if __riscv_xlen == 64
pub const LOG_REGBYTES: usize = 3;
//#endif
pub const REGBYTES: usize = 1 << LOG_REGBYTES;

pub const SH_RD: usize = 7;
pub const SH_RS1: usize = 15;
pub const SH_RS2: usize = 20;
pub const SH_RS2C: usize = 2;
