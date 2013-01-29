@echo off

echo xmlBlaster Build System
echo -------------------

if "%JAVA_HOME%" == "" goto error

if "%XMLBLASTER_HOME%" == ""  set XMLBLASTER_HOME="."
REM  a missing XMLBLASTER_HOME is not problem if the bat file is started in the root directory

set CLASSPATH=%CLASSPATH%;%JAVA_HOME%\lib\tools.jar
set LOCALCLASSPATH=%CLASSPATH%
for %%i in ("lib\ant\*.jar") do call "bin\lcp.bat" %%i

set LOCALCLASSPATH=%LOCALCLASSPATH%;lib\batik\js.jar
echo Building with classpath %LOCALCLASSPATH%

echo Starting Ant...

REM -Dbuild.compiler=jikes  or  modern  or classic

"%JAVA_HOME%\bin\java.exe" -Dant.home=. -classpath "%LOCALCLASSPATH%" org.apache.tools.ant.Main %*
REM to see compiler options use -verbose
REM "%JAVA_HOME%\bin\java.exe" -Dant.home=. -classpath "%LOCALCLASSPATH%" org.apache.tools.ant.Main -verbose %*

goto end

:error

echo ERROR: JAVA_HOME not found in your environment.
echo        Please, set the JAVA_HOME variable in your environment to match the
echo        location of the Java Virtual Machine you want to use, example:
echo set JAVA_HOME=C:\software\jdk1.4
echo set Path=%%Path%%;%%JAVA_HOME%%\bin

:end

set LOCALCLASSPATH=
