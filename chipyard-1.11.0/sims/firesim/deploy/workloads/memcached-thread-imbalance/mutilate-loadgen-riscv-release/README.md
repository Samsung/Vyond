## Mutilate Load Generator for RISC-V

This repo contains scripts to build the [`mutilate`](https://github.com/leverich/mutilate) load generator for RISC-V systems running Linux.

You can find the official mutilate repo here: https://github.com/leverich/mutilate

## Setup

Ideally, you should use this from within the firesim repo, which will automatically give you rootfses with memcached + mutilate built-in. Otherwise:

1) RISC-V Tools must be on your path

2) Enable the following packages in buildroot and run the build process to get rootfses:

* memcached
* zeromq
* zmqpp
* cppzmq

3) In the Makefile, point the sysroot to your copy of buildroot

4) Run ./build.sh

5) Copy overlay into firesim-software's buildroot-overlay
