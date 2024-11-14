#!/usr/bin/env bash

set -e
set -o pipefail
RDIR=$(git rev-parse --show-toplevel)


#zip -r archive.zip bandwidth.xclbin resnet50_params.h shell.dcp
#split -b 40m archive.zip archive_part_
#rm archive.zip

cat bigfiles/archive_part_* > bigfiles/archive.zip
unzip bigfiles/archive.zip -d bigfiles

mv bigfiles/resnet50_params.h ./generators/gemmini/software/gemmini-rocc-tests/imagenet/resnet50_params.h
cp bigfiles/bandwidth.xclbin ./sims/firesim/platforms/f1/aws-fpga/Vitis/examples/xilinx_2020.1/host/p2p_fpga2fpga_bandwidth/hw_bins/u250_xdma_201830_2/bandwidth.xclbin
mv bigfiles/bandwidth.xclbin ./sims/firesim/platforms/f1/aws-fpga/Vitis/examples/xilinx_2020.2/host/p2p_fpga2fpga_bandwidth/hw_bins/u250_xdma_201830_2/bandwidth.xclbin
mv bigfiles/shell.dcp ./sims/firesim/platforms/xilinx_vcu118/garnet-firesim/shell/prebuilt/shell.dcp

rm bigfiles/archive.zip
