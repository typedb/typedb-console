@echo off
REM This Source Code Form is subject to the terms of the Mozilla Public
REM License, v. 2.0. If a copy of the MPL was not distributed with this
REM file, You can obtain one at https://mozilla.org/MPL/2.0/.

SET "TYPEDB_HOME=%~dp0"
IF %TYPEDB_HOME:~-1%==\ SET TYPEDB_HOME=%TYPEDB_HOME:~0,-1%

if "%1" == "" goto missingargument

if "%1" == "console" goto startconsole

echo   Invalid argument: %1. Possible commands are:
goto print_usage

:missingargument

echo   Missing argument. Possible commands are:
goto print_usage

:startconsole
for /f "tokens=1,* delims= " %%a in ("%*") do set ARGS=%%b
%TYPEDB_HOME%\console\typedb_console_bin.exe %ARGS%
goto exit

:exit
exit /b 0

:print_usage
echo   Console:         typedb console [--help]
goto exiterror

:exiterror
exit /b 1
