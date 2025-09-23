#!/bin/sh
set -e

usage() {
    cat <<HELP_USAGE
    Usage: $(basename $0) <sdkRepositoryPath> 
        sdkRepositoryPath: path where the android-communications repository is cloned in"
HELP_USAGE
}

if [ $# -eq 0 ]; then
    echo "\nERROR: No arguments provided\n"
    usage
    exit 1
fi

REPOSITORIES_PATH=$1
cd $REPOSITORIES_PATH

# clean up
rm -f $REPOSITORIES_PATH/SdkBuild/polar-ble-sdk.aar
$REPOSITORIES_PATH/gradlew clean

# build
$REPOSITORIES_PATH/gradlew assembleSdkRelease
mkdir -p $REPOSITORIES_PATH/SdkBuild
cp $REPOSITORIES_PATH/library/build/outputs/aar/library-sdk-release.aar $REPOSITORIES_PATH/SdkBuild/polar-ble-sdk.aar