# $Id: jacorb.sh,v 1.7 2002/07/13 20:01:45 goetzger Exp $
#-------- Checking JacORB --------
# Is JacORB home not set already? Try to find where JacORB is:
if [ "${JacORB_HOME}" = "" ] ; then
   JACO_EXE=`${WHICH} jaco`
   if [ "${JACO_EXE}" != "" ] ; then
      JACO_BIN=`dirname $JACO_EXE`
      JacORB_HOME=`dirname $JACO_BIN`
      export JacORB_HOME
   fi
fi

if [ "${JacORB_HOME}" = "" ] ; then
   # No external JacORB found, use the with xmlBlaster delivered JacORB:
   JacORB_HOME=${XMLBLASTER_HOME}
   export JacORB_HOME
   PATH=${PATH}:${JacORB_HOME}/bin 
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
   ${ECHO} "$BLACK_LTGREEN   JacORB_HOME    =${JacORB_HOME}  $ESC"

   if [ ! -f ${HOME}/jacorb.properties ]; then
      cp ${JacORB_HOME}/config/jacorb.properties.template ${HOME}/jacorb.properties
      ${ECHO} "$BLACK_RED   Please edit and customize ${HOME}/jacorb.properties   $ESC"
   fi
else
   ${ECHO} "$BLACK_RED   The directory JacORB_HOME=$JacORB_HOME doesn't exist   $ESC"
fi

JACO_EXE=`${WHICH} jaco`
JACO_BIN=`dirname ${JACO_EXE}`
export IDL_OUTDIR="-d"
export IDL2JAVA="${JACO_BIN}/idl -p org.xmlBlaster.protocol.corba"
export JAVA_WRAP=java
#export JAVA_WRAP=jaco
