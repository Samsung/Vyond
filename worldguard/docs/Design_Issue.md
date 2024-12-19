Hi all,


I'm Sungkeun Kim working at Samsung Research, Korea.


We (our team) have implemented the WorldGuard (WG) on top of [Rocket SoC](https://github.com/ucb-bar/chipyard) and open-sourced on the github ([https://github.com/Samsung/Vyond).](https://github.com/Samsung/Vyond)
During testing it, we've found a design issue on WG along with cache extension. We'd like to share it, get some feedback from security working group and finally contribute to WorldGuard specification.


# 1. WorldGuard on Rocket SoC
We first briefly introduce WorldGuard-aware Rocket RoC as shown in Figure 1.
There are two Rocket Cores - one with WG-aware and the other core with no-WG-aware. WG Checkers are added in front of Memory Port for DRAM, BootROM, and PLIC.

![WorldGuard Overview](./docs/images/overview.png)
                           Figure 1. WorldGuard on Rocket SoC


We also extended L1/L2 Caches to store a world ID (WID) to each cache line. We exactly follows the description about "WG Impact on Caches" from [the WG technical paper.](https://sifive.cdn.prismic.io/sifive/31b03c05-70fa-4dd8-bb06-127fdb4ba85a_WorldGuard-Technical-Paper_v2.4.pdf)




# 2. Issue Overview
The issue occurs with caches and WGChecker for DRAM. As describe above, upon an access request (read/write) with a transaction wid, the cache allows to full access to the entire cache block if both tag and wid in transaction match with each of them in cache block.
However, giving a full access to the entire cache block can be a security hole if there are more than one memory region with different permissions for a wid stored in cache block.
It is possible for WG user to configure WG Checker so that small memory regions (say 64 Bytes) mapping to a single cache line have different access permissions for one world because a minimum memory region the user can configure on WGChecker slot is 4 byte.


# 3. Proof-Of-Concept
## 3.1 Setting - After access arr[0] with wid-1
As shown in Figure 2, we declare an array (arr) two elements whose size is 8 bytes and they are mapped to the same cache block (highlighted with blue).
Then, we wid-1 (W1 for short) different access permission to those two elements. That is, wid-1 can read/write to arr[0] but it cannot read/write to arr[1] at all.
Lastly, PoC (a test program) sends a request to write to arr[0]. As a result, Caches (both L1/L2) install 64-byte data including arr[0] and arr[1] in the cache block and store transaction wid (1) as well.


                                                          <Figure 2. Proof-of-Concept Setting>


## 3.2 Upon a write to arr[1] with wid-1
We are ready to produce the issue now. PoC sends a write to arr[1] with wid-1. Then, the cache controller (either L1/L2) lookup cache blocks that have the same wid and tag. As the cache has it, the cache considers cache hit and takes actions for it. In other words, the cache does not invalidate the cache line and forward the transaction to WGChecker to see if the wid-1 has a permission to write to arr[1]. This is the issue we are reporting here.
Like we mentioned in "Issue Overview", the reason of this issue is because mismatch between cache block size (64-byte) and the minimum memory region that WGChecker can configure (4-byte). 


## 3.3 What happens during write back?
In addition, we further investigate what will happen during write back. At glance, it seems that this access violation could be caught by WGChecker but it is not the case.
This is because of a bus protocol. We use TileLink bus for PoC but other bus protocols such as AXI would have the same property. In the transaction message, it stores start address and size. Because of this, even if WG Checker receives the write back request, there is no clue or history that arr[1] has been accessed by wid-1. Finally, cache block will be written back to memory with updated arr[1] by wid-1.


# 4. Possible Solutions
## 4.1 An ability to configure granularity of memory region.
As shown in PoC, the issue happens due to the mismatch between size of cache block and minimum size of memory region. We propose the additional configuration register in WGChecker to configure minimum size of memory region.
In this way, WG can still achieve fine-grained access control without this security hole.


## 4.2 Explicitly describe this potential issue on the WG specification.
We agree that the PoC is not common configuration so it would rarely happen. However, we should clear about this case on the specification so that WG user is aware of it.


# 5. PoC code is available our open source
We have posted PoC code running in both FPGA and RTL simulator. Please refer to the following link.
https://github.com/Samsung/Vyond/blob/main/worldguard/tests/lib/tests/multiple_permissions_on_cacheline.c
