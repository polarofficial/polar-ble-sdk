#!/bin/sh
set -e
# clean up
rm -f polar-ble-sdk.aar

cd ..
./gradlew clean
./gradlew assembleRelease
cp build/outputs/aar/android-communications-release.aar SdkBuild/polar-ble-sdk.aar
cp libs/polar-protobuf-release.aar SdkBuild/polar-protobuf-release.aar
