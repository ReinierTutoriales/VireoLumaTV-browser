# Add project specific ProGuard rules here.
# By default, the flags in this file are appended to flags specified
# in the default proguard-android-optimize.txt file.
# You can edit the include path and order by changing the proguardFiles
# directive in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# Add any project specific keep options here:

-keep class com.reiniertutoriales.vireolumatv.webengine.webview.WebViewWebEngine { *; }

-keepclassmembers class com.reiniertutoriales.vireolumatv.model.** {
   public *;
}
-keepclassmembers class com.reiniertutoriales.vireolumatv.webengine.webview.AndroidJSInterface {
   public *;
   private *;
}

-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
}

#-keepclasseswithmembers class com.reiniertutoriales.vireolumatv.model.** {
#    <fields>;
#}