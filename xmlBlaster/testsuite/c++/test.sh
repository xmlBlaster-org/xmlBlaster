export DELAY_TIME=4

echo -e "\nStarting xmlBlaster server (without jit) ...\n"
    xterm -bg yellow -fg black -sb -sl 4000 -geom 160x24 \
              -T "xmlBlaster server" \
              -e ${JAVA_WRAP} org.xmlBlaster.MainGUI -iorFile /tmp/ior.dat &

sleep ${DELAY_TIME}
sleep ${DELAY_TIME}
bin/RamTest -iorFile /tmp/ior.dat
sleep ${DELAY_TIME}
bin/TestGet -iorFile /tmp/ior.dat
sleep ${DELAY_TIME}
bin/TestLogin -iorFile /tmp/ior.dat
sleep ${DELAY_TIME}
bin/TestSub -iorFile /tmp/ior.dat
