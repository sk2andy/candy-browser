# WebView callbacks are framework subclasses and require no custom keep rules.

# ML Kit discovers Firebase component registrars from manifest metadata and constructs them by
# reflection. R8 full mode otherwise removes their public no-arg constructors in local releases.
-keep class * implements com.google.firebase.components.ComponentRegistrar {
    public <init>();
}
