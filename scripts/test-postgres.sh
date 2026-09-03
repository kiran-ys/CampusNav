#!/bin/sh
set -eu
/bin/sh "$(dirname "$0")/compile.sh"
mkdir -p out/test
find src/test/java -name '*.java' -print | sort > out/test-sources.txt
javac --release 17 -encoding UTF-8 -cp 'out/main:lib/*' -d out/test @out/test-sources.txt
java -ea -cp 'out/main:out/test:lib/*' com.campusnav.PostgresIntegrationTestSuite
