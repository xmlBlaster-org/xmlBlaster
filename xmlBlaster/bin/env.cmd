echo "Setting Marcels aliases"
@echo off

REM Example:
REM set XMLBLASTER_HOME=C:\Marcel\xmlBlaster
REM set PATH=C:\PROGRA~1\MICROS~1.NET\Common7\IDE;C:\PROGRA~1\MICROS~1.NET\VC7\BIN;C:\PROGRA~1\MICROS~1.NET\Common7\Tools;C:\PROGRA~1\MICROS~1.NET\Common7\Tools\bin;C:\PROGRA~1\MICROS~1.NET\SDK\v1.1\bin;C:\WINDOWS\Microsoft.NET\Framework\v1.1.4322;C:\WINDOWS\system32;C:\WINDOWS;C:\WINDOWS\System32\Wbem;C:\Marcel\IONA\bin;C:\Marcel\IONA\asp\5.1\bin;C:\Marcel\jdk\bin;C:\Marcel\bin;C:\Marcel\CRiSP\bin.w32;C:\Marcel\xerces-c2_2_0-win32\bin;%ACE_ROOT%\bin;%XMLBLASTER_HOME%\bin;C:\Marcel\mico\win32-bin;%XMLBLASTER_HOME%\lib;C:\Marcel\omniORB-4.0.0\bin\x86_win32;%XMLBLASTER_HOME%\testsuite\src\c++\bin;%XMLBLASTER_HOME%\demo\c++\bin;%XMLBLASTER_HOME%\demo\c\socket\bin;%XMLBLASTER_HOME%\testsuite\src\c\bin
REM set INCLUDE=C:\PROGRA~1\MICROS~1.NET\VC7\ATLMFC\INCLUDE;C:\PROGRA~1\MICROS~1.NET\VC7\INCLUDE;C:\PROGRA~1\MICROS~1.NET\VC7\PlatformSDK\include;C:\PROGRA~1\MICROS~1.NET\SDK\v1.1\include
REM set LIB=C:\PROGRA~1\MICROS~1.NET\VC7\ATLMFC\LIB;C:\PROGRA~1\MICROS~1.NET\VC7\LIB;C:\PROGRA~1\MICROS~1.NET\VC7\PlatformSDK\lib;C:\PROGRA~1\MICROS~1.NET\SDK\v1.1\lib;C:\PROGRA~1\MICROS~1.NET\VC7\lib

doskey l=dir
doskey cdx=cd %XMLBLASTER_HOME%
doskey cdxs=cd %XMLBLASTER_HOME%\src
doskey cdxj=cd %XMLBLASTER_HOME%\src\java\org\xmlBlaster
doskey cdxu=cd %XMLBLASTER_HOME%\src\java\org\jutils
doskey cdxd=cd %XMLBLASTER_HOME%\demo
doskey cdxdj=cd %XMLBLASTER_HOME%\demo\javaclients
doskey cdxt=cd %XMLBLASTER_HOME%\testsuite\src
doskey cdxr=cd %XMLBLASTER_HOME%\doc\requirements
doskey cdxtj=cd %XMLBLASTER_HOME%\testsuite\src\java\org\xmlBlaster\test

