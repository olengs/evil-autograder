@echo off
call mvn -q compile dependency:copy-dependencies -DincludeScope=runtime
if errorlevel 1 exit /b %errorlevel%
java --enable-native-access=ALL-UNNAMED -cp "target/classes;target/dependency/*" app.TerminalMain %*
exit /b %errorlevel%
