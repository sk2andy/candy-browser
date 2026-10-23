-keep class dev.sk2andy.materialbrowser.pocket.NativePocketSpeech { *; }
-keep interface dev.sk2andy.materialbrowser.pocket.NativePocketSpeech$Sink { *; }
-keepclassmembers class * implements dev.sk2andy.materialbrowser.pocket.NativePocketSpeech$Sink {
    public boolean onAudio(float[]);
    public boolean isCancelled();
}
