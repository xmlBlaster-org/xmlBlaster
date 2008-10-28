#-----------------------------------------------------------
# The xmlBlaster.org project
#
# You may use this script to source into your sh, ksh, bash
#
# Example (copy this into your .profile or .bashrc):
#   export JAVA_HOME=/opt/local/jdk
#   export XMLBLASTER_HOME=${HOME}/xmlBlaster
#
#   These are optional:
#   export JacORB_HOME=/opt/local/JacORB
#   export JIKES_HOME=/opt/local/jikes
#   export USE_ANT=true
#
#   . ${XMLBLASTER_HOME}/.bashrc
#
# If you want to use Ant to build xmlBlaster set USE_ANT to true
#
# If you want to access xmlBlaster using cvs, un comment following line:
#   export CVSROOT=:pserver:reader@server.xmlBlaster.org:/opt/cvsroot
#
# Tested on Linux, HPUX and Solaris with sh, ksh and bash.
# Thanks to Heinrich Goetzger
#-----------------------------------------------------------


BLACK_LTGREEN="\033[40;46m"
BLACK_RED="\033[30;41m"
BLACK_YELLOW="\033[40;43m"
ESC="\033[0m"

OS="`uname -s`"

#if [ `basename ${SHELL}` = "bash" ]; then
#   ECHO="echo -e"
#else
#   ECHO="echo"
#fi

if test "`echo -e xxx`" = "xxx"
then
    ECHO="echo -e"
else
    ECHO=echo
fi
WHICH="/usr/bin/which"

if [ "${CLASSPATH}" = "" ] ; then
   CLASSPATH=
   export CLASSPATH
fi


#-------- Checking xmlBlaster --------
if [ "${XMLBLASTER_HOME}" = "" ] ; then
   export XMLBLASTER_HOME=`pwd`
   if [ ! -f ${XMLBLASTER_HOME}/build.xml ] ; then
      export XMLBLASTER_HOME=${HOME}/xmlBlaster
      if [ ! -f ${XMLBLASTER_HOME}/build.xml ] ; then
         ${ECHO} "$BLACK_RED   Please set the environment variable XMLBLASTER_HOME          $ESC"
         ${ECHO} "$BLACK_RED      Example: 'export XMLBLASTER_HOME=`pwd`'   $ESC"
         return
      fi
   fi
fi

if [ ! -d ${XMLBLASTER_HOME} ] ; then
   ${ECHO} "$BLACK_RED   The directory XMLBLASTER_HOME=$XMLBLASTER_HOME doesn't exist   $ESC"
fi

export XMLBLASTER_HOME


