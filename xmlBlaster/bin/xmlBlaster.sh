#!/bin/sh
#
# Starting xmlBlaster server
#
# USAGE:
#   XMLBLASTER_HOME=/opt/xmlBlaster
#   xmlBlaster.sh
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
   # resolve links - $0 may be a softlink
   PRG="$0"

   while [ -h "$PRG" ] ; do
     ls=`ls -ld "$PRG"`
     link=`expr "$ls" : '.*-> \(.*\)$'`
     if expr "$link" : '.*/.*' > /dev/null; then
       PRG="$link"
     else
       PRG=`dirname "$PRG"`/"$link"
     fi
   done

   PRGDIR=`dirname "$PRG"`
   XMLBLASTER_HOME=${PRGDIR}/..
    
   if [ ${XMLBLASTER_HOME:=""} = "" ] ; then
      ${ECHO} "$BLACK_RED   Please set the environment variable XMLBLASTER_HOME          $ESC"
      ${ECHO} "$BLACK_RED      Example: 'export XMLBLASTER_HOME=`pwd`'   $ESC"
      exit 1
   fi

   echo "Using XMLBLASTER_HOME=$XMLBLASTER_HOME"

   if [ ! -d ${XMLBLASTER_HOME} ] ; then
      ${ECHO} "$BLACK_RED   Please set the environment variable XMLBLASTER_HOME          $ESC"
      ${ECHO} "$BLACK_RED      Example: 'export XMLBLASTER_HOME=`pwd`'   $ESC"
      exit 1
   fi
fi

if [ ! -d ${XMLBLASTER_HOME} ] ; then
   ${ECHO} "$BLACK_RED   The directory XMLBLASTER_HOME=$XMLBLASTER_HOME doesn't exist   $ESC"
   exit 1
fi

java -Xms18M -Xmx64M \
     -Djava.security.policy=${XMLBLASTER_HOME}/config/xmlBlaster.policy \
     -jar ${XMLBLASTER_HOME}/lib/xmlBlaster.jar "$@"

# Redirect:
#  > ${STDOUT_LOG} 2>&1 &

#. ${XMLBLASTER_HOME}/.bashrc

# NOTE: JVM settings force using JacORB CORBA libs and naming service
#       instead of those from JDK 1.2 or 1.3

#${ECHO} "${BLACK_LTGREEN}Starting xmlBlaster server ...$ESC"
#java -Xbootclasspath:${JacORB_HOME}/lib/jacorb.jar:${JAVA_HOME}/jre/lib/rt.jar:$CLASSPATH -Dorg.omg.CORBA.ORBClass=org.jacorb.orb.ORB -Dorg.omg.CORBA.ORBSingletonClass=org.jacorb.orb.ORBSingleton -Djava.security.policy=${XMLBLASTER_HOME}/config/xmlBlaster.policy org.xmlBlaster.Main -plugin/ior/iorFile /tmp/xmlBlaster.ior "$@"

# Debugging with www.karmira.com
#java -Xdebug -Xnoagent -Djava.compiler=NONE -Xrunjdwp:transport=dt_socket,server=y,suspend=n,address=8000 org.xmlBlaster.Main -dump true
