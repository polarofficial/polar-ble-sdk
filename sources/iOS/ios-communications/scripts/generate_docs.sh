#!/bin/bash
if [[ "$(gem list '^jazzy$' -i)" == "false" ]]; then
    echo "No Jazzy installed"
    sudo gem install jazzy
fi

cd ..
jazzy --build-tool-arguments -target,PolarBleSdk --exclude=iOSCommunications/ble/*,iOSCommunications/sdk/impl/protobuf/*
