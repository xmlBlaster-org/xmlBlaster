#-----------------------------------------------------------
# The xmlBlaster.org project
#
# You may use this script to source into your sh, ksh, bash
#
# Example:
#   export JDK_HOME=/usr/local/jdk
#   export XMLBLASTER_HOME=/home/paul/xmlBlaster
#   export JacORB_HOME=/usr/local/JacORB
#   export MICO_HOME=/usr/local/mico
#   . /home/paul/xmlBlaster/.bashrc
#
# If you want to access xmlBlaster using cvs:
#   export CVSROOT=:pserver:xmlblaster@193.197.24.129:/www/cvsroot
#-----------------------------------------------------------


BLACK_LTGREEN="\033[40;46m"
BLACK_RED="\033[30;41m"
ESC="\033[0m"


#-------- Checking xmlBlaster --------
if ! [ ${XMLBLASTER_HOME} ] ; then
   echo -e "$BLACK_RED   Please set the environment variable XMLBLASTER_HOME          $ESC"
   echo -e "$BLACK_RED      Example: 'export XMLBLASTER_HOME=/home/paul/xmlBlaster'   $ESC"
   return
fi

if ! [ -d ${XMLBLASTER_HOME} ] ; then
   echo -e "$BLACK_RED   The directory XMLBLASTER_HOME=$XMLBLASTER_HOME doesn't exist   $ESC"
fi

export XMLBLASTER_HOME

if [ -d ${XMLBLASTER_HOME} ]; then
   
   # OK, know we know where xmlBlaster is installed ...

   echo -e "$BLACK_LTGREEN   Welcome to xmlBlaster.org   $ESC"
   echo -e "$BLACK_LTGREEN      Using XMLBLASTER_HOME=${XMLBLASTER_HOME}  $ESC"

   CLASSPATH=${CLASSPATH}:${XMLBLASTER_HOME}/classes
   CLASSPATH=${CLASSPATH}:${XMLBLASTER_HOME}/src/java
   CLASSPATH=${CLASSPATH}:${XMLBLASTER_HOME}/lib/omquery.jar
   CLASSPATH=${CLASSPATH}:${XMLBLASTER_HOME}/lib/xtdash.jar
   CLASSPATH=${CLASSPATH}:${XMLBLASTER_HOME}/lib/xml.jar
   export CLASSPATH

   PATH=$PATH:$XMLBLASTER_HOME/testsuite/bin
   export PATH

   alias cdx='cd $XMLBLASTER_HOME'
   alias cdxr='cd $XMLBLASTER_HOME'
   alias cdxs='cd $XMLBLASTER_HOME/src'
   alias cdxi='cd $XMLBLASTER_HOME/src/idl'
   alias cdxj='cd $XMLBLASTER_HOME/src/java/org/xmlBlaster'
   alias cdxt='cd $XMLBLASTER_HOME/testsuite'
   alias cdxtj='cd $XMLBLASTER_HOME/testsuite/org/xmlBlaster'
else
   echo -e "$BLACK_RED  Sorry, xmlBlaster.org not loaded, set your environment manually   $ESC"
   return 1
fi


#-------- Checking JacORB --------
if ! [ ${JacORB_HOME} ] ; then
   JACO_EXE=`which jaco`
   if [ ${JACO_EXE} ] ; then
      JACO_BIN=`dirname $JACO_EXE`
      JacORB_HOME=`dirname $JACO_BIN`
      export JacORB_HOME
   else
      echo -e ""
      echo -e "$BLACK_RED   Please set environment variable JacORB_HOME                       $ESC"
      echo -e "$BLACK_RED      Example: 'export JacORB_HOME=/usr/local/JacORB'                $ESC"
      echo -e "$BLACK_RED      or set PATH to contain 'jaco' and we will do the rest for you  $ESC"
      echo -e ""
      return 1
   fi
   echo -e "$BLACK_LTGREEN      Using JacORB_HOME=${JacORB_HOME}  $ESC"
else
   echo -e "$BLACK_LTGREEN      Using JacORB_HOME=${JacORB_HOME}  $ESC"
fi

if ! [ -d ${JacORB_HOME} ] ; then
   echo -e "$BLACK_RED   The directory JacORB_HOME=$JacORB_HOME doesn't exist   $ESC"
fi



#-------- Checking MICO --------
if ! [ ${MICO_HOME} ] ; then
   echo -e "      If you want to use the C++ MICO client, set the MICO_HOME environment variable   "
   echo -e "         Example: 'export MICO_HOME=/usr/local/mico'"
else
   echo -e "$BLACK_LTGREEN      Using MICO_HOME=${MICO_HOME}  $ESC"
fi



#-------- Checking JDK version -
if [ ${JDK_HOME} ] ; then
   if [ -d ${JDK_HOME} ] ; then
      CLASSPATH=${CLASSPATH}:${JDK_HOME}/jre/lib/rt.jar
      export CLASSPATH
   else
      echo -e "$BLACK_RED   The directory JacORB_HOME=$JacORB_HOME doesn't exist   $ESC"
   fi
else
   echo -e "$BLACK_LTGREEN      NOTE: You need JDK 1.2 to compile xmlBlaster            $ESC"
   echo -e "$BLACK_LTGREEN            and your CLASSPATH setting needs at least         $ESC"
   echo -e "$BLACK_LTGREEN               export CLASSPATH=JDK_HOME/jre/lib/rt.jar       $ESC"
   echo -e "$BLACK_LTGREEN            Or set JDK_HOME, and we will do the rest for you  $ESC"
   echo -e "$BLACK_LTGREEN               Example: 'export JDK_HOME=/usr/local/jdk'       $ESC"
fi


return 0

