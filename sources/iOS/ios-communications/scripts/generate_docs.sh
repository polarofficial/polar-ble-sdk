#!/bin/bash
if [[ "$(gem list '^jazzy$' -i)" == "false" ]]; then
    echo "No Jazzy installed"
    sudo gem install jazzy
fi

cd ..
jazzy \
  --clean \
  --build-tool-arguments -workspace,iOSCommunications.xcworkspace,-scheme,PolarBleSdk,-destination,"generic/platform=iOS" \
  --exclude=Sources/iOSCommunications/ble/*,Sources/PolarBleSdk/sdk/impl/protobuf/*
