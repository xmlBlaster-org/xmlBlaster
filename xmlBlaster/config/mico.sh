
#-------- Checking MICO --------
if [ ${MICO_HOME:=""} = "" ] || [ ! -d ${MICO_HOME} ] ; then
   ${ECHO} "      If you want to use the C++ MICO client, set the MICO_HOME environment variable   "
   ${ECHO} "         Example: 'export MICO_HOME=/usr/local/mico'"
else
   if ! [ $LD_LIBRARY_PATH ] ; then
      LD_LIBRARY_PATH=${MICO_HOME}/lib
   else
      LD_LIBRARY_PATH=${LD_LIBRARY_PATH}:${MICO_HOME}/lib
   fi
   export LD_LIBRARY_PATH
   ${ECHO} "$BLACK_LTGREEN      Using MICO_HOME=${MICO_HOME}  $ESC"
fi


