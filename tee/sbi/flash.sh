PAYLOAD=./opensbi/build/platform/generic/firmware/fw_payload.bin


sudo sgdisk --clear -g --set-alignment=34 --new=1:34:1048576 --new=2:1048594:0 --typecode=1:af0a --typecode=2:af00 /dev/sdh
sudo dd if=$PAYLOAD of=/dev/sdh1 status=progress oflag=sync bs=1M
