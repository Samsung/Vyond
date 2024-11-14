#!/bin/bash

HOST_PORT="$((RANDOM % 3000 + 3000))"
export HOST_PORT

ROOT_PATH="$(readlink -f "$(cd "$(dirname "$0")" && pwd)")"
echo $ROOT_PATH

echo "**** Running QEMU SSH on port ${HOST_PORT} ****";

export SMP=2;

while [ "$1" != "" ]; do
    if [ "$1" = "-debug" ];
    then
        echo "**** GDB port $((HOST_PORT + 1)) ****";
        DEBUG="-gdb tcp::$((HOST_PORT + 1)) -S -d in_asm -D debug.log";
    fi;
    if [ "$1" = "-smp" ];
    then
        SMP="$2";
        shift;
    fi;
    shift;
done;

QEMU_SYSTEM="$ROOT_PATH/prebuilt/qemu-system-riscv64"
BOOTROM_BIN="$ROOT_PATH/prebuilt/bootrom.bin"
FW_ELF="$ROOT_PATH/sbi/opensbi/build/platform/generic/firmware/fw_payload.elf"
DTB_FILE="$ROOT_PATH/prebuilt/smp.dtb"
LINUX_IMAGE="$ROOT_PATH/prebuilt/Image"
SECOS_IMAGE="$ROOT_PATH/prebuilt/secos.bin"
ROOTFS_IMAGE="$ROOT_PATH/prebuilt/rootfs.ext2"

# Remove rom option from machine
$QEMU_SYSTEM \
    $DEBUG \
    -m 4G \
    -nographic \
    -dtb "$DTB_FILE" \
    -device loader,file="$SECOS_IMAGE",addr=0x80400000 \
    -device loader,file="$LINUX_IMAGE",addr=0x84200000 \
    -drive file="$ROOTFS_IMAGE",format=raw,id=hd0 \
    -device virtio-blk-device,drive=hd0 \
    -machine virt,rom="$BOOTROM_BIN" \
    -bios "$FW_ELF" \
    -netdev user,id=net0,net=192.168.100.1/24,dhcpstart=192.168.100.128,hostfwd=tcp::${HOST_PORT}-:22 \
    -device virtio-net-device,netdev=net0 \
    -device virtio-rng-pci  \
    -smp "$SMP" \
    -semihosting-config enable=on,userspace=on
