all:
        idl2perl -o . -imp ${XMLBLASTER_HOME}/src/java/org/xmlBlaster/protocol/corba/xmlBlaster.idl


clean:
        rm -rf serverIdl authenticateIdl
        rm -f clientIdl/BlasterCallback.pm clientIdl/BlasterCallback_skel.pm clientIdl/BlasterCallback_types.pm
        rm -f *.pm

