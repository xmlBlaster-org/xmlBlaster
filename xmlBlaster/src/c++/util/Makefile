include ../Makefile.def

.cpp.o:
	$(CC) $(SLIB_FLAGS) $(CORBA_INC) -I.. -c $<

all: CompatibleCorba.o Log.o

test: all

CompatibleCorba.o: CompatibleCorba.cpp PropertyDef.h

Log.o: Log.cpp PropertyDef.h

clean: 
	$(RM) *.o
