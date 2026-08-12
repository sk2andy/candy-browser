#!/usr/bin/env python3
"""Build the Candy Browser promotional poster from repository assets."""

from __future__ import annotations

from pathlib import Path

from PIL import Image, ImageDraw, ImageFilter, ImageFont


ROOT = Path(__file__).resolve().parents[1]
PROMO_DIR = ROOT / "docs" / "promo"
SCREENSHOT_DIR = ROOT / "docs" / "screenshots"
BACKGROUND = PROMO_DIR / "candy-promo-background.png"
OUTPUT = PROMO_DIR / "candy-browser-showcase.png"
LOGO = ROOT / "app" / "src" / "main" / "res" / "drawable-nodpi" / "ic_launcher_foreground_art.png"

WIDTH = 1600
HEIGHT = 1000

FONT_REGULAR = "/System/Library/Fonts/SFNS.ttf"
FONT_FALLBACK = "/System/Library/Fonts/Supplemental/Verdana.ttf"
FONT_FALLBACK_BOLD = "/System/Library/Fonts/Supplemental/Verdana Bold.ttf"

INK = "#20212B"
MUTED = "#555767"
PINK = "#FF2F78"
PURPLE = "#7457D7"


def font(size: int, *, weight: str = "regular") -> ImageFont.FreeTypeFont:
    candidates = [FONT_REGULAR, FONT_FALLBACK_BOLD if weight in {"semibold", "bold"} else FONT_FALLBACK]
    for index, candidate in enumerate(candidates):
        try:
            text_font = ImageFont.truetype(candidate, size=size)
            if index == 0:
                text_font.set_variation_by_name(weight.title())
            return text_font
        except OSError:
            continue
    return ImageFont.load_default(size=size)


def cover(image: Image.Image, size: tuple[int, int]) -> Image.Image:
    target_w, target_h = size
    scale = max(target_w / image.width, target_h / image.height)
    resized = image.resize((round(image.width * scale), round(image.height * scale)), Image.Resampling.LANCZOS)
    left = (resized.width - target_w) // 2
    top = (resized.height - target_h) // 2
    return resized.crop((left, top, left + target_w, top + target_h))


def rounded_image(image: Image.Image, size: tuple[int, int], radius: int) -> Image.Image:
    resized = cover(image.convert("RGBA"), size)
    mask = Image.new("L", size, 0)
    ImageDraw.Draw(mask).rounded_rectangle((0, 0, size[0] - 1, size[1] - 1), radius=radius, fill=255)
    resized.putalpha(mask)
    return resized


