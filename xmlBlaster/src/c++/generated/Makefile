include ../Makefile.def

.SUFFIXES: .cpp

.cpp.o::
	$(CC) $(CORBA_INC) -c $<
.cc.o::
	$(CC) $(CORBA_INC) -c $<

all: $(CORBA_OBJ)

test: all

$(CORBA_SRC):$(IDL_DIR)/xmlBlaster.idl
	$(IDL_CMD) $(IDL_FLAG) $(IDL_DIR)/xmlBlaster.idl

$(CORBA_OBJ): $(CORBA_SRC)

clean::
	$(RM) *.o *.cc *.cpp *.h
