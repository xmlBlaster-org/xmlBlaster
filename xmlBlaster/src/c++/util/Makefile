include ../Makefile.def

.cc.o:
	$(CC) $(CORBA_INC) -I.. -c $<

all: CompatibleCorba.o Log.o

test: all

PropertyDef.h:
	java org.xmlBlaster.util.cpp.SetCCProperties > PropertyDef.h

CompatibleCorba.o: CompatibleCorba.cc PropertyDef.h

Log.o: Log.cc PropertyDef.h

clean: 
	$(RM) *.o PropertyDef.h
