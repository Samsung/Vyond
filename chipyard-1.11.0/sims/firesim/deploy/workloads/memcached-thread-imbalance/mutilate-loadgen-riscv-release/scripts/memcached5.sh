#!/bin/bash

memcached -u root -t 5 -c 32768

#ps -mo tid,psr -p $MEMCACHEDPID
#
#MEMCACHEDPID=$(pgrep memcached)
#
#THREADIDSHERE=$(ps -mo tid -p $MEMCACHEDPID | grep [0-9])
#
#COUNTER=0
#PROC2BIN=(0x1 0x2 0x4 0x8)
#
#
#for i in $THREADIDSHERE;
#do
#    taskset -p ${PROC2BIN[COUNTER]} $i
#    COUNTER=$(($COUNTER + 1))
#    COUNTER=$(($COUNTER % 4))
#done
#
#ps -mo tid,psr -p $MEMCACHEDPID
#
