@echo off

echo xmlBlaster Build System
echo -------------------

if "%JAVA_HOME%" == "" goto error

if "%XMLBLASTER_HOME%" == ""  set XMLBLASTER_HOME="."
REM  a missing XMLBLASTER_HOME is not problem if the bat file is started in the root directory

set LOCALCLASSPATH=%JAVA_HOME%\lib\tools.jar;%XMLBLASTER_HOME%\lib\ant\ant.jar;%XMLBLASTER_HOME%\lib\ant\cpptasks.jar;%XMLBLASTER_HOME%\lib\ant\ant-contrib.jar;%XMLBLASTER_HOME%\lib\ant\xerces.jar;%XMLBLASTER_HOME%\lib\ant\optional.jar;%XMLBLASTER_HOME%\lib\junit.jar;%XMLBLASTER_HOME%\lib\ant\xalan.jar;%XMLBLASTER_HOME%\lib\ant\xml-apis.jar


echo Building with classpath %LOCALCLASSPATH%

echo Starting Ant...

REM -Dbuild.compiler=jikes  or  modern  or classic

%JAVA_HOME%\bin\java.exe -Dant.home=. -classpath "%LOCALCLASSPATH%" org.apache.tools.ant.Main %*

goto end

:error

echo "ERROR: JAVA_HOME not found in your environment."
echo "Please, set the JAVA_HOME variable in your environment to match the"
echo "location of the Java Virtual Machine you want to use."

:end

set LOCALCLASSPATH=
