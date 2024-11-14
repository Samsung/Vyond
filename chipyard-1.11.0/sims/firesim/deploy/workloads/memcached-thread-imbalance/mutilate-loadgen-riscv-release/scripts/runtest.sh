#!/bin/bash

echo "running mutilatemaster$1.sh"

for i in {3200..163200..3200}
do
    echo "running $i"
    mutilatemaster$1.sh $i
done
