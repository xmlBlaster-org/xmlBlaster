#!/bin/sh
#
# Helper to start the different test groups in sequence (UNIX only)
# You need to manually close each of the GUI windows to continue
#
rm -rf $HOME/tmp/fileRecorder
echo "STARTING classtest TESTS ..."
java -Djava.compiler= junit.swingui.TestRunner -noloading org.xmlBlaster.test.classtest.AllTests
echo "STARTING xmlBlaster and wait for 10 sec to startup ..."
xterm -geom 180x26 -e java org.xmlBlaster.Main&
sleep 10
echo "STARTING qos TESTS ..."
java junit.swingui.TestRunner -noloading org.xmlBlaster.test.qos.AllTests
echo "STARTING distributor TESTS ..."
java junit.swingui.TestRunner -noloading org.xmlBlaster.test.distributor.AllTests
echo "STARTING client TESTS ..."
java junit.swingui.TestRunner -noloading org.xmlBlaster.test.client.AllTests
echo "STARTING authentication TESTS ..."
java -Djava.compiler= junit.swingui.TestRunner -noloading org.xmlBlaster.test.authentication.AllTests
echo "STARTING dispatch TESTS ..."
java junit.swingui.TestRunner -noloading org.xmlBlaster.test.dispatch.AllTests
echo "STARTING jdbc TESTS ..."
java junit.swingui.TestRunner -noloading org.xmlBlaster.test.jdbc.AllTests
echo "STARTING jms TESTS ..."
java junit.swingui.TestRunner -noloading org.xmlBlaster.test.jms.AllTests
echo "STARTING mime TESTS ..."
java junit.swingui.TestRunner -noloading org.xmlBlaster.test.mime.AllTests
echo "STARTING topic TESTS ..."
java junit.swingui.TestRunner -noloading org.xmlBlaster.test.topic.AllTests
echo "STARTING persistence TESTS ..."
java junit.swingui.TestRunner -noloading org.xmlBlaster.test.persistence.AllTests
echo "STARTING j2ee TESTS ..."
java junit.swingui.TestRunner -noloading org.xmlBlaster.test.j2ee.AllTests
#echo "STARTING memoryleak TESTS ..."
# Do it manually
#java junit.swingui.TestRunner -noloading org.xmlBlaster.test.memoryleak.AllTests
echo "STARTING jmx TESTS ..."
java junit.swingui.TestRunner -noloading org.xmlBlaster.test.jmx.AllTests
echo "STARTING cluster TESTS ..."
java junit.swingui.TestRunner -noloading org.xmlBlaster.test.cluster.AllTests
echo "STARTING stress TESTS ..."
java junit.swingui.TestRunner -noloading org.xmlBlaster.test.stress.AllTests
echo "STARTING classloader TESTS ..."
java junit.swingui.TestRunner -noloading org.xmlBlaster.test.classloader.AllTests
echo "STARTING snmp TESTS ..."
java junit.swingui.TestRunner -noloading org.xmlBlaster.test.snmp.AllTests
echo "STARTING C TESTS ..."
java -Djava.compiler= junit.swingui.TestRunner -noloading org.xmlBlaster.test.C.AllTests
