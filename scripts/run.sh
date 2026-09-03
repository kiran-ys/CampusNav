#!/bin/sh
set -eu
/bin/sh "$(dirname "$0")/compile.sh"
java -cp 'out/main:lib/*' com.campusnav.app.Main
