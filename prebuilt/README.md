# Prebuild images to run WorldGuard-aware Hardwares
We don't provide a prebuilt images (WG-Aware qemu, bootrom, kernel, and root filesystem) until we check if there is no linsese conflict.
Except WG-Aware qemu, you can build bootrom, linux kernel images, and root filesystem using [Keystone Enclave](https://github.com/keystone-enclave).

The following is inforamation about baseline and patch of qemu source code so that you can apply the patch to the baseline qemu source file and build out of it.

 
| Information | Link |
| -----------| ---- |
|Baseline qemu | https://github.com/qemu/qemu/releases/tag/v9.1.0 |
|Worldguard patch| https://patchwork.ozlabs.org/project/qemu-devel/list/?series=410513 |

[!WARNING]
As we couldn't found the exact baseline of the patch, we did our best to select the version with minimum conflict (v9.1.0).
You may see some conflicts to apply it but you can resolve them without hassles.
