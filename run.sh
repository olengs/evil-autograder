#!/bin/sh
set -e

mvn -q compile dependency:copy-dependencies -DincludeScope=runtime
exec java --enable-native-access=ALL-UNNAMED -cp 'target/classes:target/dependency/*' app.TerminalMain "$@"
