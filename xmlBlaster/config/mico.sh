
#-------- Checking MICO --------
if [ ${MICO_HOME:=""} = "" ] || [ ! -d ${MICO_HOME} ] ; then
   ${ECHO} "$BLACK_RED  If you want to use the C++ MICO client, set the MICO_HOME environment variable $ESC"
   ${ECHO} "$BLACK_RED  Example: 'export MICO_HOME=/usr/local/mico' $ESC"
else
   if ! [ $LD_LIBRARY_PATH ] ; then
      LD_LIBRARY_PATH=${MICO_HOME}/lib
   else
      LD_LIBRARY_PATH=${LD_LIBRARY_PATH}:${MICO_HOME}/lib
   fi
   export LD_LIBRARY_PATH
   ${ECHO} "$BLACK_LTGREEN      Using MICO_HOME=${MICO_HOME}  $ESC"
fi

export CORBA_CPP="mico"
export CORBACPP_HOME=${MICO_HOME}
if [ $MICO_VER ] ; then
  export CORBACPP_VER=${MICO_VER}
fi

