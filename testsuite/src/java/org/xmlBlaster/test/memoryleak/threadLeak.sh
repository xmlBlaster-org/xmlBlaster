#!/bin/bash
# Start xmlBlaster first: java org.xmlBlaster.Main
# Invoke: threadLeak.sh  > log 2>&1; tail -f log
uname -a
java -version
java -classpath \
       $XMLBLASTER_HOME/lib/junit.jar:$XMLBLASTER_HOME/lib/testsuite.jar:$XMLBLASTER_HOME/lib/xmlBlaster.jar \
       -Dtrace=false org.xmlBlaster.test.memoryleak.TestThreadLeak \
       -pidFileName pidFile &
echo $! > pidFile
