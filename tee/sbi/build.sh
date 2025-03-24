#!/bin/bash

if [ -z "${CROSS_COMPILE}" ]; then
    echo "Error: CROSS_COMPILE is not set"
    exit 1
fi
# set environments (dir path...)
SCRIPT_PATH="$(readlink -f "$0")"
SCRIPT_DIR="$(dirname "$SCRIPT_PATH")"
SCRIPT_NAME="$(basename "$SCRIPT_PATH")"

ROOT_DIR="$(readlink -f "$SCRIPT_DIR"/..)"
BUILD_DIR="$ROOT_DIR/build"

BITS="64"
ISA="rv${BITS}gc"
ABI="lp64d"

FW_PAYLOAD_PATH=$1
#FW_FDT_PATH=$3  # enable it if necessary

SBI_SRC_DIR="$ROOT_DIR/sbi"

OUTPUT_DIR="$ROOT_DIR/output"
SBI_OUT="$OUTPUT_DIR/sm"
BOOTROM_OUT="$OUTPUT_DIR/bootrom"

make -C opensbi O=build PLATFORM_DIR="$SBI_SRC_DIR"/plat/generic FW_PIC=n \
    FW_PAYLOAD=y PLATFORM_RISCV_XLEN=$BITS PLATFORM_RISCV_ISA=$ISA PLATFORM_RISCV_ABI=$ABI\
    CROSS_COMPILE=$CROSS_COMPILE \
    FW_PAYLOAD_PATH=$FW_PAYLOAD_PATH \
    VY_PLATFORM=$VY_PLATFORM
