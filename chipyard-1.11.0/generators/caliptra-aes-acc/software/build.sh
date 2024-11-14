#!/bin/bash

set -ex

BENCHDIR=aes-simple

rm -rf $BENCHDIR
mkdir -p $BENCHDIR
cp *.c $BENCHDIR
cp *.h $BENCHDIR
pushd $BENCHDIR
riscv64-unknown-elf-gcc -g -fno-common -fno-builtin-printf -specs=htif_nano.specs -c accellib.c
riscv64-unknown-elf-gcc -g -fno-common -fno-builtin-printf -specs=htif_nano.specs -c test.c
riscv64-unknown-elf-gcc -g -static -specs=htif_nano.specs accellib.o test.o -o test.riscv
riscv64-unknown-elf-objdump -S test.riscv > test.dump
popd
