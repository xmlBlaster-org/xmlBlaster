export DELAY_TIME=4

echo -e "\nStarting xmlBlaster server (without jit) ...\n"
    xterm -bg yellow -fg black -sb -sl 4000 -geom 160x24 \
	      -T "xmlBlaster server" \
	      -e ${JAVA_WRAP} org.xmlBlaster.MainGUI &

sleep ${DELAY_TIME}
sleep ${DELAY_TIME}
bin/RamTest -ior `java org.jutils.cpp.HttpIorForCpp`
sleep ${DELAY_TIME}
bin/TestGet -ior `java org.jutils.cpp.HttpIorForCpp`
sleep ${DELAY_TIME}
bin/TestLogin -ior `java org.jutils.cpp.HttpIorForCpp`
sleep ${DELAY_TIME}
bin/TestSub -ior `java org.jutils.cpp.HttpIorForCpp`
