@echo off

echo xmlBlaster Build System
echo -------------------

if "%JAVA_HOME%" == "" goto error

set LOCALCLASSPATH=%CLASSPATH%;%JAVA_HOME%\lib\tools.jar;.\lib\ant.jar;lib\parser.jar;lib\jaxp.jar;.\lib\idl.jar;.\lib\jacorb.jar;.\lib\omquery.jar;.\lib\xtdash.jar;.\servlet.jar;.\lib\test.jar;.\lib\xmlrpc.jar;.\lib\a2Blaster.jar;.\lib\jutils.jar;.\lib\mail.jar;.\lib\activation.jar;.\lib\cpptasks.jar;.\lib\batik-awt-util.jar;.\lib\batik-bridge.jar;.\lib\batik-css.jar;.\lib\batik-dom.jar;.\lib\batik-ext.jar;.\lib\batik-extension.jar;.\lib\batik-gui-util.jar;.\lib\batik-gvt.jar;.\lib\batik-parser.jar;.\lib\batik-script.jar;.\lib\batik-svg-dom.jar;.\lib\batik-svggen.jar;.\lib\batik-transcoder.jar;.\lib\batik-util.jar;.\lib\batik-xml.jar

echo Building with classpath %LOCALCLASSPATH%

echo Starting Ant...

%JAVA_HOME%\bin\java.exe -Dant.home=. -classpath "%LOCALCLASSPATH%" org.apache.tools.ant.Main %1 %2 %3 %4 %5

goto end

:error

echo "ERROR: JAVA_HOME not found in your environment."
echo "Please, set the JAVA_HOME variable in your environment to match the"
echo "location of the Java Virtual Machine you want to use."

:end

set LOCALCLASSPATH=
