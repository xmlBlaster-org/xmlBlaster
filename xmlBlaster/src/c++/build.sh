#!/bin/sh

#Set this in your environment:
#JAVA_HOME=/usr/jdk1.2.2
if [ ${JAVA_HOME:=""} = "" ] ; then
   echo "ERROR: Please set the environment variable JAVA_HOME"
   exit
fi
MAIN_DIR=${XMLBLASTER_HOME}
CLASSPATH=${MAIN_DIR}/lib/ant.jar:${MAIN_DIR}/lib/xtdash.jar:${MAIN_DIR}/lib/idl.jar:${MAIN_DIR}/lib/jacorb.jar:${MAIN_DIR}/lib/omquery.jar:${MAIN_DIR}/lib/parser.jar:${MAIN_DIR}/lib/servlet-2.0.jar:${MAIN_DIR}/lib/test.jar:$JAVA_HOME/lib/tools.jar:.


#$JAVA_HOME/bin/java -Dant.home=$XMLBLASTER_HOME -Dant.classpath=$CLASSPATH -classpath $CLASSPATH org.apache.tools.ant.Main $@

$JAVA_HOME/bin/java -Dsubtarget=$1 -Dsrc.dir="." -Dcorba.home=${CORBACPP_HOME} -Dcorbacpp.ver=${CORBACPP_VER} -Dxmlcpp.home=${XMLCPP_HOME} -Dxmlcpp.ver=${XMLCPP_VER}  -Dant.home=$XMLBLASTER_HOME -DxmlBlaster.home=$XMLBLASTER_HOME -Dant.classpath=$CLASSPATH -classpath $CLASSPATH:classes org.apache.tools.ant.Main -buildfile ${CORBA_CPP}.xml


