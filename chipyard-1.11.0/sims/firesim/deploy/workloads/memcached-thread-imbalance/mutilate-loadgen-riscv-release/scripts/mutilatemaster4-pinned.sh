#!/bin/bash

# pass in expected QPS as first arg
mutilate3 -s 172.16.0.18 --noload -T 4 -Q 1000 -C 4 -c 4 -q $1 -t 1 -i exponential:1 -a 172.16.0.19 -a 172.16.0.20 -a 172.16.0.21 -a 172.16.0.22 -a 172.16.0.23 -a 172.16.0.24
