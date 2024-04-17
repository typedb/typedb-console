@echo off
REM This Source Code Form is subject to the terms of the Mozilla Public
REM License, v. 2.0. If a copy of the MPL was not distributed with this
REM file, You can obtain one at https://mozilla.org/MPL/2.0/.

REM needs to be called such that software installed
REM by Chocolatey in prepare.bat is accessible
CALL refreshenv

ECHO Building and deploying windows package...
SET DEPLOY_ARTIFACT_USERNAME=%REPO_TYPEDB_USERNAME%
SET DEPLOY_ARTIFACT_PASSWORD=%REPO_TYPEDB_PASSWORD%

SET /p VER=<VERSION
bazel --output_user_root=C:/b run --verbose_failures --define version=%VER% //:deploy-windows-x86_64-zip --compilation_mode=opt -- release
IF %errorlevel% NEQ 0 EXIT /b %errorlevel%
