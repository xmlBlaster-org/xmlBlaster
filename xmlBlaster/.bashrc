#-----------------------------------------------------------
# The xmlBlaster.org project
#
# You may use this script to source into your sh, ksh, bash
#
# Example (copy this into your .profile or .bashrc):
#   export JDK_HOME=/usr/local/jdk
#   export XMLBLASTER_HOME=${HOME}/xmlBlaster
#
#   These are optional:
#   export JacORB_HOME=/usr/local/JacORB
#   export MICO_HOME=/usr/local/mico
#   export JIKES_HOME=/usr/local/jikes
#   export USE_ANT=true
#
#   . ${XMLBLASTER_HOME}/.bashrc
#
# If you want to use Ant to build xmlBlaster set USE_ANT to true
#
# If you want to access xmlBlaster using cvs, un comment following line:
#   export CVSROOT=:pserver:reader@server.xmlBlaster.org:/opt/cvsroot
#
# Tested on Linux, HPUX and Solaris with sh, ksh and bash
# Thanks to Heinrich Goetzger
# $Revision: 1.43 $
#-----------------------------------------------------------


BLACK_LTGREEN="\033[40;46m"
BLACK_RED="\033[30;41m"
ESC="\033[0m"

OS="`uname -s`"

if [ `basename ${SHELL}` = "bash" ]; then
   ECHO="echo -e"
else
   ECHO="echo"
fi


#-------- Checking xmlBlaster --------
if [ ${XMLBLASTER_HOME:=""} = "" ] ; then
   ${ECHO} "$BLACK_RED   Please set the environment variable XMLBLASTER_HOME          $ESC"
   ${ECHO} "$BLACK_RED      Example: 'export XMLBLASTER_HOME=`pwd`'   $ESC"
   return
fi

if [ ! -d ${XMLBLASTER_HOME} ] ; then
   ${ECHO} "$BLACK_RED   The directory XMLBLASTER_HOME=$XMLBLASTER_HOME doesn't exist   $ESC"
fi

export XMLBLASTER_HOME

if [ -d ${XMLBLASTER_HOME} ]; then
   
   # OK, know we know where xmlBlaster is installed ...

   ${ECHO} "$BLACK_LTGREEN   Welcome to xmlBlaster.org   $ESC"
   ${ECHO} "$BLACK_LTGREEN      Using XMLBLASTER_HOME=${XMLBLASTER_HOME}  $ESC"

   CLASSPATH=${XMLBLASTER_HOME}/lib/xml.jar:${CLASSPATH}
   CLASSPATH=${XMLBLASTER_HOME}/lib/xtdash.jar:${CLASSPATH}
   CLASSPATH=${XMLBLASTER_HOME}/lib/omquery.jar:${CLASSPATH}
   CLASSPATH=${XMLBLASTER_HOME}/lib/test.jar:${CLASSPATH}
   CLASSPATH=${XMLBLASTER_HOME}/lib/servlet-2.0.jar:${CLASSPATH}
   if [ ${USE_ANT:=""} = "true" ] ; then
      ${ECHO} "$BLACK_LTGREEN      Using Ant to build xmlBlaster  $ESC"
      CLASSPATH=${XMLBLASTER_HOME}/lib/xmlBlaster.jar:${CLASSPATH}
      CLASSPATH=${XMLBLASTER_HOME}/lib/demo.jar:${CLASSPATH}
      CLASSPATH=${XMLBLASTER_HOME}/lib/testsuite.jar:${CLASSPATH}
   else
      if [ -f ${XMLBLASTER_HOME}/lib/xmlBlaster.jar ]; then
         CLASSPATH=${XMLBLASTER_HOME}/lib/xmlBlaster.jar:${CLASSPATH}
      fi
   fi

   if [ -d ${XMLBLASTER_HOME}/src/java ]; then
      CLASSPATH=${XMLBLASTER_HOME}/src/java:${CLASSPATH}
   fi
   if [ -d ${XMLBLASTER_HOME}/classes ]; then
      CLASSPATH=${XMLBLASTER_HOME}/classes:${CLASSPATH}
   fi
   if [ -d ${XMLBLASTER_HOME}/demo ]; then
      CLASSPATH=${XMLBLASTER_HOME}/demo:${CLASSPATH}
   fi
   export CLASSPATH

   PATH=$PATH:$XMLBLASTER_HOME/bin:$XMLBLASTER_HOME/testsuite/bin
   export PATH

   alias cdx='cd $XMLBLASTER_HOME'
   alias cdxs='cd $XMLBLASTER_HOME/src'
   alias cdxi='cd $XMLBLASTER_HOME/src/idl'
   alias cdxj='cd $XMLBLASTER_HOME/src/java/org/xmlBlaster'
   alias cdxd='cd $XMLBLASTER_HOME/demo'
   alias cdxt='cd $XMLBLASTER_HOME/testsuite'
   alias cdxr='cd $XMLBLASTER_HOME/doc/requirements'
   alias cdxtj='cd $XMLBLASTER_HOME/testsuite/org/xmlBlaster'

   if [ ! -f ${HOME}/xmlBlaster.properties ]; then
      cp ${XMLBLASTER_HOME}/xmlBlaster.properties.template ${HOME}/xmlBlaster.properties
      ${ECHO} "$BLACK_RED   Please edit and customize ${HOME}/xmlBlaster.properties   $ESC"
   fi
