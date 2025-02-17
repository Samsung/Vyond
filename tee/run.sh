#!/bin/bash

HOST_PORT="$((RANDOM % 3000 + 3000))"
export HOST_PORT

ROOT_PATH="$(readlink -f "$(cd "$(dirname "$0")" && pwd)")"
echo $ROOT_PATH

ROOTFS_IMAGE="$ROOT_PATH/../prebuilt/rootfs.ext2"
LINUX_IMAGE="$ROOT_PATH/../prebuilt/Image"

echo "**** Running QEMU SSH on port ${HOST_PORT} ****";

export SMP=1;

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
    if [ "$1" = "-fw" ];
    then
        FW_ELF="$2";
        shift;
    fi;
    if [ "$1" = "-qemu" ];
    then
        QEMU_SYSTEM="$2";
        shift;
    fi;
    shift;
done;

for var in QEMU_SYSTEM FW_ELF; do
    if [ -z "${!var}" ]; then
        echo "$var is not set"
        exit 1
    fi
done

#QEMU_SYSTEM="/opt/qemu-v9.1.0/bin/qemu-system-riscv64"
#FW_ELF="$ROOT_PATH/sbi/opensbi/build/platform/generic/firmware/fw_payload.elf"

# Remove rom option from machine
CMD="$QEMU_SYSTEM $DEBUG -m 4G -nographic -machine virt,wg=on -bios $FW_ELF -kernel $LINUX_IMAGE -append console=ttyS0 ro root=/dev/vda -drive if=none,file=$ROOTFS_IMAGE,format=raw,id=hd0 -device virtio-blk-device,drive=hd0 -netdev user,id=net0,net=192.168.100.1/24,dhcpstart=192.168.100.128,hostfwd=tcp::${HOST_PORT}-:22 -device virtio-net-device,netdev=net0 -device virtio-rng-pci  -smp $SMP -semihosting-config enable=on,userspace=on"
echo $CMD
$QEMU_SYSTEM \
    $DEBUG \
    -m 4G \
    -nographic \
    -machine virt,wg=on \
    -bios "$FW_ELF" \
    -kernel "$LINUX_IMAGE" \
    -append "console=ttyS0 ro root=/dev/vda" \
    -drive if=none,file="$ROOTFS_IMAGE",format=raw,id=hd0 \
    -device virtio-blk-device,drive=hd0 \
    -netdev user,id=net0,net=192.168.100.1/24,dhcpstart=192.168.100.128,hostfwd=tcp::${HOST_PORT}-:22 \
    -device virtio-net-device,netdev=net0 \
    -device virtio-rng-pci  \
    -smp "$SMP" \
    -semihosting-config enable=on,userspace=on
