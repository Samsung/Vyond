#!/usr/bin/env bash

git submodule update --init

RDIR=$(pwd)

mkdir -p overlay/bin

make optstuff

mv cmdline.cc mutilate/
mv cmdline.h mutilate/
cp config.h mutilate/

cp scripts/* overlay/bin/

make
cp mutilate3 overlay/bin/
