@echo off

echo xmlBlaster Build System
echo -------------------

if "%JAVA_HOME%" == "" goto error

set LOCALCLASSPATH=%CLASSPATH%;%JAVA_HOME%\lib\tools.jar;.\lib\ant.jar;lib\xml.jar;.\lib\idl.jar;.\lib\jacorb.jar;.\lib\omquery.jar;.\lib\xtdash.jar;.\servlet-2.0.jar;.\lib\test.jar

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