else
   ${ECHO} "$BLACK_RED  Sorry, xmlBlaster.org not loaded, set your environment manually   $ESC"
   return 1
fi


#-------- Checking JDK version -
if [ ${JDK_HOME:=""} != "" ] ; then
   if [ -d ${JDK_HOME} ] ; then
      if [ -f ${JDK_HOME}/lib/classes.zip ]; then
         # JDK 1.1.x
         JDK_1_1=true
         export JDK_1_1
         CLASSPATH=${XMLBLASTER_HOME}/lib/collections.jar:${CLASSPATH}
      else
         # JDK 1.2
         CLASSPATH=${JDK_HOME}/jre/lib/rt.jar:${CLASSPATH}
         export CLASSPATH
			ORB_PROPS=${JDK_HOME}/jre/lib/orb.properties
		   if [ ! -f ${ORB_PROPS} ]; then
		      cp ${XMLBLASTER_HOME}/orb.properties ${ORB_PROPS}
		      ${ECHO} "$BLACK_RED   Created ${ORB_PROPS} to switch off default JDK-ORB$ESC"
		   fi
      fi
      PATH=${JDK_HOME}/bin:${PATH}
      export PATH
      # set JAVA_HOME for ANT:
      JAVA_HOME=$JDK_HOME
      export JAVA_HOME
   else
      ${ECHO} "$BLACK_RED   The directory JDK_HOME=$JDK_HOME doesn't exist   $ESC"
   fi
else
   ${ECHO} "$BLACK_LTGREEN      NOTE: You need JDK 1.2 to compile xmlBlaster            $ESC"
   ${ECHO} "$BLACK_LTGREEN            and your CLASSPATH setting needs at least         $ESC"
   ${ECHO} "$BLACK_LTGREEN               export CLASSPATH=\${JDK_HOME}/jre/lib/rt.jar    $ESC"
   ${ECHO} "$BLACK_LTGREEN            Or set JDK_HOME, and we will do the rest for you  $ESC"
   ${ECHO} "$BLACK_LTGREEN               Example: 'export JDK_HOME=/usr/local/jdk'      $ESC"
fi



#-------- Checking JacORB --------
# Is JacORB home not set already? Try to find where JacORB is:
if [ ${JacORB_HOME:=""} = "" ] ; then
   JACO_EXE=`which jaco`
   if [ ${JACO_EXE:=""} != "" ] ; then
      JACO_BIN=`dirname $JACO_EXE`
      JacORB_HOME=`dirname $JACO_BIN`
      export JacORB_HOME
   fi
fi

if [ ${JacORB_HOME:=""} = "" ] ; then
   # No external JacORB found, use the with xmlBlaster delivered JacORB:
   JacORB_HOME=${XMLBLASTER_HOME}
   export JacORB_HOME
fi

if [ -d ${JacORB_HOME} ] ; then
   PATH=${JacORB_HOME}/bin:${PATH}
   export PATH
   if [ -f ${JacORB_HOME}/classes/jacorb.jar ] ; then
      # The original JacORB distribution is used
      JacORB_LIB=${JacORB_HOME}/classes
      # The following two entries are only useful if you have JacORB installed separately:
      # To use JacORB demo:
      CLASSPATH=${CLASSPATH}:${JacORB_HOME}
      # To compile JacORB yourself:
      CLASSPATH=${CLASSPATH}:${JacORB_HOME}/classes
      export CLASSPATH
   else
      # The with xmlBlaster delivered JacORB distribution is used
      JacORB_LIB=${JacORB_HOME}/lib
   fi
   export JacORB_LIB
   CLASSPATH=${JacORB_LIB}/idl.jar:${CLASSPATH}
   CLASSPATH=${JacORB_LIB}/jacorb.jar:${CLASSPATH}
   #CLASSPATH=${CLASSPATH}:${JacORB_LIB}
   ${ECHO} "$BLACK_LTGREEN      Using JacORB_HOME=${JacORB_HOME}  $ESC"

   if [ ! -f ${HOME}/jacorb.properties ]; then
      cp ${JacORB_HOME}/jacorb.properties.template ${HOME}/jacorb.properties
      ${ECHO} "$BLACK_RED   Please edit and customize ${HOME}/jacorb.properties   $ESC"
   fi
else
   ${ECHO} "$BLACK_RED   The directory JacORB_HOME=$JacORB_HOME doesn't exist   $ESC"
fi



#-------- Checking MICO --------
if [ ${MICO_HOME:=""} = "" ] || [ ! -d ${MICO_HOME} ] ; then
   ${ECHO} "      If you want to use the C++ MICO client, set the MICO_HOME environment variable   "
   ${ECHO} "         Example: 'export MICO_HOME=/usr/local' if mico is in /usr/local/mico"
else
   ${ECHO} "$BLACK_LTGREEN      Using MICO_HOME=${MICO_HOME}  $ESC"
fi



#-------- Checking jikes version -
# use jikes 1.06 or better
if [ ${JIKES_HOME:=""} != "" ] ; then
   if [ -d ${JIKES_HOME} ] ; then
      PATH=${PATH}:${JIKES_HOME}
      export PATH
      JIKESPATH=${CLASSPATH}
      export JIKESPATH
      ${ECHO} "$BLACK_LTGREEN      Using JIKES_HOME=${JIKES_HOME}  $ESC"
   else
      ${ECHO} "$BLACK_RED   The directory JIKES_HOME=$JIKES_HOME doesn't exist   $ESC"
   fi
fi


return 0

