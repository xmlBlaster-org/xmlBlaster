export IDL_OUTDIR="--output-dir"
export IDL2JAVA="${ORBACUS_HOME}/bin/jidl --tie --package org.xmlBlaster.protocol.corba"
export CLASSPATH=${XMLBLASTER_HOME}/lib/miniJacorb.jar:${CLASSPATH}
export JAVA_WRAP=${XMLBLASTER_HOME}/config/java_orbacus.sh
#
# change this if you want different settings for java and c++
#
export ORBACUS_CONFIG=${XMLBLASTER_HOME}/config/orbacus.cfg
#export CLASSPATH=$ORBACUS_HOME/lib/OB.jar:$ORBACUS_HOME/lib/OBNaming.jar:$JDK_HOME/jre/lib/rt.jar:$(CLASSPATH):.
export CLASSPATH=${ORBACUS_HOME}/lib/OB.jar:$ORBACUS_HOME/lib/OBNaming.jar:${CLASSPATH}:.

export CORBA_CPP="orbacus"
export CORBACPP_HOME=${ORBACUS_HOME}
export CORBACPP_VER=${ORBACUS_VER}
if [ ${CORBACPP_VER:=""} = "" ] ; then 
  export CORBACPP_VER="4.1"
fi


