@echo off

echo xmlBlaster Build System
echo -------------------

if "%JAVA_HOME%" == "" goto error

set LOCALCLASSPATH=%JAVA_HOME%\lib\tools.jar;lib\ant\ant.jar;lib\ant\cpptasks.jar;lib\ant\ant-contrib.jar;lib\ant\xerces.jar;lib\ant\optional.jar;lib\junit.jar;lib\ant\xalan.jar;lib\ant\xml-apis.jar


echo Building with classpath %LOCALCLASSPATH%

echo Starting Ant...

REM -Dbuild.compiler=jikes  or  modern  or classic

%JAVA_HOME%\bin\java.exe -Dant.home=. -classpath "%LOCALCLASSPATH%" org.apache.tools.ant.Main %1 %2 %3 %4 %5

goto end

:error

echo "ERROR: JAVA_HOME not found in your environment."
echo "Please, set the JAVA_HOME variable in your environment to match the"
echo "location of the Java Virtual Machine you want to use."

:end

set LOCALCLASSPATH=
