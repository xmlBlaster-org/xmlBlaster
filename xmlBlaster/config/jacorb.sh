
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
   ${ECHO} "$BLACK_LTGREEN      Using JacORB_HOME=${JacORB_HOME}  $ESC"

   if [ ! -f ${HOME}/jacorb.properties ]; then
      cp ${JacORB_HOME}/jacorb.properties.template ${HOME}/jacorb.properties
      ${ECHO} "$BLACK_RED   Please edit and customize ${HOME}/jacorb.properties   $ESC"
   fi
else
   ${ECHO} "$BLACK_RED   The directory JacORB_HOME=$JacORB_HOME doesn't exist   $ESC"
fi


export IDL2JAVA="${JacORB_HOME}/bin/idl -p org.xmlBlaster.protocol.corba"

