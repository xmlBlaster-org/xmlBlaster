export IDL_OUTDIR="--output-dir"
export IDL2JAVA="${ORBACUS_HOME}/bin/jidl --tie --package org.xmlBlaster.protocol.corba"
export CLASSPATH=${XMLBLASTER_HOME}/lib/miniJacorb.jar:${CLASSPATH}
export JAVA_WRAP=${XMLBLASTER_HOME}/config/java_orbacus.sh
#
# change this if you want different settings for java and c++
#
export ORBACUS_CONFIG=${XMLBLASTER_HOME}/config/orbacus.cfg