/**
 * This test checks if read after write a cache line with unauthorized wid. 
 * The WGChecker is configured not to raise neither interrupt nor bus error exception so as to
 * demonstrate the undefined behavior of cache controller.
 * Although WorldGuard specification does not define this case, this case must be handled otherwise this could be a security whole.
 * We suggest to enable interrupt or bus error so that security monitor take an action immediately.
 *
 * Author: Sungkeun Kim (sk84.kim@samsung.com)
*/
#include <stdio.h>

#include <common/init.h>
#include <common/tests/raw_cacheline.h>

int main()
{
  printf("---------------------------------------------\n");
  printf("Start Testing Read After Write Cachelines (demonstrating undefined cache behavior)...\n");
  init_worldguard();
  raw_cacheline();
  
  return 0;
}
