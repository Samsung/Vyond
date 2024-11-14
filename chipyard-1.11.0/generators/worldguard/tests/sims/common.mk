
SRC_DIR = ./src
COMMON_SRC_DIR = ../common/lib
COMMON_SRC = $(wildcard $(COMMON_SRC_DIR)/*.c)
COMMON_OBJ = $(patsubst $(COMMON_SRC_DIR)/%.c, %.o, $(COMMON_SRC))
$(info common_src_dir $(COMMON_SRC_DIR))
$(info common_src  $(COMMON_SRC))
$(info common_obj $(COMMON_OBJ))
$(info cflag $(CFLAGS))

%.o: $(COMMON_SRC_DIR)/%.c
	$(GCC) $(CFLAGS) -c $< -o $@

