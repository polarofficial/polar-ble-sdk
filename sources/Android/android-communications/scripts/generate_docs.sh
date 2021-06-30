#!/bin/sh
set -e

cd ..
./gradlew clean
./gradlew dokkaJavadoc
