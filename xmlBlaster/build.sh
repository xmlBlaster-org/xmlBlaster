#!/bin/sh

#Set this in your environment:
#JAVA_HOME=/usr/jdk1.2.2
if [ ${JAVA_HOME:=""} = "" ] ; then
   echo "ERROR: Please set the environment variable JAVA_HOME"
   exit
fi

CLASSPATH=lib/ant.jar:lib/parser.jar:lib/jaxp.jar:lib/idl.jar:lib/jacorb.jar:lib/omquery.jar:lib/xtdash.jar:lib/servlet.jar:lib/test.jar:$JAVA_HOME/lib/tools.jar:lib/xmlrpc.jar:lib/a2Blaster.jar:lib/jutils.jar:$CLASSPATH

ALL_ENV=`env`
MY_DEF=""
for i in ${ALL_ENV} ; do
    #echo "Processing -D$i"
    MY_DEF="${MY_DEF} -D$i"
done
#echo ${MY_DEF}

# -Dbuild.compiler=jikes  or  modern  or classic

#$JAVA_HOME/bin/java -Dbuild.compiler=jikes -Dant.home=$XMLBLASTER_HOME ${MY_DEF} -classpath $CLASSPATH org.apache.tools.ant.Main $@
$JAVA_HOME/bin/java -Dant.home=$XMLBLASTER_HOME ${MY_DEF} -classpath $CLASSPATH org.apache.tools.ant.Main $@

