#!/bin/sh

JAVA_HOME=/usr/jdk1.2.2

CLASSPATH=lib/ant.jar:lib/xml.jar:lib/idl.jar:lib/jacorb.jar:lib/omquery.jar:lib/xtdash.jar:lib/servlet-2.0.jar:lib/test.jar:$JAVA_HOME/lib/tools.jar


$JAVA_HOME/bin/java -Dant.home=$XMLBLASTER_HOME -classpath $CLASSPATH org.apache.tools.ant.Main $@

