@echo off
setlocal
rem 命令行 JAVA_HOME 默认为空，PATH 里是 JDK 25，AGP 8.13 只支持到 17/21，这里显式钉住 21
set "JAVA_HOME=C:\Program Files\Java\jdk-21.0.11"
cd /d "%~dp0"
call gradlew.bat --console=plain %*
endlocal
