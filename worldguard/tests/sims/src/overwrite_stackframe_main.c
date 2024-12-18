/**
 * This case shows the unintended overwriting to stackframe 
 * if stack variables to be protected and other variables such as return address are allocated in the same cacheblock.
 * Specifically, with WGChecker for memory hierarchy configured not to raise interrupt or bus error, 
 * if protected data is accessed with unauthorized wid, the entire cacheline would be refilled with zer,
 * which ends up with zeroing other variables in the same cacheline such as return address.
 * WorldGuard does not define how cache controller handles zero data due to unauthorized data.
 * Therefore, we strongly recommend for users to manage the data to be procted with cacheline granularity.
 *
 * Author: Sungkeun Kim (sk84.kim@samsung.com)
 */

#include <stdio.h>

#include <common/init.h>
#include <common/tests/overwrite_stackframe.h>



int main()
{
  printf("---------------------------------------------\n");
  printf("WorldGuard Test - overwrite stackframe\n");
  init_worldguard();
  overwrite_stackframe();
  
  return 0;
}
