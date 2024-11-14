# Chipyard version 1.11.0 for WorldGuard

Chipyard is slightly customized to run WorldGuard. The original chipyard version is [1.11.0](https://github.com/ucb-bar/chipyard/tree/1.11.0). 
Chipyard is tested using Ubuntu 22.04.3.

## Initial Repository Setup

### 1. Conda

#### Install Conda
Please refer to [Conda Installation](https://github.com/conda-forge/miniforge/#download).

#### Install [libmamba](https://www.anaconda.com/blog/a-faster-conda-for-a-growing-community) for faster dependency solving.
```sh
conda install -n base conda-libmamba-solver
conda config --set solver libmamba
```

#### Install `conda-lock` to `base` conda environment
```sh
conda install -n base conda-lock==1.4.0
conda activate base
```

### 2. Setting Chipyard
```sh
./build-setup.sh -s 6 -s 7 -s 8 -s 9
```

## Running WorldGuard on the Chipyard
Please refer to WorldGuard's [README](chipyard-1.11.0/generators/worldguard/README.md)
