include ../Makefile.def

.SUFFIXES: .cpp

.cc.o:
	$(CC) $(SLIB_FLAGS) $(CORBA_INC) $(XMLCPP_INC) -I.. -c $<

all: CorbaConnection.o

test: all

CorbaConnection.o: CorbaConnection.cc

clean: 
	$(RM) *.o
