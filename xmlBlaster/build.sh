#!/bin/sh

#Set this in your environment:
#JAVA_HOME=/usr/jdk1.2.2
if [ ${JAVA_HOME:=""} = "" ] ; then
   echo "ERROR: Please set the environment variable JAVA_HOME"
   exit
fi

CLASSPATH=lib/ant/ant.jar:lib/ant/cpptasks.jar:lib/ant/ant-contrib.jar:$CLASSPATH:lib/ant/xerces.jar

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

