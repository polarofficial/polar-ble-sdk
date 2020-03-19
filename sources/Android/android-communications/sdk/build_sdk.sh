#!/bin/sh
set -e
if [ $# -ne 2 ]
  then
    echo "Missing artifactory user or password"
    exit 1
fi
cd ..
gradle assembleRelease androidJavadocsJar -PbuildSdk
cp build/outputs/aar/android-communications-release.aar sdk/polar-ble-sdk.aar
cp build/outputs/aar/android-communications-release.aar sdk/androidBleSdkTestApp/app/libs/polar-ble-sdk.aar
cp libs/polar-protobuf-release.aar sdk/polar-protobuf-release.aar
cp libs/polar-protobuf-release.aar sdk/androidBleSdkTestApp/app/libs/polar-protobuf-release.aar
rm -fr sdk/docs/
mkdir sdk/docs/
cp build/libs/android-communications-javadoc.jar sdk/docs/polar-ble-sdk-docs.jar
cp -r doxygen/ sdk/docs/
cd sdk

find . -name '.DS_Store' -type f -delete
zip -r polar-ble-sdk.zip androidBleSdkTestApp README docs polar-ble-sdk.aar polar-protobuf-release.aar Polar_SDK_License
echo "sdk zip created"
gradle artifactoryPublish -PartifactoryUser=$1 -PartifactoryPassword=$2
rm polar-ble-sdk.zip
