#!/bin/bash

# pass in expected QPS as first arg
mutilate3 -s 172.16.0.10 --noload -T 4 -Q 1000 -C 4 -c 4 -q $1 -t 1 -i exponential:1 -a 172.16.0.11 -a 172.16.0.12 -a 172.16.0.13 -a 172.16.0.14 -a 172.16.0.15 -a 172.16.0.16
