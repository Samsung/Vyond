# WorldGuard Test
The basic test environments are adopted from the [Chipyard Tests](https://github.com/ucb-bar/chipyard/tree/1.11.0/tests). 
Test programs run on the chipyard simulation (e.g., verilator) using [libgloss-htif](https://github.com/ucb-bar/libgloss-htif/tree/39234a16247ab1fa234821b251f1f1870c3de343) which is a minimal system calls.

# Build Test Programs
We assume that you have setup chipyard development environment by running `./build-setup.sh` and source `env.sh`.
```sh
cd ${CHIPYARD_ROOT}/generators/worldguard/tests/sims/src
make  # build all test programs
```

# Running Test Programs

As test programs run on the simulation, first you need to build a one design of chipyard configuration. The following example builde WGRocketConfig in debug mode and run the `read_unauthorized_cacheline1.riscv.
```sh
# Enter Verilator directory
cd ${CHIPYARD_ROOT}/sims/verilator
make CONFIG=WGRocketConfig run-binary-debug BINARY=../../generators/worldguard/tests/sims/src/read_unauthorized_cacheline1.riscv
```


# Descripts of Test Programs
## [Read Unauthorized Cacheline (case 1)]()
This test checks if WorldGuard implementation in cache hierarchy evict the cache line 
if wid in metadata of matched cache line and wid in the request are different.
In the test, it refille the lines with wid 3 then tries to access them with other wids without permissions.


## [Read Unauthorized Cacheline (case 2)]()
This test is similar to the `read_unauthorized_cacheline1` except that the cachelines are filled with different wids every iteration.
We hope this case find some corner cases can't found by previous cases.

## [Instruction Cache]()
This test demonstrate protection of program code.
To make it simple, a function (gcd_ref) is targeted to be protected.
This test checks if the extension of the instruction cache works as expected.

## [Read After Write Cacheline]()
This test checks if read after write a cache line with unauthorized wid. 
The WGChecker is configured not to raise neither interrupt nor bus error exception so as to
demonstrate the undefined behavior of cache controller.
Although WorldGuard specification does not define this case, this case must be handled otherwise this could be a security whole.
We suggest to enable interrupt or bus error so that security monitor take an action immediately.

## [Overwriting to Stackframe]()
This case shows the unintended overwriting to stackframe 
if stack variables to be protected and other variables such as return address are allocated in the same cacheblock.
Specifically, with WGChecker for memory hierarchy configured not to raise interrupt or bus error, 
if protected data is accessed with unauthorized wid, the entire cacheline would be refilled with zero,
which ends up with zeroing other variables in the same cacheline such as return address.
WorldGuard does not define how cache controller handles zero data due to unauthorized data.
Therefore, we strongly recommend for users to manage the data to be procted with cacheline granularity.


## [Multiple Permissions on Cacheline]()
This test shows that if two memory regions (two array elements in this test) with different permission list on the same cache line,
an wid without permission could have access the unauthorized data. 
As shown in this test, your memory regions with different permission slist must not be stored in the same cache line.
Simple way to achieve this is to align resions with cacheline.
