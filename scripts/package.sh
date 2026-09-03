#!/bin/sh
set -eu
/bin/sh "$(dirname "$0")/compile.sh"
mkdir -p dist
jar --create --file dist/campusnav.jar --main-class com.campusnav.app.Main -C out/main .
printf '%s\n' 'Created dist/campusnav.jar'