if [ -d ${XMLBLASTER_HOME} ]; then

   # OK, know we know where xmlBlaster is installed ...

   ${ECHO} "${BLACK_LTGREEN}Welcome to xmlBlaster.org   ${ESC}"
   ${ECHO} "${BLACK_LTGREEN}   XMLBLASTER_HOME=${XMLBLASTER_HOME}  ${ESC}"

   # Funny stuff for speech synthetizer ...
   CLASSPATH=${XMLBLASTER_HOME}/lib/speech.jar:${CLASSPATH}

   # Doug Lea JDK 1.5 concurrent for JDK 1.4
   CLASSPATH=${XMLBLASTER_HOME}/lib/backport-util-concurrent.jar:${CLASSPATH}

   # classpath for the embedded db ...
   CLASSPATH=${XMLBLASTER_HOME}/lib/hsqldb.jar:${CLASSPATH}
   CLASSPATH=${XMLBLASTER_HOME}/lib/mckoidb.jar:${CLASSPATH}

   # Even funnier things for graphics with xmlBlaster ...
   CLASSPATH=${XMLBLASTER_HOME}/lib/jhotdraw.jar:${CLASSPATH}

   #a2Blaster - authentication and authorisation service
   CLASSPATH=${XMLBLASTER_HOME}/lib/a2Blaster.jar:${CLASSPATH}

   # CLASSPATH=/home/a2blaster/a2Blaster/lib/a2Blaster.jar:${CLASSPATH}
   # CLASSPATH=${CLASSPATH}:${XMLBLASTER_HOME}/lib/ant/xercesImpl.jar
   CLASSPATH=${XMLBLASTER_HOME}/lib/xtdash.jar:${CLASSPATH}
   CLASSPATH=${XMLBLASTER_HOME}/lib/omquery.jar:${CLASSPATH}
   CLASSPATH=${XMLBLASTER_HOME}/lib/junit.jar:${CLASSPATH}
   CLASSPATH=${XMLBLASTER_HOME}/lib/xmlunit.jar:${CLASSPATH}
   CLASSPATH=${XMLBLASTER_HOME}/lib/servlet.jar:${CLASSPATH}
   CLASSPATH=${XMLBLASTER_HOME}/lib/gnu-regexp.jar:${CLASSPATH}
   CLASSPATH=${XMLBLASTER_HOME}/lib/xmlrpc.jar:${CLASSPATH}

   CLASSPATH=${XMLBLASTER_HOME}/lib/jax.jar:${CLASSPATH}

   CLASSPATH=${XMLBLASTER_HOME}/lib/remotecons.jar:${CLASSPATH}

   CLASSPATH=${XMLBLASTER_HOME}/lib/joda-time.jar:${CLASSPATH}

   CLASSPATH=${XMLBLASTER_HOME}/lib/tinySQL.jar:${CLASSPATH}

   CLASSPATH=${XMLBLASTER_HOME}/lib/concurrent.jar:${CLASSPATH}

   CLASSPATH=${XMLBLASTER_HOME}/lib/jzlib.jar:${CLASSPATH}

   CLASSPATH=${XMLBLASTER_HOME}/lib/jaxen.jar:${CLASSPATH}

   #CLASSPATH=${XMLBLASTER_HOME}/lib/cpptasks.jar:${CLASSPATH}

   # Mail support
   CLASSPATH=${XMLBLASTER_HOME}/lib/mail.jar:${CLASSPATH}
   CLASSPATH=${XMLBLASTER_HOME}/lib/activation.jar:${CLASSPATH}

   # EJB connector (J2EE) support:
   CLASSPATH=${XMLBLASTER_HOME}/lib/connector.jar:${CLASSPATH}
   CLASSPATH=${XMLBLASTER_HOME}/lib/jaas.jar:${CLASSPATH}
   CLASSPATH=${XMLBLASTER_HOME}/lib/jta-spec1_0_1.jar:${CLASSPATH}
   CLASSPATH=${XMLBLASTER_HOME}/lib/javax.jms.jar:${CLASSPATH}
   CLASSPATH=${XMLBLASTER_HOME}/lib/ejb2.0.jar:${CLASSPATH}
   # this stuff is only needed for the demo javaclients.svg.batik
   CLASSPATH=${XMLBLASTER_HOME}/lib/batik/batik.jar:${CLASSPATH}
   CLASSPATH=${XMLBLASTER_HOME}/lib/batik/js.jar:${CLASSPATH}

   if [ "${USE_ANT}" = "true" ] ; then
      ${ECHO} "$BLACK_LTGREEN      Using Ant to build xmlBlaster  $ESC"
      CLASSPATH=${XMLBLASTER_HOME}/lib/xmlBlaster.jar:${CLASSPATH}
      CLASSPATH=${XMLBLASTER_HOME}/lib/demo.jar:${CLASSPATH}
      CLASSPATH=${XMLBLASTER_HOME}/lib/testsuite.jar:${CLASSPATH}
   else
      if [ -f ${XMLBLASTER_HOME}/lib/xmlBlaster.jar ]; then
         CLASSPATH=${XMLBLASTER_HOME}/lib/xmlBlaster.jar:${CLASSPATH}
      fi
   fi

   #jdbc
   CLASSPATH=${XMLBLASTER_HOME}/lib/mysql-connector-java-3.0.6-stable-bin.jar:${CLASSPATH}
   CLASSPATH=${XMLBLASTER_HOME}/lib/jdbc7.2dev-1.2.jar:${CLASSPATH}
   CLASSPATH=${XMLBLASTER_HOME}/lib/classes12.zip:${CLASSPATH}
   CLASSPATH=${XMLBLASTER_HOME}/lib/nls_charset12.zip:${CLASSPATH}

   CLASSPATH=${XMLBLASTER_HOME}/lib/ldbc.jar:${CLASSPATH}
   CLASSPATH=${XMLBLASTER_HOME}/lib/nls_charset12.zip:${CLASSPATH}

   if [ -f ${XMLBLASTER_HOME}/lib/postgresql.jar ]; then
      CLASSPATH=${XMLBLASTER_HOME}/lib/postgresql.jar:${CLASSPATH}
   fi

   if [ -f ${XMLBLASTER_HOME}/lib/ojdbc14.jar ]; then
      CLASSPATH=${XMLBLASTER_HOME}/lib/ojdbc14.jar:${CLASSPATH}
   fi

   #CLASSPATH=${CLASSPATH}:${XMLBLASTER_HOME}/lib/soap/jdom.jar
   #CLASSPATH=${CLASSPATH}:${XMLBLASTER_HOME}/lib/soap/saw.jar
   #CLASSPATH=${CLASSPATH}:${XMLBLASTER_HOME}/lib/soap/soap.jar

   # jmx reference implementation
   CLASSPATH=$CLASSPATH:${XMLBLASTER_HOME}/lib/jmxri.jar:${XMLBLASTER_HOME}/lib/jmxtools.jar:${XMLBLASTER_HOME}/lib/mx4j-impl.jar:${XMLBLASTER_HOME}/lib/mx4j-tools.jar 

   # quartz scheduler
   CLASSPATH=$CLASSPATH:${XMLBLASTER_HOME}/lib/quartz-all-1.6.0.jar
   CLASSPATH=$CLASSPATH:${XMLBLASTER_HOME}/lib/commons-collections-3.1.jar

   CLASSPATH=$CLASSPATH:${XMLBLASTER_HOME}/lib/naming-common.jar
   CLASSPATH=$CLASSPATH:${XMLBLASTER_HOME}/lib/naming-java.jar
   CLASSPATH=$CLASSPATH:${XMLBLASTER_HOME}/lib/commons-logging.jar
   CLASSPATH=$CLASSPATH:${XMLBLASTER_HOME}/lib/log4j.jar

   # apache's Base64 encoding (for XmlBlasterApplet) and utils (for token)
   CLASSPATH=$CLASSPATH:${XMLBLASTER_HOME}/lib/commons-codec.jar
   CLASSPATH=$CLASSPATH:${XMLBLASTER_HOME}/lib/commons-lang-2.4.jar

   if [ -d ${XMLBLASTER_HOME}/src/java ]; then
      CLASSPATH=${XMLBLASTER_HOME}/src/java:${CLASSPATH}
   fi
   #if [ -d ${XMLBLASTER_HOME}/build.tmp/classes ]; then
      CLASSPATH=${XMLBLASTER_HOME}/build.tmp/classes:${CLASSPATH}
   #fi
   if [ -d ${XMLBLASTER_HOME}/demo ]; then
      CLASSPATH=${XMLBLASTER_HOME}/demo:${CLASSPATH}
   fi
   if [ -d ${XMLBLASTER_HOME}/testsuite ]; then
      CLASSPATH=${XMLBLASTER_HOME}:${CLASSPATH}
      CLASSPATH=${XMLBLASTER_HOME}/testsuite/src/java/org/xmlBlaster:${CLASSPATH}
   fi

   CLASSPATH=${XMLBLASTER_HOME}/lib/java_cup.jar:${CLASSPATH}

   # CORBA
   CLASSPATH=$CLASSPATH:${XMLBLASTER_HOME}/lib/jacorb/avalon-framework-4.1.5.jar
   CLASSPATH=$CLASSPATH:${XMLBLASTER_HOME}/lib/jacorb/logkit-1.2.jar
   CLASSPATH=$CLASSPATH:${XMLBLASTER_HOME}/lib/jacorb/jacorb.jar

   export CLASSPATH

   PATH=${PATH}:${XMLBLASTER_HOME}/bin:${XMLBLASTER_HOME}/testsuite/src/c/bin:${XMLBLASTER_HOME}/testsuite/src/c++/bin:${XMLBLASTER_HOME}/demo/c/socket/bin:${XMLBLASTER_HOME}/demo/c++/bin
   export PATH

   # Linux
   LD_LIBRARY_PATH=$LD_LIBRARY_PATH:${XMLBLASTER_HOME}/lib
   export LD_LIBRARY_PATH

   # HPUX
   SHLIB_PATH=$SHLIB_PATH:${XMLBLASTER_HOME}/lib
   export SHLIB_PATH

   # AIX
   LIBPATH=$LIBPATH:${XMLBLASTER_HOME}/lib
   export LIBPATH

   # Mac OSX
   DYLD_LIBRARY_PATH=$DYLD_LIBRARY_PATH:${XMLBLASTER_HOME}/lib
   export DYLD_LIBRARY_PATH

   alias cdx='cd $XMLBLASTER_HOME'
   alias cdxs='cd $XMLBLASTER_HOME/src'
   alias cdxi='cd $XMLBLASTER_HOME/src/idl'
   alias cdxj='cd $XMLBLASTER_HOME/src/java/org/xmlBlaster'
   alias cdxd='cd $XMLBLASTER_HOME/demo'
   alias cdxdj='cd $XMLBLASTER_HOME/demo/javaclients'
   alias cdxt='cd $XMLBLASTER_HOME/testsuite/src'
   alias cdxr='cd $XMLBLASTER_HOME/doc/requirements'
   alias cdxtj='cd $XMLBLASTER_HOME/testsuite/src/java/org/xmlBlaster/test'
   alias build='build.sh'

   if [ ! -f ${HOME}/xmlBlaster.properties ]; then
      cp ${XMLBLASTER_HOME}/config/xmlBlaster.properties.template ${HOME}/xmlBlaster.properties
      ${ECHO} "$BLACK_RED   Please edit and customize ${HOME}/xmlBlaster.properties   $ESC"
   fi

   if [ ! -f ${HOME}/xmlBlasterPlugins.xml ]; then
      cp ${XMLBLASTER_HOME}/config/xmlBlasterPlugins.xml.template ${HOME}/xmlBlasterPlugins.xml
      ${ECHO} "$BLACK_LTGREEN   Copied ${HOME}/xmlBlasterPlugins.xml$ESC"
   fi

   # Add to you MANPATH and try 'man XmlBlasterAccessUnparsed'
   export MANPATH=:$XMLBLASTER_HOME/doc/doxygen/c/man:$XMLBLASTER_HOME/doc/doxygen/c++/man

else
   ${ECHO} "$BLACK_RED  Sorry, xmlBlaster.org not loaded, set your environment manually   $ESC"
   return 1
fi


#-------- Checking JDK version -
if [ "${JAVA_HOME}" != "" ] ; then
   if [ -d "${JAVA_HOME}" ] ; then
      PATH="${JAVA_HOME}/bin":${PATH}
      export PATH
   else
      ${ECHO} "$BLACK_RED   The directory JAVA_HOME=$JAVA_HOME doesn't exist   $ESC"
   fi
else
   ${ECHO} "$BLACK_RED      NOTE: You need JDK 1.4 or higher to compile xmlBlaster   $ESC"
   ${ECHO} "$BLACK_RED            Please set JAVA_HOME                               $ESC"
   ${ECHO} "$BLACK_RED            Example: 'export JAVA_HOME=/opt/local/jdk'         $ESC"
   return 1
fi

return 0

