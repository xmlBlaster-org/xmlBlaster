#!/bin/sh
#
# Helper to start the different test groups in sequence (UNIX only)
# You need to manually close each of the GUI windows to continue
#
rm -r $HOME/tmp/fileRecorder
echo "STARTING xmlBlaster and wait for 10 sec to startup ..."
xterm -geom 180x26 -e java org.xmlBlaster.Main&
sleep 10
echo "STARTING classtest TESTS ..."
java -Djava.compiler= junit.swingui.TestRunner -noloading org.xmlBlaster.test.classtest.AllTests
echo "STARTING qos TESTS ..."
java junit.swingui.TestRunner -noloading org.xmlBlaster.test.qos.AllTests
echo "STARTING client TESTS ..."
java junit.swingui.TestRunner -noloading org.xmlBlaster.test.client.AllTests
echo "STARTING authentication TESTS ..."
java -Djava.compiler= junit.swingui.TestRunner -noloading org.xmlBlaster.test.authentication.AllTests
echo "STARTING dispatch TESTS ..."
java junit.swingui.TestRunner -noloading org.xmlBlaster.test.dispatch.AllTests
echo "STARTING jdbc TESTS ..."
java junit.swingui.TestRunner -noloading org.xmlBlaster.test.jdbc.AllTests
echo "STARTING mime TESTS ..."
java junit.swingui.TestRunner -noloading org.xmlBlaster.test.mime.AllTests
echo "STARTING topic TESTS ..."
java junit.swingui.TestRunner -noloading org.xmlBlaster.test.topic.AllTests
echo "STARTING persistence TESTS ..."
java junit.swingui.TestRunner -noloading org.xmlBlaster.test.persistence.AllTests
echo "STARTING cluster TESTS ..."
java junit.swingui.TestRunner -noloading org.xmlBlaster.test.cluster.AllTests

