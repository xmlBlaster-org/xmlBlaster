include ../Makefile.def

.SUFFIXES: .cpp

.cc.o:
	$(CC) $(CORBA_INC) $(XMLCPP_INC) -I.. -c $<

all: CorbaConnection.o

test: all

CorbaConnection.o: CorbaConnection.cc

clean: 
	$(RM) *.o