def phone(screen_path: Path, *, width: int = 276) -> Image.Image:
    screen = Image.open(screen_path).convert("RGBA")
    screen_h = round(width * screen.height / screen.width)
    bezel = 12
    shell = 5
    phone_w = width + 2 * (bezel + shell)
    phone_h = screen_h + 2 * (bezel + shell)
    layer = Image.new("RGBA", (phone_w + 44, phone_h + 44), (0, 0, 0, 0))

    shadow = Image.new("RGBA", layer.size, (0, 0, 0, 0))
    sd = ImageDraw.Draw(shadow)
    sd.rounded_rectangle((24, 25, phone_w + 20, phone_h + 21), radius=58, fill=(24, 20, 45, 105))
    shadow = shadow.filter(ImageFilter.GaussianBlur(16))
    layer.alpha_composite(shadow)

    draw = ImageDraw.Draw(layer)
    draw.rounded_rectangle((18, 18, phone_w + 17, phone_h + 17), radius=58, fill="#11121A")
    draw.rounded_rectangle((23, 23, phone_w + 12, phone_h + 12), radius=54, fill="#343746")

    clipped = rounded_image(screen, (width, screen_h), radius=30)
    layer.alpha_composite(clipped, (18 + bezel + shell, 18 + bezel + shell))

    # Quiet device details: side button and speaker slot.
    draw.rounded_rectangle((phone_w + 18, 164, phone_w + 22, 235), radius=2, fill="#2A2B35")
    draw.rounded_rectangle((phone_w // 2 - 27, 25, phone_w // 2 + 27, 29), radius=2, fill="#11121A")
    return layer


def paste_rotated(canvas: Image.Image, image: Image.Image, xy: tuple[int, int], angle: float) -> None:
    rotated = image.rotate(angle, resample=Image.Resampling.BICUBIC, expand=True)
    canvas.alpha_composite(rotated, xy)


def draw_logo(canvas: Image.Image, xy: tuple[int, int], diameter: int) -> None:
    x, y = xy
    shadow = Image.new("RGBA", canvas.size, (0, 0, 0, 0))
    ImageDraw.Draw(shadow).ellipse((x + 5, y + 10, x + diameter + 5, y + diameter + 10), fill=(66, 43, 120, 48))
    canvas.alpha_composite(shadow.filter(ImageFilter.GaussianBlur(18)))

    draw = ImageDraw.Draw(canvas)
    draw.ellipse((x, y, x + diameter, y + diameter), fill=(255, 255, 255, 238), outline=(255, 255, 255, 255), width=2)
    mark = Image.open(LOGO).convert("RGBA")
    mark.thumbnail((round(diameter * 0.66), round(diameter * 0.66)), Image.Resampling.LANCZOS)
    canvas.alpha_composite(mark, (x + (diameter - mark.width) // 2, y + (diameter - mark.height) // 2))


def chip(draw: ImageDraw.ImageDraw, xy: tuple[int, int], label: str, fill: str, text_fill: str) -> int:
    x, y = xy
    label_font = font(15, weight="semibold")
    bounds = draw.textbbox((0, 0), label, font=label_font)
    width = bounds[2] - bounds[0] + 28
    draw.rounded_rectangle((x, y, x + width, y + 34), radius=17, fill=fill)
    draw.text((x + 14, y + 7), label, font=label_font, fill=text_fill)
    return width


def feature_item(draw: ImageDraw.ImageDraw, xy: tuple[int, int], title: str, body: str, accent: str) -> None:
    x, y = xy
    draw.ellipse((x, y + 3, x + 28, y + 31), fill=accent)
    draw.ellipse((x + 9, y + 12, x + 19, y + 22), fill="white")
    draw.text((x + 42, y), title, font=font(15, weight="semibold"), fill=INK)
    draw.text((x + 42, y + 21), body, font=font(12), fill=MUTED)


def main() -> None:
    PROMO_DIR.mkdir(parents=True, exist_ok=True)
    background = cover(Image.open(BACKGROUND).convert("RGB"), (WIDTH, HEIGHT)).convert("RGBA")

    draw = ImageDraw.Draw(background)
    chip(draw, (64, 58), "ANDROID 14+  ·  OPEN SOURCE", "#ECE8FF", PURPLE)
    draw_logo(background, (64, 128), 108)
    draw.text((196, 143), "Candy Browser", font=font(40, weight="semibold"), fill=INK)
    draw.text((198, 190), "Gesture-first  ·  Privacy-first", font=font(18), fill=PURPLE)

    draw.multiline_text((64, 312), "Browse sweet.\nStay private.", font=font(54, weight="semibold"), fill=INK, spacing=5)
    draw.rounded_rectangle((64, 450, 138, 456), radius=3, fill=PINK)
    draw.multiline_text(
        (64, 490),
        "Powered by Android System WebView,\nwith local privacy tools, visual tabs\nand expressive edge-to-edge design.",
        font=font(21),
        fill=MUTED,
        spacing=7,
    )

    bullets = ["Visual tab journeys", "Privacy X-Ray", "Privacy tools stay local"]
    bullet_y = 624
    for index, label in enumerate(bullets):
        y = bullet_y + index * 38
        draw.ellipse((65, y + 7, 77, y + 19), fill=PINK if index == 0 else PURPLE)
        draw.text((92, y), label, font=font(19, weight="medium"), fill=INK)

    tabs = phone(SCREENSHOT_DIR / "candy-tabs.png", width=248)
    trail = phone(SCREENSHOT_DIR / "candy-trail.png", width=276)
    privacy = phone(SCREENSHOT_DIR / "candy-privacy.png", width=248)
    paste_rotated(background, tabs, (642, 105), -5.0)
    paste_rotated(background, privacy, (1240, 125), 5.0)
    paste_rotated(background, trail, (930, 64), 0.0)

    # Feature matrix. One panel keeps eight short claims readable without visual noise.
    draw.rounded_rectangle((48, 796, 1552, 966), radius=30, fill=(255, 255, 255, 220), outline=(255, 255, 255, 248), width=2)
    draw.text((72, 814), "BUILT FOR ANDROID · BUILT YOUR WAY", font=font(14, weight="semibold"), fill=PURPLE)
    draw.text((1278, 814), "github.com/sk2andy/candy-browser", font=font(13, weight="medium"), fill=INK)
    draw.line((72, 844, 1528, 844), fill=(116, 87, 215, 42), width=1)

    features = [
        ("WebView-powered", "Web engine updates arrive through System WebView.", PINK),
        ("Local by design", "History, favorites and privacy stats stay local.", PURPLE),
        ("Candy Trails", "Follow and fork one tab's click path.", PINK),
        ("Flexible tabs", "Pin tabs. Pick cover flow, grid or list.", PURPLE),
        ("Profile choices", "Use shared or isolated WebView storage.", PURPLE),
        ("Filter Studio", "Built-in blocking plus your own rules.", PINK),
        ("Address commands", "Clear cache, cookies or duplicate tabs.", PURPLE),
        ("Made to feel good", "Edge-to-edge UI, haptics and smooth motion.", PINK),
    ]
    columns = (72, 440, 808, 1176)
    rows = (856, 912)
    for index, (title, body, accent) in enumerate(features):
        feature_item(draw, (columns[index % 4], rows[index // 4]), title, body, accent)

    background.convert("RGB").save(OUTPUT, optimize=True, compress_level=9)
    print(OUTPUT)


if __name__ == "__main__":
    main()
