if [ ! -d "target" ]; then
    mkdir target/
fi
javac -d target/classes -sourcepath src src/app/Main.java