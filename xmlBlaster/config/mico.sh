
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

# check if the MICO Version is set (it is only used to build the name for the libraries)
if [ ${MICO_VER:=""} = "" ] ; then 
  ${ECHO} "$BLACK_RED You choosed to use the C++ MICO client but forgot to set MICO_VER $ESC"
  ${ECHO} "$BLACK_RED Example: export MICO_VER='2.3.1' $ESC"
fi

# check if the mico library really exists
MICO_LIBRARY_BASE=${MICO_HOME}/lib/libmico${MICO_VER}
if [ ! -f ${MICO_LIBRARY_BASE}.so ] && [ ! -f ${MICO_LIBRARY_BASE}.dll ] && [ ! -f ${MICO_LIBRARY_BASE}.a ] ; then 
  ${ECHO} "$BLACK_RED Could not find the MICO library. Check MICO_HOME & MICO_VER $ESC"
fi


#MICO_LIBRARY_BASE=${MICO_HOME}/lib/libmico${MICO_VER}
#if ! [ -f ${MICO_LIBRARY_BASE}.so ] ; then
#  ${ECHO} "$BLACK_RED Could not find the MICO library. Check MICO_HOME & MICO_VER $ESC"
#fi



export CORBA_CPP="mico"
export CORBACPP_HOME=${MICO_HOME}
if [ $MICO_VER ] ; then
  export CORBACPP_VER=${MICO_VER}
fi

