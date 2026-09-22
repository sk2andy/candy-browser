# Promotional artwork

## Google Play assets

The Play campaign follows the public Candy website instead of introducing a separate visual system:
Nunito display type, `#FFF8FA` negative space, flat Material 3 rose/mint/lilac stages, matte Candy
shapes, straight product captures with rounded corners, and restrained burgundy drop shadows.

The feature graphic uses the same English brand line in every locale: **A sweeter way to browse.**
It includes the Candy logo, the single positioning line **Material 3 Expressive Web Browser**, and
a real-UI collage over a light motion background; individual feature copy stays in the carousel.

Run the deterministic compositor after changing copy, screenshots, or website design tokens:

```sh
python3 scripts/generate_play_store_assets.py
```

The script requires Pillow and uses the repository-owned `site/assets/fonts/Nunito-wght.ttf`. It
generates the complete bundle in a temporary directory, validates size, color mode, locale count,
and screenshot count, then replaces the upload assets.

| Asset | English | German | Output contract |
| --- | --- | --- | --- |
| Feature graphic | `fastlane/metadata/android/en-US/images/featureGraphic.png` | `fastlane/metadata/android/de-DE/images/featureGraphic.png` | 1024 × 500 RGB PNG |
| Phone screenshots | `fastlane/metadata/android/en-US/images/phoneScreenshots/` | `fastlane/metadata/android/de-DE/images/phoneScreenshots/` | 8 × 1080 × 1920 RGB PNG |
| Connected tab preview | — | `docs/promo/play-store-concepts/tabs-triptych.png` | 3240 × 1920 RGB PNG |

## Campaign order

| Order | Story | Visual |
| ---: | --- | --- |
| 1 | Firefox add-ons on Android | Friendly feature overview with uBlock Origin and cookie-banner blocking |
| 2 | Ad blocking and Privacy X-Ray | Real Privacy X-Ray capture with separate editorial copy for uBlock Origin |
| 3 | Cover flow tabs | Large real UI capture; first panel of a connected three-image panorama |
| 4 | Grid tabs | Large real UI capture; center panel of the connected panorama |
| 5 | List tabs | Large real UI capture; final panel of the connected panorama |
| 6 | Link Peek | Real Link Peek capture |
| 7 | Candy Trails | Real navigation-graph capture |
| 8 | Sync across devices | Friendly phone-to-computer feature overview |

The first two images emphasize Candy's clearest Play Store differentiators: Firefox add-ons managed
inside Candy and default ad/cookie protection. Screenshots 3–5 make every tab switcher style large
enough to understand at a glance while a shared stage and route connect them across the carousel.

Screenshots 1 and 8 are feature overviews derived from the website's add-on and sync stories. The
remaining product visuals use repository captures. `tabs-triptych.png` provides a single wide
preview of the three connected tab screenshots.

## Upload alt text

| Order | English alt text | German alt text |
| ---: | --- | --- |
| 1 | Candy Browser manages Firefox add-ons including uBlock Origin on Android. | Candy Browser verwaltet Firefox-Add-ons wie uBlock Origin auf Android. |
| 2 | Candy pairs uBlock Origin ad blocking with Privacy X-Ray inspection. | Candy kombiniert Werbeblockierung durch uBlock Origin mit Privacy-X-Ray-Analyse. |
| 3 | Candy Browser displays open tabs in a large Cover flow view. | Candy Browser zeigt offene Tabs in einer großen Cover-flow-Ansicht. |
| 4 | Candy Browser displays open tabs in a compact visual grid. | Candy Browser zeigt offene Tabs in einem kompakten visuellen Raster. |
| 5 | Candy Browser displays open tabs in a simple list. | Candy Browser zeigt offene Tabs in einer einfachen Liste. |
| 6 | Link Peek previews a web page above the current Candy Browser tab. | Link Peek zeigt eine Webseite über dem aktuellen Candy-Browser-Tab als Vorschau. |
| 7 | Candy Trail displays connected pages as a navigable graph. | Candy Trail zeigt verbundene Seiten als navigierbaren Graphen. |
| 8 | Candy keeps your tabs private and lets you continue browsing on your computer. | Candy hält deine Tabs privat und lässt dich am Computer weitersurfen. |

Google Play currently permits up to eight phone screenshots. These exports use the recommended 9:16
portrait format and keep product UI dominant in the carousel.
