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
xcodebuild -scheme PolarBleSdkWatchOs -configuration Release -sdk `xcrun --sdk watchos --show-sdk-path` -derivedDataPath $DERIVED_DATA ONLY_ACTIVE_ARCH=NO ARCHS="armv7k arm64_32" CODE_SIGN_IDENTITY='' 
xcodebuild -scheme PolarBleSdkWatchOs -configuration Release -sdk `xcrun --sdk watchsimulator --show-sdk-path` -derivedDataPath $DERIVED_DATA ONLY_ACTIVE_ARCH=NO ARCHS="i386 x86_64" CODE_SIGN_IDENTITY=''
echo "Creating universal watchos framework"
cd $DERIVED_DATA
lipo -create -output ../3rd_party_sdk/PolarBleSdkWatchOs.framework/PolarBleSdkWatchOs Build/Products/Release-watchos/PolarBleSdkWatchOs.framework/PolarBleSdkWatchOs Build/Products/Release-watchsimulator/PolarBleSdkWatchOs.framework/PolarBleSdkWatchOs
echo "Copying swift modules..."
cp Build/Products/Release-watchos/PolarBleSdkWatchOs.framework/Modules/PolarBleSdkWatchOs.swiftmodule/*.swiftmodule ../3rd_party_sdk/PolarBleSdkWatchOs.framework/Modules/PolarBleSdkWatchOs.swiftmodule
cp Build/Products/Release-watchos/PolarBleSdkWatchOs.framework/Modules/PolarBleSdkWatchOs.swiftmodule/*.swiftdoc ../3rd_party_sdk/PolarBleSdkWatchOs.framework/Modules/PolarBleSdkWatchOs.swiftmodule
cp Build/Products/Release-watchsimulator/PolarBleSdkWatchOs.framework/Modules/PolarBleSdkWatchOs.swiftmodule/*.swiftmodule ../3rd_party_sdk/PolarBleSdkWatchOs.framework/Modules/PolarBleSdkWatchOs.swiftmodule
cp Build/Products/Release-watchsimulator/PolarBleSdkWatchOs.framework/Modules/PolarBleSdkWatchOs.swiftmodule/*.swiftdoc ../3rd_party_sdk/PolarBleSdkWatchOs.framework/Modules/PolarBleSdkWatchOs.swiftmodule
cp Build/Products/Release-watchos/PolarBleSdkWatchOs.framework/Modules/module.modulemap ../3rd_party_sdk/PolarBleSdkWatchOs.framework/Modules

# copy info list
cp Build/Products/Release-watchos/PolarBleSdkWatchOs.framework/info.plist ../3rd_party_sdk/PolarBleSdkWatchOs.framework/info.plist
cp -r Build/Products/Release-watchos/PolarBleSdkWatchOs.framework/Headers ../3rd_party_sdk/PolarBleSdkWatchOs.framework
sed -i -- 's/#import <PolarProtobuf.h>//' ../3rd_party_sdk/PolarBleSdkWatchOs.framework/Headers/PolarBleSdkWatchOs.h
rm ../3rd_party_sdk/PolarBleSdkWatchOs.framework/Headers/PolarBleSdkWatchOs.h--
sudo codesign --force --sign - ../3rd_party_sdk/PolarBleSdkWatchOs.framework
sudo codesign --force --sign - ../3rd_party_sdk/PolarBleSdkWatchOs.framework/PolarBleSdkWatchOs
echo "=== release build ready ==="
cd ../3rd_party_sdk
