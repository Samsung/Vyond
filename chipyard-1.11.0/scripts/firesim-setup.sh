#!/usr/bin/env bash

# Sets up FireSim for use as a library within Chipyard

set -e
set -o pipefail

RDIR=$(git rev-parse --show-toplevel)
RDIR=$RDIR/chipyard-1.11.0

cd "$RDIR"

# Reenable the FireSim submodule
git config --unset submodule.sims/firesim.update || true
pushd sims/firesim
./build-setup.sh "$@" --library --skip-validate
popd
