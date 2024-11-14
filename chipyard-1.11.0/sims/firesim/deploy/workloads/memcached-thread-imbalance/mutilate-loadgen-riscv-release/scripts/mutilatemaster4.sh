#!/bin/bash

# pass in expected QPS as first arg
mutilate3 -s 172.16.0.2 --noload -T 4 -Q 1000 -C 4 -c 4 -q $1 -t 1 -i exponential:1 -a 172.16.0.3 -a 172.16.0.4 -a 172.16.0.5 -a 172.16.0.6 -a 172.16.0.7 -a 172.16.0.8
