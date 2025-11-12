//******************************************************************************
// Copyright (c) 2018, The Regents of the University of California (Regents).
// All Rights Reserved. See LICENSE for license details.
//------------------------------------------------------------------------------
#include "edge/edge_call.h"
#include "edge_wrapper.h"
#include "host/keystone.h"
#include "host/SharedMemory.hpp"
#include <pthread.h>

using namespace Keystone;

Enclave enc_publisher, enc_subscriber;
SharedMemory shm;
void *shm_base;
rid_t rid;

void create_enclaves(char *publisher_path, char *subscriber_path, char *eyrie_path, char *loader_path)
{
  Params params;

  params.setFreeMemSize(256 * 1024);
  params.setUntrustedSize(256 * 1024);

  // PUBLISHER
  printf("[HOST] Initializing publisher with %s, %s, %s...\n", publisher_path, eyrie_path, loader_path);
  enc_publisher.init(publisher_path, eyrie_path, loader_path, params);

  enc_publisher.registerOcallDispatch(incoming_call_dispatch);
  edge_call_init_internals(
      (uintptr_t)enc_publisher.getSharedBuffer(), enc_publisher.getSharedBufferSize());

  // SUBSCRIBER
  // printf("[HOST] Initializing subscriber with %s, %s, %s...\n", subscriber_path, eyrie_path, loader_path);
  // enc_subscriber.init(subscriber_path, eyrie_path, loader_path, params);

  // enc_subscriber.registerOcallDispatch(incoming_call_dispatch);
  // edge_call_init_internals(
  //     (uintptr_t)enc_subscriber.getSharedBuffer(), enc_subscriber.getSharedBufferSize());
}

void init_shm()
{

  rid = shm.createShm(0x1000);
  shm.changeShm(rid, 7);
  shm.shareShm(rid, enc_publisher.getEID(), 7);
  // shm.shareShm(rid, enc_subscriber.getEID(), 7);
  printf("[HOST] init_shm created rid %d\n", rid);
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

unsigned long loan_shm(loan_t *loan)
{
  printf("[HOST] loan_shm size: %d perm: %#x rid: %d\n", loan->size, loan->perm, rid);
  return rid;
}

// int main(int argc, char **argv)
//{
//   printf("[HOST] Entering main function of host...\n");
//   Enclave enclave;
//   Params params;
//   params.setFreeMemSize(256 * 1024);
//   params.setUntrustedSize(256 * 1024);
//
//   enclave.init(argv[1], argv[3], argv[4], params);
//   edge_init(&enclave);
//
//   rid = shm.createShm(0x1000);
//   shm.changeShm(rid, 7);
//   shm.shareShm(rid, enclave.getEID(), 7);
//   printf("[HOST] init_shm created rid %d\n", rid);
//
//   uintptr_t encl_ret;
//   enclave.run(&encl_ret);
//
//   printf("[HOST] Terminating host...\n");
//
//   return 0;
// }

int main(int argc, char **argv)
{
  printf("[HOST] Entering main function of host...\n");

  create_enclaves(argv[1], argv[2], argv[3], argv[4]);
  init_shm();
  edge_init(&enc_publisher);
  // edge_init(&enc_subscriber);

  pthread_t thr_publisher, thr_subscriber;
  pthread_create(&thr_publisher, 0, publisher_run, (void *)argv);
  // pthread_create(&thr_subscriber, 0, subscriber_run, (void *)argv);

  pthread_join(thr_publisher, NULL);
  // pthread_join(thr_subscriber, NULL);

  printf("[HOST] Terminating host...\n");

  return 0;
}
