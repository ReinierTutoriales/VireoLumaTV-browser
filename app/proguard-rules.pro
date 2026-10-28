# Release shrinking rules beyond the defaults of proguard-android-optimize.txt.

# Pages call the JavaScript bridge by method name.
-keepclassmembers class com.reiniertutoriales.vireolumatv.webengine.webview.PageBridge {
    @android.webkit.JavascriptInterface public <methods>;
}

# ActiveModelsRepository creates each model through its no-argument constructor.
-keepclassmembers class * extends com.reiniertutoriales.vireolumatv.utils.activemodel.ActiveModel {
    <init>();
}

# Debug and verbose logging is dropped from release builds.
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
}
