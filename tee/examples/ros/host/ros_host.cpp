//******************************************************************************
// Copyright (c) 2018, The Regents of the University of California (Regents).
// All Rights Reserved. See LICENSE for license details.
//------------------------------------------------------------------------------
#include <pthread.h>

#include "edge/edge_call.h"
#include "host/keystone.h"
#include "host/SharedMemory.hpp"

#include "edge_wrapper.h"

using namespace Keystone;

Enclave enc_publisher, enc_subscriber;
SharedMemory shm[2];
void *shm_base;

void create_enclaves(char *publisher_path, char *subscriber_path, char *eyrie_path, char *loader_path)
{
  Params params;

  params.setFreeMemSize(256 * 1024);
  params.setUntrustedSize(256 * 1024);

  // PUBLISHER
  enc_publisher.init(publisher_path, eyrie_path, loader_path, params);
  printf("[HOST] Initialized publisher with %s, %s, %s shared buffer starts at %#lx\n",
         publisher_path, eyrie_path, loader_path, (uintptr_t)enc_publisher.getSharedBuffer());

  // SUBSCRIBER
  enc_subscriber.init(subscriber_path, eyrie_path, loader_path, params);
  printf("[HOST] Initialized subscriber with %s, %s, %s shared buffer starts at %#lx\n",
         subscriber_path, eyrie_path, loader_path, (uintptr_t)enc_subscriber.getSharedBuffer());
}

void init_shm()
{

  // shared region 0 (w1)
  // It will be used by enclave 1 (publisher) and camera device
  rid_t rid0 = shm[0].createShm(0x1000);
  shm[0].changeShm(rid0, 7);
  shm[0].shareShm(rid0, enc_publisher.getEID(), 7);
  shm[0].shareShm(rid0, enc_subscriber.getEID(), 7);

  rid_t rid1 = shm[1].createShm(0x1000);
  shm[1].changeShm(rid1, 7);
  shm[1].shareShm(rid1, enc_publisher.getEID(), 7);
  shm[1].shareShm(rid1, enc_subscriber.getEID(), 7);
  printf("[HOST] init_shm created rid %d and %d\n", rid0, rid1);
}

void *publisher_run(void *arg)
{

  printf("[HOST][PUBLISHER] start running...\n");
  enc_publisher.run();
  printf("[HOST][PUBLISHER] done ...\n");
  return NULL;
}

void *subscriber_run(void *arg)
{
  printf("[HOST][SUBSCRIBER] start running...\n");
  enc_subscriber.run();
  printf("[HOST][SUBSCRIBER] start done...\n");
  return NULL;
}

unsigned long
print_buffer(char *str)
{
  printf("[HOST] Enclave said: %s", str);
  return strlen(str);
}

void print_value(unsigned long val)
{
  printf("[HOST] Enclave said value: %u (%#x)\n", val, val);
  return;
}

const char *longstr = "hello_ros";
const char *
get_host_string()
{
  return longstr;
}

// id: id from configuration between host and eapp
shm_t loan_shm(int id)
{
  shm_t s;
  s.rid = shm[id].getRID();
  s.pa = (uintptr_t)shm[id].getPA();
  s.size = shm[id].getSize();
  printf("[HOST] loan_shm rid: %d pa: %#lx size: %d\n", s.rid, s.pa, s.size);
  return s;
}

int main(int argc, char **argv)
{
  printf("[HOST] Entering main function of host...\n");

  create_enclaves(argv[1], argv[2], argv[3], argv[4]);
  init_shm();
  edge_init(&enc_publisher);

  pthread_t thr_publisher, thr_subscriber;
  pthread_create(&thr_publisher, 0, publisher_run, (void *)argv);
  pthread_join(thr_publisher, NULL);

  edge_init(&enc_subscriber);
  pthread_create(&thr_subscriber, 0, subscriber_run, (void *)argv);
  pthread_join(thr_subscriber, NULL);

  printf("[HOST] Terminating host...\n");

  return 0;
}
