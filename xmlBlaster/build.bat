@echo off

echo xmlBlaster Build System
echo -------------------

if "%JAVA_HOME%" == "" goto error

set LOCALCLASSPATH=%CLASSPATH%;%JAVA_HOME%\lib\tools.jar;lib\ant\ant.jar;lib\ant\cpptasks.jar;lib\ant\ant-contrib.jar;lib\parser.jar;lib\jaxp.jar;.\lib\idl.jar;.\lib\jacorb.jar;.\lib\omquery.jar;.\lib\xtdash.jar;.\servlet.jar;.\lib\junit.jar;.\lib\xmlrpc.jar;.\lib\a2Blaster.jar;.\lib\jutils.jar;.\lib\mail.jar;.\lib\activation.jar;.\lib\cpptasks.jar;.\lib\batik\batik.jar;.\lib\batik\js.jar;.\lib\Xindice\xalan-2.0.1.jar;.\lib\Xindice\xmldb.jar;.\lib\Xindice\xindice.jar;lib\ant\xerces.jar;lib\concurrent.jar;lib\gnu-regexp.jar;lib\remotecons.jar

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
