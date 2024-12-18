#include <stdio.h>
#include <common/init.h>
#include <common/tests/read_unauthorized_cacheline1.h>

int main()
{
  printf("---------------------------------------------\n");
  printf("Start Testing WorldGuard Read unauthorized cache line (check eviction due to wid miss match)...\n");
  init_worldguard();
  read_unauthorized_cacheline1();
  
  return 0;
}
