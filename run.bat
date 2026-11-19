@echo off
REM Build all Java files into bin
if not exist "bin" mkdir bin
javac -d bin src\processx\*.java src\processx\agile\*.java
if %ERRORLEVEL% NEQ 0 (
    echo Compilation failed!
    pause
    exit /b %ERRORLEVEL%
)

if "%1"=="" (
    java -cp bin processx.Main
) else if "%1"=="--test" (
    java -cp bin processx.Main --test
) else if "%1"=="--agile" (
    java -cp bin processx.agile.AgileTddRunner
) else (
    java -cp bin processx.Main %*
)
