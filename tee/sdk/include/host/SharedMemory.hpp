//******************************************************************************
// Copyright (c) 2018, The Regents of the University of California (Regents).
// All Rights Reserved. See LICENSE for license details.
//------------------------------------------------------------------------------
#pragma once

#include <assert.h>
#include <fcntl.h>
#include <stdarg.h>
#include <stddef.h>
#include <sys/ioctl.h>
#include <sys/types.h>
#include <unistd.h>

#include <cerrno>
#include <cstring>
#include <iostream>

#include "./common.h"
#include "KeystoneDevice.hpp"
#include "hash_util.hpp"

namespace Keystone {

class SharedMemory {
 public:
  SharedMemory();
  ~SharedMemory();
  virtual rid_t createShm(size_t size);
  virtual void* mapShm(rid_t rid);
  virtual int unmapShm(void* va);
  virtual int changeShm(rid_t rid, unsigned long perm);
  virtual int shareShm(rid_t rid, int eid, unsigned long perm);

 private:
  int fd;
};

}  // namespace Keystone
