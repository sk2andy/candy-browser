# Candy Sync extension icons

These checked-in PNGs are deterministic size variants of Candy Browser's repository-owned Android
foreground artwork at `app/src/main/res/drawable-nodpi/ic_launcher_foreground_art.png`.

The source artwork is center-cropped to 320×320 pixels before resizing to 16, 32, 48, 96, and 128
pixels. The crop gives the mark appropriate visual weight while preserving its original geometry,
colors, transparency, and clear space. No generative image processing is used.

`tests/build.test.mjs` locks the reviewed source and output files by SHA-256. An intentional artwork
change therefore requires regenerating every size and updating the hashes in the same review.
