
#-------- Checking MICO --------
if [ ${CORBACPP_HOME:=""} = "" ] || [ ! -d ${CORBACPP_HOME} ] ; then
   ${ECHO} "$BLACK_RED  If you want to use the C++ MICO client, set the CORBACPP_HOME environment variable $ESC"
   ${ECHO} "$BLACK_RED  Example: 'export CORBACPP_HOME=/usr/local/mico' $ESC"
else
   if ! [ $LD_LIBRARY_PATH ] ; then
      LD_LIBRARY_PATH=${CORBACPP_HOME}/lib
   else
      LD_LIBRARY_PATH=${LD_LIBRARY_PATH}:${CORBACPP_HOME}/lib
   fi
   export LD_LIBRARY_PATH
fi

# check if the MICO Version is set (it is only used to build the name for the libraries)
if [ ${CORBACPP_VER:=""} = "" ] ; then 
  ${ECHO} "$BLACK_RED   You choosed to use the C++ MICO client but forgot to set CORBACPP_VER $ESC"
  ${ECHO} "$BLACK_RED   Example: export CORBACPP_VER='2.3.6' $ESC"
fi

# check if the mico library really exists
MICO_LIBRARY_BASE=${CORBACPP_HOME}/libs/libmico${CORBACPP_VER}
if [ ! -f ${MICO_LIBRARY_BASE}.so ] && [ ! -f ${MICO_LIBRARY_BASE}.dll ] && [ ! -f ${MICO_LIBRARY_BASE}.a ] ; then 
  ${ECHO} "${BLACK_RED}   Could not find the MICO library in ${MICO_LIBRARY_BASE}. Check CORBACPP_HOME & CORBACPP_VER $ESC"
fi


export CORBA_CPP="mico"
export CORBACPP_HOME=${CORBACPP_HOME}
if [ $CORBACPP_VER ] ; then
  export CORBACPP_VER=${CORBACPP_VER}
fi

export PATH=$PATH:${CORBACPP_HOME}/bin
