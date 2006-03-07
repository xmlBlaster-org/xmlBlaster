#!/bin/sh
#
# Helper to start the different test groups in sequence (UNIX only)
# You need to manually close each of the GUI windows to continue
#
rm -rf $HOME/tmp/fileRecorder
echo "STARTING xmlBlaster and wait for 10 sec to startup ..."
xterm -geom 180x26 -e java org.xmlBlaster.Main&
sleep 10
echo "STARTING stress TESTS ..."
java junit.swingui.TestRunner -noloading org.xmlBlaster.test.stress.AllTests
echo "STARTING classloader TESTS ..."
java junit.swingui.TestRunner -noloading org.xmlBlaster.test.classloader.AllTests
echo "STARTING snmp TESTS ..."
java junit.swingui.TestRunner -noloading org.xmlBlaster.test.snmp.AllTests
echo "STARTING C TESTS ..."
java -Djava.compiler= junit.swingui.TestRunner -noloading org.xmlBlaster.test.C.AllTests
