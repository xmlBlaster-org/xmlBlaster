@echo off

echo xmlBlaster Build System
echo -------------------

if "%JAVA_HOME%" == "" goto error

set LOCALCLASSPATH=%CLASSPATH%;%JAVA_HOME%\lib\tools.jar;lib\ant\ant.jar;lib\ant\cpptasks.jar;lib\ant\ant-contrib.jar;lib\parser.jar;lib\jaxp.jar;.\lib\idl.jar;.\lib\jacorb.jar;.\lib\omquery.jar;.\lib\xtdash.jar;.\servlet.jar;.\lib\test.jar;.\lib\xmlrpc.jar;.\lib\a2Blaster.jar;.\lib\jutils.jar;.\lib\mail.jar;.\lib\activation.jar;.\lib\cpptasks.jar;.\lib\batik\batik-awt-util.jar;.\lib\batik\batik-bridge.jar;.\lib\batik\batik-css.jar;.\lib\batik\batik-dom.jar;.\lib\batik\batik-ext.jar;.\lib\batik\batik-extension.jar;.\lib\batik\batik-gui-util.jar;.\lib\batik\batik-gvt.jar;.\lib\batik\batik-parser.jar;.\lib\batik\batik-script.jar;.\lib\batik\batik-svg-dom.jar;.\lib\batik\batik-svggen.jar;.\lib\batik\batik-transcoder.jar;.\lib\batik\batik-util.jar;.\lib\batik\batik-xml.jar;.\lib\Xindice\xalan-2.0.1.jar;.\lib\Xindice\xmldb.jar;.\lib\Xindice\xindice.jar;lib\ant\xerces.jar;lib\concurrent.jar

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
