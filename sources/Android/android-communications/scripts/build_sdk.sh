#!/bin/sh
set -e
# clean up
rm -f polar-ble-sdk.aar

cd ..
./gradlew clean
./gradlew assembleSdkRelease
mkdir -p SdkBuild
cp build/outputs/aar/android-communications-sdk-release.aar SdkBuild/polar-ble-sdk.aar
