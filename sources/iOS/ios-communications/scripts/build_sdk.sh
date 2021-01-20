#!/bin/sh
set -e

cd ..

XC_FRAMEWORK_OUTPUT_FOLDER="SdkBuild"
XC_FRAMEWORK_ARCHIVES_FOLDER="archives"

# Clean up before building
rm -fr $XC_FRAMEWORK_OUTPUT_FOLDER
rm -fr $XC_FRAMEWORK_ARCHIVES_FOLDER

# Create needed folders
mkdir $XC_FRAMEWORK_OUTPUT_FOLDER
mkdir $XC_FRAMEWORK_ARCHIVES_FOLDER

function archive {
    local SCHEME=$1
    local PLATFORM=$2
    local DESTINATION=${PLATFORM// /-}
    echo "Start Building scheme: $SCHEME for platform: $PLATFORM. Archive destination: $DESTINATION"; sleep 1
    xcodebuild archive \
    -scheme $SCHEME \
    -destination "generic/platform=$PLATFORM" \
    -archivePath "$XC_FRAMEWORK_ARCHIVES_FOLDER/$SCHEME-$DESTINATION" \
    SKIP_INSTALL=NO \
    BUILD_LIBRARY_FOR_DISTRIBUTION=YES
}

archive PolarBleSdk iOS
archive PolarBleSdk "iOS Simulator"
archive PolarBleSdkWatchOs watchOS
archive PolarBleSdkWatchOs "watchOS Simulator"

# Create XCFramework 
echo "Create XCFramework"; sleep 1
xcodebuild -create-xcframework \
-framework $XC_FRAMEWORK_ARCHIVES_FOLDER/PolarBleSdk-iOS.xcarchive/Products/Library/Frameworks/PolarBleSdk.framework \
-framework $XC_FRAMEWORK_ARCHIVES_FOLDER/PolarBleSdk-iOS-Simulator.xcarchive/Products/Library/Frameworks/PolarBleSdk.framework \
-framework $XC_FRAMEWORK_ARCHIVES_FOLDER/PolarBleSdkWatchOs-WatchOS.xcarchive/Products/Library/Frameworks/PolarBleSdkWatchOs.framework \
-framework $XC_FRAMEWORK_ARCHIVES_FOLDER/PolarBleSdkWatchOs-WatchOS-Simulator.xcarchive/Products/Library/Frameworks/PolarBleSdkWatchOs.framework \
-output $XC_FRAMEWORK_OUTPUT_FOLDER/PolarBleSdk.xcframework

# Clean up after building
rm -fr $XC_FRAMEWORK_ARCHIVES_FOLDER
