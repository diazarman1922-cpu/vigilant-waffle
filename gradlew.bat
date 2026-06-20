@echo off
set DIR=%~dp0
set WRAPPER_JAR=%DIR%gradle\wrapper\gradle-wrapper.jar
if exist "%WRAPPER_JAR%" (
  java -classpath "%WRAPPER_JAR%" org.gradle.wrapper.GradleWrapperMain %*
  exit /b %ERRORLEVEL%
)
gradle %*
exit /b %ERRORLEVEL%
