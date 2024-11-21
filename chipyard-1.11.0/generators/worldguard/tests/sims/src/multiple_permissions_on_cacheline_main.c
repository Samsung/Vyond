/**
 * This test shows that if two memory regions (two array elements in this test) with different permission list on the same cache line,
 * an wid without permission could have access the unauthorized data. 
 * As shown in this test, your memory regions with different permission slist must not be stored in the same cache line.
 * Simple way to achieve this is to align resions with cacheline.
 *
 * Author: Sungkeun Kim (sk84.kim@samsung.com)
*/

#include <stdio.h>

#include <common/init.h>
#include <common/tests/multiple_permissions_on_cacheline.h>

int main()
{
  printf("---------------------------------------------\n");
  printf("WorldGuard Test - multiple permissions on the same cache block.\n");
  init_worldguard();
  multiple_permissions_on_cacheline();
  
  return 0;
}
