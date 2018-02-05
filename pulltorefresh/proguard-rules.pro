# Add project specific ProGuard rules here.
# By default, the flags in this file are appended to flags specified
# in /opt/AndroidSDK/tools/proguard/proguard-android.txt
# You can edit the include path and order by changing the proguardFiles
# directive in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# Add any project specific keep options here:

# pulltorefresh and lottie
-dontnote com.airbnb.lottie.LottieAnimationView
-dontnote com.fletech.android.pulltorefresh.**
#-keep,includedescriptorclasses class com.airbnb.lottie.LottieAnimationView { *; }
#-keep,includedescriptorclasses class com.fletech.android.pulltorefresh.** { *; }

# kotlin
-dontwarn kotlin.reflect.jvm.internal.KClassImpl
-dontwarn kotlin.reflect.jvm.internal.impl.protobuf.SmallSortedMap
-dontnote kotlin.internal.jdk8.JDK8PlatformImplementations
-dontnote kotlin.internal.JRE8PlatformImplementations
-dontnote kotlin.internal.jdk7.JDK7PlatformImplementations
-dontnote kotlin.internal.JRE7PlatformImplementations

