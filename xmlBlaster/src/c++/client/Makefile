include ../Makefile.def

.SUFFIXES: .cpp

.cpp.o:
	$(CC) $(SLIB_FLAGS) $(CORBA_INC) $(XMLCPP_INC) -I.. -c $<

all: CorbaConnection.o

test: all

CorbaConnection.o: CorbaConnection.cpp

clean: 
	$(RM) *.o
