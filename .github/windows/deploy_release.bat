@echo off
REM This Source Code Form is subject to the terms of the Mozilla Public
REM License, v. 2.0. If a copy of the MPL was not distributed with this
REM file, You can obtain one at https://mozilla.org/MPL/2.0/.

REM Usage: deploy_release.bat <component>   where <component> is "console" or "loader"
SET COMPONENT=%~1
IF "%COMPONENT%"=="" (
    ECHO ERROR: missing component argument ^(expected "console" or "loader"^)
    EXIT /b 1
)

REM needs to be called such that software installed
REM by Chocolatey in prepare.bat is accessible
CALL refreshenv

ECHO Building and deploying windows %COMPONENT% release package...
SET DEPLOY_ARTIFACT_USERNAME=%REPO_TYPEDB_USERNAME%
SET DEPLOY_ARTIFACT_PASSWORD=%REPO_TYPEDB_PASSWORD%

REM Bazel binary must produce a `.exe` instead of a binary without a file extension, otherwise windows cannot launch the binary
git apply .github\windows\package_binary_as_exe.patch

SET /p VER=<VERSION
bazel --output_user_root=C:/b run --verbose_failures --enable_runfiles --define version=%VER% //%COMPONENT%:deploy-windows-x86_64-zip --compilation_mode=opt -- release
IF %errorlevel% NEQ 0 EXIT /b %errorlevel%
