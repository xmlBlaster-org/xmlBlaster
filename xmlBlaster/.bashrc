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


JUTILS_HOME=${XMLBLASTER_HOME}
export JUTILS_HOME

if [ -d ${XMLBLASTER_HOME} ]; then

   # OK, know we know where xmlBlaster is installed ...

   ${ECHO} "${BLACK_LTGREEN}Welcome to xmlBlaster.org   ${ESC}"
   ${ECHO} "${BLACK_LTGREEN}   XMLBLASTER_HOME=${XMLBLASTER_HOME}  ${ESC}"

   # Funny stuff for speech synthetizer ...
   CLASSPATH=${XMLBLASTER_HOME}/lib/speech.jar:${CLASSPATH}

   # classpath for the embedded db ...
   CLASSPATH=${XMLBLASTER_HOME}/lib/hsqldb.jar:${CLASSPATH}
   CLASSPATH=${XMLBLASTER_HOME}/lib/mckoidb.jar:${CLASSPATH}

   # Even funnier things for graphics with xmlBlaster ...
   CLASSPATH=${XMLBLASTER_HOME}/lib/jhotdraw.jar:${CLASSPATH}

   #a2Blaster - authentication and authorisation service
   CLASSPATH=${XMLBLASTER_HOME}/lib/a2Blaster.jar:${CLASSPATH}

   #CLASSPATH=/home/a2blaster/a2Blaster/lib/a2Blaster.jar:${CLASSPATH}
   CLASSPATH=${CLASSPATH}:${XMLBLASTER_HOME}/lib/ant/xercesImpl.jar
   # jutils.jar is now included in xmlBlaster.jar
   #if [ -f ${XMLBLASTER_HOME}/lib/jutils.jar ]; then
      CLASSPATH=${XMLBLASTER_HOME}/lib/jutils.jar:${CLASSPATH}
   #fi
   CLASSPATH=${XMLBLASTER_HOME}/lib/xtdash.jar:${CLASSPATH}
   CLASSPATH=${XMLBLASTER_HOME}/lib/omquery.jar:${CLASSPATH}
   CLASSPATH=${XMLBLASTER_HOME}/lib/junit.jar:${CLASSPATH}
   CLASSPATH=${XMLBLASTER_HOME}/lib/xmlunit.jar:${CLASSPATH}
   CLASSPATH=${XMLBLASTER_HOME}/lib/servlet.jar:${CLASSPATH}
   CLASSPATH=${XMLBLASTER_HOME}/lib/gnu-regexp.jar:${CLASSPATH}
   CLASSPATH=${XMLBLASTER_HOME}/lib/xmlrpc.jar:${CLASSPATH}

   CLASSPATH=${XMLBLASTER_HOME}/lib/jax.jar:${CLASSPATH}

   CLASSPATH=${XMLBLASTER_HOME}/lib/remotecons.jar:${CLASSPATH}

   CLASSPATH=${XMLBLASTER_HOME}/lib/tinySQL.jar:${CLASSPATH}

   CLASSPATH=${XMLBLASTER_HOME}/lib/concurrent.jar:${CLASSPATH}

   CLASSPATH=${XMLBLASTER_HOME}/lib/jzlib.jar:${CLASSPATH}

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
   # JNDI, only needed for JDK 1.2, not for JDK 1.3
   CLASSPATH=${CLASSPATH}:${XMLBLASTER_HOME}/lib/jndi-jdk1_3_1.jar
   CLASSPATH=${XMLBLASTER_HOME}/lib/parser.jar:${CLASSPATH}
   CLASSPATH=${XMLBLASTER_HOME}/lib/jaxp.jar:${CLASSPATH}
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

   #CLASSPATH=${CLASSPATH}:${XMLBLASTER_HOME}/lib/soap/jdom.jar
   #CLASSPATH=${CLASSPATH}:${XMLBLASTER_HOME}/lib/soap/saw.jar
   #CLASSPATH=${CLASSPATH}:${XMLBLASTER_HOME}/lib/soap/soap.jar

   # jmx reference implementation
   CLASSPATH=$CLASSPATH:${XMLBLASTER_HOME}/lib/jmxri.jar:${XMLBLASTER_HOME}/lib/jmxtools.jar

   # apache's embedded jndi naming service
   CLASSPATH=$CLASSPATH:${XMLBLASTER_HOME}/lib/naming-common.jar
   CLASSPATH=$CLASSPATH:${XMLBLASTER_HOME}/lib/naming-java.jar
   CLASSPATH=$CLASSPATH:${XMLBLASTER_HOME}/lib/commons-logging.jar

   # apache's Base64 encoding (for XmlBlasterApplet) and utils (for token)
   CLASSPATH=$CLASSPATH:${XMLBLASTER_HOME}/lib/commons-codec.jar
   CLASSPATH=$CLASSPATH:${XMLBLASTER_HOME}/lib/commons-lang-2.0.jar

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

   LD_LIBRARY_PATH=$LD_LIBRARY_PATH:${XMLBLASTER_HOME}/lib
   export LD_LIBRARY_PATH

   alias cdj='cd $JUTILS_HOME/src/java/org/jutils'
   alias cdx='cd $XMLBLASTER_HOME'
   alias cdxs='cd $XMLBLASTER_HOME/src'
   alias cdxi='cd $XMLBLASTER_HOME/src/idl'
   alias cdxj='cd $XMLBLASTER_HOME/src/java/org/xmlBlaster'
   alias cdxu='cd $XMLBLASTER_HOME/src/java/org/jutils'
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
if [ "${JAVA_HOME}" = "" ] ; then
   # xmlBlaster release < 0.78 used JDK_HOME, try this variable:
   JAVA_HOME=$JDK_HOME
   export JAVA_HOME
