#!/bin/sh
set -e

ANDROID_SDK_PATH="~/repos/polar-ble-sdk/sources/Android/android-communications/"

echo "INSTRUCTIONS FOR MASTER BRANCH RELEASE: \n
        You will still have to done some manual steps you need to hurdle:\n
        1. Push all changes to polar-ble-sdk repository master branch in Github (git checkout master &&git add -A && git commit -m \""Release $SDK_VERSION"\" && git push)\n 
        2. Create new release tag to polar-ble-sdk repository in Github (git tag $SDK_VERSION && git push origin $SDK_VERSION)\n
        3. Trigger build in Cocoapods (cd $REPOSITORIES_PATH/polar-ble-sdk && pod trunk push PolarBleSdk.podspec)\n
        4. Check that android-sdk v$SDK_VERSION release is available at https://jitpack.io/#polarofficial/polar-ble-sdk\n
        5. Write release notes to github and publish release"

echo "\n\n"
while true; do
    read -p "Would you like to release v$SDK_VERSION-java17, too? (Y/N): : " selection
    case "$selection" in
        [Yy]* ) RELEASE_JAVA="true"; break ;;
        [Nn]* ) RELEASE_JAVA="false"; break ;;
        * ) echo "Please answer Y(y) or N(n)." ;;
    esac
done

if [ -n $RELEASE_JAVA ] && [[ $RELEASE_JAVA == "true" ]]; then

    echo "\nPreparing release for Java 17 branch of android-communications library..."
    git show-ref --quiet --branches master-java17
    if [ $? -ne 0 ]; then
        echo "Branch master-java17 does not exist in android-communications repository. I'll create it."
        sed -i '' "s/.JavaVersion.VERSION_21/ JavaVersion.VERSION_17/" $ANDROID_SDK_PATH/library/build.gradle
        git add -A
        git commit -m "Prepare master-java17 branch for Java 17 release"
        git checkout -b master-java17
       #  git push -u origin master-java17
    else
        echo "Branch master-java17 already exists in android-communications repository. I'll use it for Java 17 release preparations."
        git checkout master-java17
        git merge master master-java17
        git checkout master
    fi

    echo "INSTRUCTIONS FOR JAVA 17 BRANCH RELEASE: \n
        You will still have to done some manual steps you need to hurdle:\n
        1. Take master-java17 branch of polar-ble-sdk repository in Github (git checkout master-java17)\n 
        2. Create new release tag to polar-ble-sdk repository in Github (git tag $SDK_VERSION-java17 && git push origin $SDK_VERSION-java17)\n
        4. Check that android-sdk v$SDK_VERSION-java17 release is available at https://jitpack.io/#polarofficial/polar-ble-sdk\n
        5. For Java 17 release, there is no need to trigger build in Cocoapods as the iOS part of the SDK is not affected. \n
        6. Neither there is a need to write separate release notes for Java 17 release, as the java-17 changes are for internal use only (and same as changes in master branch).\n"
fi

echo "\n All done with preparing SDK release v$SDK_VERSION"
