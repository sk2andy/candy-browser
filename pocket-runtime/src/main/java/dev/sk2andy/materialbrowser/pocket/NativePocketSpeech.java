package dev.sk2andy.materialbrowser.pocket;

/** Native inference boundary. Create, synthesize and destroy on the same owned worker. */
public final class NativePocketSpeech {
    static {
        System.loadLibrary("onnxruntime");
        System.loadLibrary("candy_pocket");
    }

    private NativePocketSpeech() {}

    public interface Sink {
        boolean onAudio(float[] samples);
        boolean isCancelled();
    }

    public static native long create(String directory, String voice);
    public static native boolean synthesize(long handle, byte[] utf8Text, Sink sink);
    public static native void destroy(long handle);
}