fi
if [ "${JAVA_HOME}" != "" ] ; then
   if [ -d ${JAVA_HOME} ] ; then
      if [ -f ${JAVA_HOME}/lib/classes.zip ]; then
         # JDK 1.1.x
         JDK_1_1=true
         export JDK_1_1
         CLASSPATH=${XMLBLASTER_HOME}/lib/collections.jar:${CLASSPATH}
      #else
         ## JDK 1.2
         #ORB_PROPS=${JAVA_HOME}/jre/lib/orb.properties
         #if [ ! -f ${ORB_PROPS} ]; then
         #   cp ${XMLBLASTER_HOME}/config/orb.properties ${ORB_PROPS}
         #      RESULT=$?
         #      if [ ${RESULT} = "0" ]; then
         #      ${ECHO} "$BLACK_YELLOW   Created ${ORB_PROPS} to switch off default JDK-ORB$ESC"
         #      else
         #      ${ECHO} "$BLACK_RED   Could not copy ${XMLBLASTER_HOME}/config/orb.properties to ${ORB_PROPS} (to switch off default JDK-ORB). Missing permissions?$ESC"
         #      fi
         #fi
         ## If copy failed (missing permissions?)
         ## if [ $? -ne 0 ] ;  then
         #if [ ! -f ${ORB_PROPS} ]; then
         #   CLASSPATH=${JAVA_HOME}/jre/lib/rt.jar:${CLASSPATH}
         #   export CLASSPATH
         #fi
      fi
      PATH=${JAVA_HOME}/bin:${PATH}
      export PATH
   else
      ${ECHO} "$BLACK_RED   The directory JAVA_HOME=$JAVA_HOME doesn't exist   $ESC"
   fi
else
   ${ECHO} "$BLACK_RED      NOTE: You need JDK 1.2 or 1.3 to compile xmlBlaster      $ESC"
   ${ECHO} "$BLACK_RED            and your CLASSPATH setting needs at least          $ESC"
   ${ECHO} "$BLACK_RED               export CLASSPATH=\${JAVA_HOME}/jre/lib/rt.jar    $ESC"
   ${ECHO} "$BLACK_RED            Or set JAVA_HOME, and we will do the rest for you  $ESC"
   ${ECHO} "$BLACK_RED               Example: 'export JAVA_HOME=/opt/local/jdk'      $ESC"
   return 1
fi

# tinySQL, a simple DBase JDBC driver.
# Conflicts with JacORBs idl.jar because both use java_cup
# (tinySQL has modified parser.java)
#CLASSPATH=${XMLBLASTER_HOME}/lib/tinySQL.jar:${CLASSPATH}


#-------- Checking jikes version -
# use jikes 1.06 or better
if [ "${JIKES_HOME}" != "" ] ; then
   if [ -d ${JIKES_HOME} ] ; then
      PATH=${PATH}:${JIKES_HOME}
      export PATH
      if [ "${JDK_1_1}" != "" ] ; then
         JIKESPATH=${CLASSPATH}
         export JIKESPATH
      else
         JIKESPATH=${CLASSPATH}:${JAVA_HOME}/jre/lib/rt.jar
         export JIKESPATH
      fi
      ${ECHO} "$BLACK_LTGREEN   JIKES_HOME     =${JIKES_HOME}  $ESC"
      ${ECHO} "$BLACK_LTGREEN   Enhance \$JIKESPATH if you enhance your CLASSPATH$ESC"
   else
      ${ECHO} "$BLACK_RED   The directory JIKES_HOME=$JIKES_HOME doesn't exist   $ESC"
   fi
fi


#-------- Running with TowerJ native compiler -
# See xmlBlaster/bin/Project.tj
# Replace xtdash.jar and jacorb.jar with the original ones.
# Invoke:
#   cd $XMLBLASTER_HOME/bin
#   tj -b-jdk 2 -verbose  -project $XMLBLASTER_HOME/bin/Project.tj
#  Run testsuite, invoke again
#   tj -b-jdk 2 -verbose  -project $XMLBLASTER_HOME/bin/Project.tj
#  until Main-xy.tjp shows no Java classes anymore.
#  Other 'final' options (increase 4%):
#   tj -b-jdk 2  -nofeedback -mode optimize -O-omit-checks
#   tj -b-jdk 2  -nofeedback -b-disable-tjlib -mode optimize -O-omit-checks
#   ( -O-inline-threshold 100 fails to compile)
#   tj -b-jdk 2  -O-closed
TOWERJ=/opt/TowerJ
export TOWERJ
if [ "${TOWERJ}" != "" ] ; then
   if [ -d ${TOWERJ} ] ; then
      TOWERJ_JAVA_HOME=${JAVA_HOME}
      export TOWERJ_JAVA_HOME
      PATH=${PATH}:${TOWERJ}/bin/x86-linux
      export PATH
      LD_LIBRARY_PATH=$LD_LIBRARY_PATH:${TOWERJ}/lib/x86-linux
      export LD_LIBRARY_PATH
      #TOWERJ_TJLIB_PATH= ???
      #export TOWERJ_TJLIB_PATH
      ${ECHO} "$BLACK_LTGREEN      Using TOWERJ=${TOWERJ}  $ESC"
   fi
fi


return 0

