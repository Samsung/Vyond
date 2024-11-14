Gemmini Spike ISA Functional Model Extensions
=============================================

This repository builds libgemmini.so, which can be dynamically linked into Spike to support executing custom Gemmini instructions.

To use this, first install a recent version of [spike](https://github.com/riscv-software-src/riscv-isa-sim), and set the `$RISCV` environment variable to the install location.

Usage:
```
make
make install
spike --extension=gemmini <custom_gemmini_program>
```

To see Gemmini commit logs, use the `--log-commits` flag in `spike`.
