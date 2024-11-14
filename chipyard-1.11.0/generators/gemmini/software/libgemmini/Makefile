ifndef RISCV
$(error RISCV is unset)
else
$(info Running with RISCV=$(RISCV))
endif

PREFIX ?= $RISCV/

default: libgemmini.so

libgemmini.so: gemmini.cc
	g++ -L $(RISCV)/lib -Wl,-rpath,$(RISCV)/lib -shared -o $@ -std=c++17 -I $(RISCV)/include -fPIC -O3 $^ -lriscv

.PHONY: install
install: libgemmini.so
	cp libgemmini.so $(RISCV)/lib

clean:
	rm -rf *.o *.so
