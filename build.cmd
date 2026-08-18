@echo off
set JAVA_HOME=C:\Program Files\Java\jdk-22
echo Building TRAI Audit Web Application...
echo JAVA_HOME=%JAVA_HOME%
call mvnw.cmd clean package -DskipTests
