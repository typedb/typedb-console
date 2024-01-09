@echo off
REM Copyright (C) 2022 Vaticle
REM
REM  This program is free software: you can redistribute it and/or modify
REM  it under the terms of the GNU Affero General Public License as
REM  published by the Free Software Foundation, either version 3 of the
REM  License, or (at your option) any later version.
REM
REM  This program is distributed in the hope that it will be useful,
REM  but WITHOUT ANY WARRANTY; without even the implied warranty of
REM  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
REM  GNU Affero General Public License for more details.
REM
REM  You should have received a copy of the GNU Affero General Public License
REM  along with this program.  If not, see <https://www.gnu.org/licenses/>.
REM
REM

REM shorten the workspace name so that we can avoid the long path restriction
git apply .circleci\windows\short_workspace.patch

REM uninstall Java 12 installed by CircleCI
choco uninstall openjdk --limit-output --yes --no-progress

REM install dependencies needed for build
choco install .circleci\windows\dependencies.config  --limit-output --yes --no-progress

REM create a symlink python3.exe and make it available in %PATH%
mklink C:\Python311\python3.exe C:\Python311\python.exe
set PATH=%PATH%;C:\Python311

REM install runtime dependency for the build
C:\Python311\python.exe -m pip install wheel

REM permanently set variables for Bazel build
SETX BAZEL_SH "C:\Program Files\Git\usr\bin\bash.exe"
SETX BAZEL_PYTHON C:\Python311\python.exe
SETX BAZEL_VC "C:\Program Files (x86)\Microsoft Visual Studio\2022\BuildTools\VC"
