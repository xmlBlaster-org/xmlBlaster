#!/bin/sh
#
# Starting xmlBlaster server
#
# USAGE:
#   XMLBLASTER_HOME=/opt/xmlBlaster
#   JAVA_HOME=/opt/jdk
#   xmlBlaster.sh
#
# Starting the java interpreter, using JacORB omg libs instead of those
# from JDK 1.2
#
#   Basically, you don't need this script anymore, calling java or javac
#   directly is enough - if you are using JDK 1.1 or, for JDK 1.2,
#   installed the orb.properties file correctly. If this was not
#   possible, you need to set and pass the system properties 
#   org.omg.CORBA.ORBClass and org.omg.CORBA.ORBSingletonClass on 
#   the command line. For convenience, you can use this script
#
#   Copy orb.properties to JAVA_HOME/lib or edit the existing
#   orb.properties file. Then you don't need this script.
#
# NOTE: If you want to use the naming service, you need to use jaco,
#       which sets the -Xbootclasspath
#       since otherwise the wrong NamingContext from JDK is used.
#
# NOTE: If you use the RMI server, you should adjust the security policy
#       file ${XMLBLASTER_HOME}/config/xmlBlaster.policy

BLACK_LTGREEN="\033[40;46m"
BLACK_RED="\033[30;41m"
BLACK_YELLOW="\033[40;43m"
ESC="\033[0m"
if test "`echo -e xxx`" = "xxx"
then
    ECHO="echo -e"
else
    ECHO=echo
fi


if [ ${XMLBLASTER_HOME:=""} = "" ] ; then
   ${ECHO} "$BLACK_RED   Please set the environment variable XMLBLASTER_HOME          $ESC"
   ${ECHO} "$BLACK_RED      Example: 'export XMLBLASTER_HOME=`pwd`'   $ESC"
   exit 1
fi

if [ ! -d ${XMLBLASTER_HOME} ] ; then
   ${ECHO} "$BLACK_RED   The directory XMLBLASTER_HOME=$XMLBLASTER_HOME doesn't exist   $ESC"
   exit 1
fi

. ${XMLBLASTER_HOME}/.bashrc


${ECHO} "${BLACK_LTGREEN}Starting xmlBlaster server ...$ESC"
java -Xbootclasspath:${JacORB_HOME}/lib/jacorb.jar:${JAVA_HOME}/jre/lib/rt.jar:$CLASSPATH -Dorg.omg.CORBA.ORBClass=jacorb.orb.ORB -Dorg.omg.CORBA.ORBSingletonClass=jacorb.orb.ORBSingleton -Djava.security.policy=${XMLBLASTER_HOME}/config/xmlBlaster.policy org.xmlBlaster.Main "$@"

