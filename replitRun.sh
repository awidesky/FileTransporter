if [ -d "bin" ]; then
    rm -r bin
fi
mkdir bin

javac -d bin src/*/*.java
cd bin

printf "Main-Class: main.Main\n" >> Manifest.txt
jar cfm FileTransporter.jar Manifest.txt ./*/*.class

java -jar FileTransporter.jar
#java bin bin/main/Main.class
#javac -cp "src" "src/main/Main"
#javac $(find . -name "src/*/*.java") -d bin
