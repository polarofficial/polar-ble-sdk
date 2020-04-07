#!/bin/sh
set -e
WORKSPACE=${WORKSPACE:=`pwd`}
DERIVED_DATA=${DERIVED_DATA:=`mktemp -qd $WORKSPACE/DerivedData.XXXXX`}
echo "WORKSPACE:          $WORKSPACE"
echo "DERIVED_DATA:       $DERIVED_DATA"
function cleanup {
  echo "Cleaning up..."
  rm -fr $DERIVED_DATA
}
trap cleanup EXIT
cd $WORKSPACE
echo "Rebuilding projects..."; sleep 1
xcodebuild -scheme PolarBleSdk -configuration Release -sdk `xcrun --sdk iphoneos --show-sdk-path` -derivedDataPath $DERIVED_DATA ONLY_ACTIVE_ARCH=NO ARCHS="armv7 arm64" "OTHER_SWIFT_FLAGS=-DDISABLE_TEAM_PRO_DECRYPTION" CODE_SIGN_IDENTITY='' "OTHER_CFLAGS=-fembed-bitcode"
xcodebuild -scheme PolarBleSdk -configuration Release -sdk `xcrun --sdk iphonesimulator --show-sdk-path` -derivedDataPath $DERIVED_DATA ONLY_ACTIVE_ARCH=NO ARCHS="i386 x86_64" "OTHER_SWIFT_FLAGS=-DDISABLE_TEAM_PRO_DECRYPTION" CODE_SIGN_IDENTITY='' "OTHER_CFLAGS=-fembed-bitcode"
echo "Creating universal ios-communications..."
cd $DERIVED_DATA
lipo -create -output ../3rd_party_sdk/PolarBleSdk.framework/PolarBleSdk Build/Products/Release-iphoneos/PolarBleSdk.framework/PolarBleSdk Build/Products/Release-iphonesimulator/PolarBleSdk.framework/PolarBleSdk
echo "Copying swift modules..."
cp Build/Products/Release-iphoneos/PolarBleSdk.framework/Modules/PolarBleSdk.swiftmodule/*.swiftmodule ../3rd_party_sdk/PolarBleSdk.framework/Modules/PolarBleSdk.swiftmodule
cp Build/Products/Release-iphoneos/PolarBleSdk.framework/Modules/PolarBleSdk.swiftmodule/*.swiftdoc ../3rd_party_sdk/PolarBleSdk.framework/Modules/PolarBleSdk.swiftmodule
cp Build/Products/Release-iphonesimulator/PolarBleSdk.framework/Modules/PolarBleSdk.swiftmodule/*.swiftmodule ../3rd_party_sdk/PolarBleSdk.framework/Modules/PolarBleSdk.swiftmodule
cp Build/Products/Release-iphonesimulator/PolarBleSdk.framework/Modules/PolarBleSdk.swiftmodule/*.swiftdoc ../3rd_party_sdk/PolarBleSdk.framework/Modules/PolarBleSdk.swiftmodule
cp Build/Products/Release-iphoneos/PolarBleSdk.framework/Modules/module.modulemap ../3rd_party_sdk/PolarBleSdk.framework/Modules
# copy info list
cp Build/Products/Release-iphoneos/PolarBleSdk.framework/info.plist ../3rd_party_sdk/PolarBleSdk.framework/info.plist
cp -r Build/Products/Release-iphoneos/PolarBleSdk.framework/Headers ../3rd_party_sdk/PolarBleSdk.framework
sed -i -- 's/#import <PolarProtobuf.h>//' ../3rd_party_sdk/PolarBleSdk.framework/Headers/PolarBleSdk.h
rm ../3rd_party_sdk/PolarBleSdk.framework/Headers/PolarBleSdk.h--
sudo codesign --force --sign - ../3rd_party_sdk/PolarBleSdk.framework
sudo codesign --force --sign - ../3rd_party_sdk/PolarBleSdk.framework/PolarBleSdk
echo "=== release build ready ==="
cd ../3rd_party_sdk
