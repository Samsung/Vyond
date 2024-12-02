export VYOND_ENV_SETUP=YES

# Source environmental variables for chipyard
cd chipyard-1.11.0
source env.sh
cd ..

# Unpack prebuild images
cd prebuilt
./unpack.sh
cd ..


# Set cross compiler for building fw_payload
path_to_gcc=`which riscv64-unknown-elf-gcc`
export CROSS_COMPILE=$(echo "$path_to_gcc" | sed 's/\(.*\)gcc/\1/')
