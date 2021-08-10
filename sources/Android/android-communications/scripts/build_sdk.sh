#!/bin/sh
set -e

cd ..
# clean up
rm -f SdkBuild/polar-ble-sdk.aar
./gradlew clean

# build
./gradlew assembleSdkRelease
mkdir -p SdkBuild
cp library/build/outputs/aar/library-sdk-release.aar SdkBuild/polar-ble-sdk.aar
