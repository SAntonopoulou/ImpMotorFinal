@echo off
javac -cp "lib\sqlite-jdbc-3.47.1.0.jar;lib\jbcrypt-0.4.jar" -d bin src\Main.java
java -cp "bin;lib\sqlite-jdbc-3.47.1.0.jar;lib\jbcrypt-0.4.jar" Main
