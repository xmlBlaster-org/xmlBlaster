#!/bin/sh
#
# Starting xmlBlaster server
#
# USAGE:
#   XMLBLASTER_HOME=/opt/xmlBlaster
#   JAVA_HOME=/opt/jdk
#   xmlBlaster.sh
#
# NOTE: JVM settings force using JacORB CORBA libs and naming service
#       instead of those from JDK 1.2 or 1.3
#
# NOTE: If you use the RMI server, you should adjust the security policy
#       file ${XMLBLASTER_HOME}/config/xmlBlaster.policy
#
# NOTE: Just edit this script, if you need to increase JVM memory or
#       other settings (see last line)!

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

# Debugging with www.karmira.com
#java -Xdebug -Xnoagent -Djava.compiler=NONE -Xrunjdwp:transport=dt_socket,server=y,suspend=n,address=8000 org.xmlBlaster.Main -dump true
