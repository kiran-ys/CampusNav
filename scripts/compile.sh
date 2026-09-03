#!/bin/sh
set -eu
mkdir -p out/main
find src/main/java -name '*.java' -print | sort > out/main-sources.txt
javac --release 17 -encoding UTF-8 -d out/main @out/main-sources.txt
