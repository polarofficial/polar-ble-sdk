#!/bin/bash
if [[ "$(gem list '^jazzy$' -i)" == "false" ]]; then
    echo "No Jazzy installed"
    sudo gem install jazzy
fi

cd ..
# Jazzy reads excluded directories from ".jazzy.yaml"
jazzy --clean --podspec PolarBleSdk.podspec