mvn -q compile dependency:copy-dependencies -DincludeScope=runtime &&
java --enable-native-access=ALL-UNNAMED -cp 'target/classes:target/dependency/*' app.Main "$@"
