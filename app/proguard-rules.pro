# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Uncomment this to preserve the line number information for
# debugging stack traces.
#-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile
-keep class org.yaml.snakeyaml.** {*; }
-dontwarn java.beans.BeanInfo
-dontwarn java.beans.FeatureDescriptor
-dontwarn java.beans.IntrospectionException
-dontwarn java.beans.Introspector
-dontwarn java.beans.PropertyDescriptor


# Keep all OpenCV classes
-keep class org.opencv.** { *; }

# Keep all native methods
-keepclassmembers class * {
    native <methods>;
}

# Keep Mat and CvType from being optimized
-keep class org.opencv.core.Mat { *; }
-keep class org.opencv.core.CvType { *; }

# Keep specific OpenCV utilities (optional, depending on your usage)
-keep class org.opencv.utils.** { *; }
