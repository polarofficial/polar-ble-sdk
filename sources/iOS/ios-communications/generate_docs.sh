sudo gem install jazzy
touch .jazzy.yaml
FILEPATH=${FILEPATH:=`pwd`}
echo "author: Polar Electro Oy">.jazzy.yaml
echo "author_url: https://www.polar.com/en\n">>.jazzy.yaml
echo "readme: $FILEPATH/README_public.md">>.jazzy.yaml
echo "xcodebuild_arguments:">>.jazzy.yaml
echo "- CODE_SIGNING_ALLOWED=NO">>.jazzy.yaml
echo " - -scheme">>.jazzy.yaml
echo " - PolarBleSdk\n">>.jazzy.yaml
echo "exclude:">>.jazzy.yaml
echo " - '$FILEPATH/iOSCommunications/ble/*'">>.jazzy.yaml
echo " - '$FILEPATH/PolarBleSdk/*'">>.jazzy.yaml
echo " - '$FILEPATH/iOSCommunications/sdk/impl/protobuf/*'">>.jazzy.yaml
echo " - '$FILEPATH/PolarBleSdkWatchOs/*'">>.jazzy.yaml
echo " - '$FILEPATH/build/*'">>.jazzy.yaml
echo " - '$FILEPATH/3rd_party_sdk/*'">>.jazzy.yaml
echo " - '$FILEPATH/Carthage/*'\n">>.jazzy.yaml
echo "clean: true">>.jazzy.yaml
echo "min_acl: public">>.jazzy.yaml
echo "skip_undocumented: true\n">>.jazzy.yaml
echo "custom_categories:">>.jazzy.yaml
echo " - name: API">>.jazzy.yaml
echo "   children:">>.jazzy.yaml
echo "   - PolarBleApi">>.jazzy.yaml
echo "   - PolarBleApiObserver">>.jazzy.yaml
echo "   - PolarBleApiDeviceFeaturesObserver">>.jazzy.yaml
echo "   - PolarBleApiDeviceHrObserver">>.jazzy.yaml
echo "   - PolarBleApiDeviceInfoObserver">>.jazzy.yaml
echo "   - PolarBleApiPowerStateObserver">>.jazzy.yaml
echo "   - PolarBleApiLogger">>.jazzy.yaml
echo "   - PolarSensorSetting">>.jazzy.yaml
echo " - name: PolarErrors">>.jazzy.yaml
echo "   children:">>.jazzy.yaml
echo "   - DateTimeFormatFailed">>.jazzy.yaml
echo "   - DeviceNotConnected">>.jazzy.yaml
echo "   - DeviceNotFound">>.jazzy.yaml
echo "   - MessageEncodeFailed">>.jazzy.yaml
echo "   - MessageDecodeFailed">>.jazzy.yaml
echo "   - NotificationNotEnabled">>.jazzy.yaml
echo "   - OperationNotSupported">>.jazzy.yaml
echo "   - ServiceNotFound">>.jazzy.yaml
echo "   - UnableToStartStreaming">>.jazzy.yaml
echo "   - InvalidArgument">>.jazzy.yaml
echo "   - UndefinedError">>.jazzy.yaml
echo " - name: API Default Implementation">>.jazzy.yaml
echo "   children:">>.jazzy.yaml
echo "   - PolarBleApiDefaultImpl">>.jazzy.yaml
jazzy
rm .jazzy.yaml


