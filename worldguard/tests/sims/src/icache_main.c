#include <stdio.h>
#include <common/init.h>
#include <common/tests/icache.h>

int main()
{
  printf("---------------------------------------------\n");
  printf("Start Testing WorldGuard...\n");
  //init_worldguard();
  test_icache();
  
  return 0;
}
