#!/bin/sh
set -e

cd ..

XC_FRAMEWORK_OUTPUT_FOLDER="sdkXcBuild"
XC_FRAMEWORK_ARCHIVES_FOLDER="archives"

# Clean up before building
rm -fr $XC_FRAMEWORK_OUTPUT_FOLDER
rm -fr $XC_FRAMEWORK_ARCHIVES_FOLDER

# Create needed folders
mkdir $XC_FRAMEWORK_OUTPUT_FOLDER
mkdir $XC_FRAMEWORK_ARCHIVES_FOLDER

# Archive takes 3 params
#
# 1st == SCHEME
# 2nd == destination
# 3rd == archivePath
function archive {
    echo "▸ Starts archiving the scheme: ${1} for destination: ${2};\n▸ Archive path: ${3}.xcarchive"
    xcodebuild clean archive \
    -workspace iOSCommunications.xcworkspace \
    -scheme ${1} \
    -destination "${2}" \
    -archivePath "${3}" \
    SKIP_INSTALL=NO \
    BUILD_LIBRARY_FOR_DISTRIBUTION=YES
}

archive PolarBleSdk "generic/platform=iOS" $XC_FRAMEWORK_ARCHIVES_FOLDER/PolarBleSdk-iOS
archive PolarBleSdk "generic/platform=iOS Simulator" $XC_FRAMEWORK_ARCHIVES_FOLDER/PolarBleSdk-iOS-Simulator
archive PolarBleSdkWatchOs "generic/platform=watchOS" $XC_FRAMEWORK_ARCHIVES_FOLDER/PolarBleSdkWatchOs-WatchOS
archive PolarBleSdkWatchOs "generic/platform=watchOS Simulator" $XC_FRAMEWORK_ARCHIVES_FOLDER/PolarBleSdkWatchOs-WatchOS-Simulator

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
