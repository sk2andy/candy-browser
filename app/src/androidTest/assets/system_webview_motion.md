# System WebView motion fixture

`system_webview_motion.webm` is a generated 20-second, silent VP8 test pattern. It contains moving pixels so playback tests can verify decoded frame callbacks and changing image data independently of the media clock. No third-party media is included.

Generate from the repository root with FFmpeg:

```sh
ffmpeg -hide_banner -loglevel error -f lavfi -i testsrc2=size=96x54:rate=15 -t 20 -an -c:v libvpx -deadline best -b:v 30k -pix_fmt yuv420p -fflags +bitexact -flags:v +bitexact -map_metadata -1 -y app/src/androidTest/assets/system_webview_motion.webm
```

FFmpeg 9.0.1 / libvpx 1.16.0 produced SHA-256
`203cc935e0899d35bfe3e4c447507222e0afadf5beb7b67f93d8010aee1aa6a3`.
Two consecutive generations produced identical bytes.
