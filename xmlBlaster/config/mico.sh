
#-------- Checking MICO --------
if [ ${MICO_HOME:=""} = "" ] || [ ! -d ${MICO_HOME} ] ; then
   ${ECHO} "      If you want to use the C++ MICO client, set the MICO_HOME environment variable   "
   ${ECHO} "         Example: 'export MICO_HOME=/usr/local' if mico is in /usr/local/mico"
else
   ${ECHO} "$BLACK_LTGREEN      Using MICO_HOME=${MICO_HOME}  $ESC"
fi


