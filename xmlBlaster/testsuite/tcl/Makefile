# The Tclmico environment should be installed
# before you can use this Makefile, e.g.:

# export TCLCORBACPP_HOME=/www/tclmico-0.5c
# export PATH            := ${TCLCORBACPP_HOME}:$(PATH)
# export LD_LIBRARY_PATH := ${TCLCORBACPP_HOME}:$(LD_LIBRARY_PATH)	LINUX,Sloaris
# export LIBPATH         := ${TCLCORBACPP_HOME}:$(LIBPATH)          AIX
# export SHLIB_PATH      := ${TCLCORBACPP_HOME}:$(SHLIB_PATH)       HPUX

all: BlasterCallback xmlBlaster.tcl

.SUFFIXES :
.SUFFIXES : .tcl .cc .h .o .idl

.cc.o:
	mico-c++ -c $<

clean:
	rm -f xmlBlaster.cc xmlBlaster.h *.o core xmlBlaster.tcl BlasterCallback *~

BlasterCallback: xmlBlaster.h xmlBlaster.o BlasterCallback.o
	mico-ld -o BlasterCallback xmlBlaster.o BlasterCallback.o -lmico$(MICOVERSION)

xmlBlaster.h xmlBlaster.cc : ${XMLBLASTER_HOME}/src/idl/xmlBlaster.idl
	idl --poa --no-boa ${XMLBLASTER_HOME}/src/idl/xmlBlaster.idl

xmlBlaster.tcl:	${XMLBLASTER_HOME}/src/idl/xmlBlaster.idl
	idl2tcl $<
