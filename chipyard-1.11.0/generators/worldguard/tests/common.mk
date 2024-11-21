$(info lib_obj $(LIB_OBJ))
$(info test_obj $(TEST_OBJ))
$(info cflag $(CFLAGS))

%.o: $(SRC_DIR)/%.c
	$(GCC) $(CFLAGS) -c $< -o $@


%.o: $(LIB_SRC_DIR)/%.c
	$(GCC) $(CFLAGS) -c $< -o $@

%.o: $(TEST_SRC_DIR)/%.c
	$(GCC) $(CFLAGS) -c $< -o $@

