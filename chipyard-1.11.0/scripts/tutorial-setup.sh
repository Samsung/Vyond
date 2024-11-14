#!/bin/bash

set -ex

RDIR=$(git rev-parse --show-toplevel)/chipyard-1.11.0

cd $RDIR

git rm generators/chipyard/src/main/scala/config/RocketSha3Configs.scala
git rm -rf generators/sha3

for p in scripts/tutorial-patches/*.patch
do
    echo "Applying tutorial patch $p"
    git apply $p
done
