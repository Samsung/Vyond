#!/bin/bash

reassemble() {
  local base_name="$1"    # base name of the split parts
  local output_file="$2"  # desired output file name

  # Check if all parts are present
  if ls "${base_name}"* 1> /dev/null 2>&1; then
    echo "Reassembling parts into ${output_file}..."
    cat "${base_name}"* > "${output_file}"
  else
    echo "Error: '${base_name}' not found!"
    exit 1
  fi
}

decompress() {
  local input_file="$1"

  if ls "${input_file}" 1> /dev/null 2>&1; then
    echo "Decompressing ${input_file}..."
    gunzip -f ${input_file}
  else
    echo "Error: '${input_file}' not found!"
    exit 1
  fi
}

if [ ! -e "qemu-system-riscv64" ]; then
  reassemble "qemu-system-riscv64-part-" "qemu-system-riscv64.gz"
  decompress  "qemu-system-riscv64.gz"
  chmod a+x qemu-system-riscv64
fi

if [ ! -e "rootfs.ext2" ]; then
  decompress "rootfs.ext2.gz"
fi

