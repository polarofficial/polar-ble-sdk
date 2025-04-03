# Add project specific ProGuard rules here.
# By default, the flags in this file are appended to flags specified
# in /Users/esilvola/Library/Android/sdk/tools/proguard/proguard-android.txt
# You can edit the include path and order by changing the proguardFiles
# directive in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# Add any project specific keep options here:

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}
-dontwarn rx.internal.util.**
-dontwarn com.google.protobuf.**
-keep class fi.polar.remote.representation.protobuf.** {public private protected *;}
-keep class protocol.** {public private protected *;}
-keep class data.** {public private protected *;}
-keep class com.androidcommunications.polar.api.ble.model.** {public private protected *;}
-keep class com.androidcommunications.polar.enpoints.ble.bluedroid.host.**
#-keep class com.polar.sdk.**
#-keep class com.polar.polarsensordatacollector.**

#-renamesourcefileattribute SourceFile
#-keepattributes SourceFile,LineNumberTable
#-printmapping outputfile.txt