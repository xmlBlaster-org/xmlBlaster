#!/bin/sh

#Set this in your environment:
#JAVA_HOME=/usr/jdk1.2.2
if [ ${JAVA_HOME:=""} = "" ] ; then
   echo "ERROR: Please set the environment variable JAVA_HOME"
   exit
fi

CLASSPATH=lib/ant/ant.jar:lib/ant/cpptasks.jar:lib/ant/ant-contrib.jar:$CLASSPATH

CLASSPATH=lib/parser.jar:lib/jaxp.jar:$CLASSPATH
CLASSPATH=lib/idl.jar:lib/jacorb.jar:$CLASSPATH
CLASSPATH=lib/omquery.jar:lib/xtdash.jar:lib/servlet.jar:lib/test.jar:$CLASSPATH
CLASSPATH=$JAVA_HOME/lib/tools.jar:lib/xmlrpc.jar:lib/a2Blaster.jar:lib/jutils.jar:$CLASSPATH
CLASSPATH=lib/mail.jar:lib/activation.jar:lib/cpptasks.jar:$CLASSPATH

CLASSPATH=lib/batik/batik-awt-util.jar:$CLASSPATH
CLASSPATH=lib/batik/batik-bridge.jar:$CLASSPATH
CLASSPATH=lib/batik/batik-css.jar:$CLASSPATH
CLASSPATH=lib/batik/batik-dom.jar:$CLASSPATH
CLASSPATH=lib/batik/batik-ext.jar:$CLASSPATH
CLASSPATH=lib/batik/batik-extension.jar:$CLASSPATH
CLASSPATH=lib/batik/batik-gui-util.jar:$CLASSPATH
CLASSPATH=lib/batik/batik-gvt.jar:$CLASSPATH
CLASSPATH=lib/batik/batik-parser.jar:$CLASSPATH
CLASSPATH=lib/batik/batik-script.jar:$CLASSPATH
CLASSPATH=lib/batik/batik-svg-dom.jar:$CLASSPATH
CLASSPATH=lib/batik/batik-svggen.jar:$CLASSPATH
CLASSPATH=lib/batik/batik-transcoder.jar:$CLASSPATH
CLASSPATH=lib/batik/batik-util.jar:$CLASSPATH
CLASSPATH=lib/batik/batik-xml.jar:$CLASSPATH

CLASSPATH=lib/Xindice/xalan-2.0.1.jar:$CLASSPATH
CLASSPATH=lib/Xindice/xindice.jar:$CLASSPATH
CLASSPATH=lib/Xindice/xmldb.jar:$CLASSPATH

CLASSPATH=lib/ant/xerces.jar:$CLASSPATH

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

