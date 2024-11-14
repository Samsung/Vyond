# Vyond-Tee Quick Start

## Prerequisites
To build Vyond-Tee, the following tools are required
- _*Cargo*_

As Vyond security monitor is written in Rust, Cargo, Rust Package Manager, is required. See [Cargo Installation](https://doc.rust-lang.org/cargo/getting-started/installation.html) page.

- _*RISC-V Toolchain*_

To build the firmware binary, risc-v toolchain is quired. See [RISC-V Toolchain Installation](https://bernardnongpoh.github.io/posts/riscv) Page. If you have setup `WorldGuard` development environment, you don't have to set it up.

- _*QEMU*_

Vyond-TEE currently run on `qemu-system-riscv64` and we will soon release a new version that run on WorldGuard enabled Rocket SoC. See [Download QEMU](https://www.qemu.org/download/) Page.

### Build Vyond Security Monitor
```sh
cd $VYOND_ROOT/tee/monitor
cargo build
```

### Build Firmware (Opensbi + Security Monitor)

```sh
# Set CROSS_COMPILE environment variable 
export CROSS_COMPILE=$YOUR_TOOLCHAIN/riscv64-unknown-elf-
cd $VYOND_ROOT/tee/sbi
./build.sh
```

### Run Vyond-TEE on QEMU
```sh
cd $VYOND_ROOT/tee
./run.sh
```
