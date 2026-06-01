#!/bin/sh
set -e

ANDROID_SDK_PATH="~/repos/polar-ble-sdk/sources/Android/android-communications/"

while true; do
    read -p "\n Would you like to release v$SDK_VERSION-java17, too? (Y/N): : " selection
    case "$selection" in
        [Yy]* ) RELEASE_JAVA="true"; break ;;
        [Nn]* ) RELEASE_JAVA="false"; break ;;
        * ) echo "Please answer Y(y) or N(n)." ;;
    esac
done

if [ -n $RELEASE_JAVA ] && [[ $RELEASE_JAVA == "true" ]]; then

    echo "\nPreparing release for Java 17 branch of android-communications library..."
    git show-ref --verify --quiet refs/heads/master-java17
    if [ ! $? == 0 ]; then
        echo "Branch master-java17 does not exist in android-communications repository. I'll create it."
        sed -i '' "s/.JavaVersion.VERSION_21/ JavaVersion.VERSION_17/" $ANDROID_SDK_PATH/library/build.gradle
        git add -A
        git commit -m "Prepare master-java17 branch for Java 17 release"
        git checkout -b master-java17
        #git push -u origin master-java17
    else
        echo "Branch master-java17 already exists in android-communications repository. I'll use it for Java 17 release preparations."
        git checkout master-java17
        git merge master master-java17
        git checkout master
    fi
fi
