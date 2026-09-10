#!/usr/bin/env python3
"""Render the widescreen Candy Browser feature showcase from repository assets."""

from __future__ import annotations

import argparse
import functools
import math
import random
import shutil
import struct
import subprocess
import tempfile
import wave
from dataclasses import dataclass
from pathlib import Path

try:
    from PIL import Image, ImageDraw, ImageFilter, ImageFont
except ModuleNotFoundError as error:
    raise SystemExit("Pillow is required; install it with `python3 -m pip install Pillow`.") from error


ROOT = Path(__file__).resolve().parents[1]
OUTPUT_DIR = ROOT / "docs" / "promo" / "video"
SCREENSHOT_DIR = ROOT / "docs" / "screenshots"
SOURCE_DIR = OUTPUT_DIR / "source"
LOGO = ROOT / "app" / "src" / "main" / "res" / "drawable-nodpi" / "ic_launcher_foreground_art.png"

WIDTH = 1920
HEIGHT = 1080
FPS = 60
SOURCE_FPS = 30
DURATION = 30.4
TRANSITION = 0.34

FONT_REGULAR = "/System/Library/Fonts/SFNS.ttf"
FONT_FALLBACK = "/System/Library/Fonts/Supplemental/Verdana.ttf"
FONT_FALLBACK_BOLD = "/System/Library/Fonts/Supplemental/Verdana Bold.ttf"

INK = "#20212B"
MUTED = "#555767"
PINK = "#FF2F78"
PURPLE = "#7457D7"


@dataclass(frozen=True)
class Scene:
    start: float
    end: float
    kind: str


SCENES = (
    Scene(0.0, 1.6, "hook"),
    Scene(1.6, 4.0, "tabs"),
    Scene(4.0, 6.4, "engines_extensions"),
    Scene(6.4, 9.6, "sync"),
    Scene(9.6, 12.6, "peek"),
    Scene(12.6, 14.6, "topping_intro"),
    Scene(14.6, 18.1, "spoiler"),
    Scene(18.1, 21.4, "hackernews"),
    Scene(21.4, 25.0, "privacy"),
    Scene(25.0, 27.6, "more"),
    Scene(27.6, 30.4, "end"),
)

VIDEO_SOURCES = {
    "tabs": SOURCE_DIR / "tab-switcher-live.mp4",
    "peek": SOURCE_DIR / "link-peek-live.mp4",
    "spoiler": SOURCE_DIR / "spoilerfree-sports-live.mp4",
    "hackernews": SOURCE_DIR / "hacker-news-comfort-live.mp4",
    "privacy": SOURCE_DIR / "privacy-xray-live.mp4",
}
VIDEO_FRAME_PATHS: dict[str, tuple[Path, ...]] = {}

STATIC_SOURCES = {
    "reader": SCREENSHOT_DIR / "candy-reader.png",
    "trail": SCREENSHOT_DIR / "candy-trail.png",
    "tabs_cover": SCREENSHOT_DIR / "candy-tabs.png",
    "tabs_grid": SCREENSHOT_DIR / "candy-tabs-grid.png",
    "tabs_list": SCREENSHOT_DIR / "candy-tabs-list.png",
    "profile": SCREENSHOT_DIR / "candy-profile-creation.png",
    "commands": SCREENSHOT_DIR / "candy-commands.png",
}


@functools.lru_cache(maxsize=None)
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


def ease_out_quint(value: float) -> float:
    value = min(1.0, max(0.0, value))
    return 1.0 - (1.0 - value) ** 5


def ease_in_out(value: float) -> float:
    value = min(1.0, max(0.0, value))
    return value * value * (3.0 - 2.0 * value)


@functools.lru_cache(maxsize=1)
def gradient_base() -> Image.Image:
    top = (243, 241, 255)
    bottom = (238, 221, 255)
    strip = Image.new("RGB", (1, HEIGHT), top)
    pixels = strip.load()
    for y in range(HEIGHT):
        ratio = y / (HEIGHT - 1)
        color = tuple(round(top[index] * (1.0 - ratio) + bottom[index] * ratio) for index in range(3))
        pixels[0, y] = color
    return strip.resize((WIDTH, HEIGHT)).convert("RGBA")


def background(time: float, *, accent: str = PURPLE) -> Image.Image:
    image = gradient_base().copy()

    glow_scale = 4
    glow_size = (WIDTH // glow_scale, HEIGHT // glow_scale)
    glow = Image.new("RGBA", glow_size, (0, 0, 0, 0))
    draw = ImageDraw.Draw(glow)
    accent_rgb = tuple(int(accent[index : index + 2], 16) for index in (1, 3, 5))
    cx = round(280 + 110 * math.sin(time * 0.8))
    cy = round(210 + 80 * math.cos(time * 0.65))
    draw.ellipse(
        tuple(value // glow_scale for value in (cx - 260, cy - 260, cx + 260, cy + 260)),
        fill=accent_rgb + (70,),
    )
    cx2 = round(1650 + 100 * math.cos(time * 0.7))
    cy2 = round(850 + 70 * math.sin(time * 0.55))
    draw.ellipse(
        tuple(value // glow_scale for value in (cx2 - 300, cy2 - 300, cx2 + 300, cy2 + 300)),
        fill=(255, 47, 120, 55),
    )
    glow = glow.filter(ImageFilter.GaussianBlur(26)).resize(image.size, Image.Resampling.BILINEAR)
    image.alpha_composite(glow)

    draw = ImageDraw.Draw(image)
    for index in range(9):
        angle = time * (0.18 + index * 0.012) + index * 0.9
        x = round(WIDTH / 2 + math.cos(angle) * (740 + index * 18))
        y = round(HEIGHT / 2 + math.sin(angle * 1.1) * (390 + index * 7))
        radius = 3 + index % 3
        draw.ellipse((x - radius, y - radius, x + radius, y + radius), fill=(255, 255, 255, 145))
    return image


def rounded_screen_image(screen: Image.Image, width: int) -> Image.Image:
    height = round(width * screen.height / screen.width)
    screen = screen.resize((width, height), Image.Resampling.LANCZOS)
    mask = Image.new("L", screen.size, 0)
    ImageDraw.Draw(mask).rounded_rectangle((0, 0, width - 1, height - 1), radius=38, fill=255)
    screen.putalpha(mask)
    return screen


def phone_image(screen: Image.Image, *, width: int = 390) -> Image.Image:
    screen = rounded_screen_image(screen, width)
    bezel = 15
    shadow_padding = 42
    phone_width = screen.width + bezel * 2
    phone_height = screen.height + bezel * 2
    layer = Image.new(
        "RGBA",
        (phone_width + shadow_padding * 2, phone_height + shadow_padding * 2),
        (0, 0, 0, 0),
    )

    shadow = Image.new("RGBA", layer.size, (0, 0, 0, 0))
    shadow_draw = ImageDraw.Draw(shadow)
    shadow_draw.rounded_rectangle(
        (
            shadow_padding + 4,
            shadow_padding + 12,
            shadow_padding + phone_width + 4,
            shadow_padding + phone_height + 12,
        ),
        radius=56,
        fill=(31, 24, 62, 100),
    )
    layer.alpha_composite(shadow.filter(ImageFilter.GaussianBlur(24)))

    draw = ImageDraw.Draw(layer)
    phone_bounds = (
        shadow_padding,
        shadow_padding,
        shadow_padding + phone_width,
        shadow_padding + phone_height,
    )
    draw.rounded_rectangle(
        phone_bounds,
        radius=58,
        fill="#0E0F15",
    )
    layer.alpha_composite(screen, (shadow_padding + bezel, shadow_padding + bezel))

    # Draw hardware above the recording so the live capture reads as a physical device.
    draw = ImageDraw.Draw(layer)
    draw.rounded_rectangle(phone_bounds, radius=58, outline=(8, 9, 14, 255), width=9)
    draw.rounded_rectangle(
        tuple(value + inset for value, inset in zip(phone_bounds, (7, 7, -7, -7))),
        radius=51,
        outline=(255, 255, 255, 72),
        width=2,
    )

    center_x = shadow_padding + phone_width // 2
    camera_y = shadow_padding + bezel + 14
    draw.ellipse(
        (center_x - 12, camera_y - 12, center_x + 12, camera_y + 12),
        fill=(5, 6, 9, 255),
        outline=(48, 51, 62, 255),
        width=3,
    )
    draw.ellipse(
        (center_x - 4, camera_y - 4, center_x + 4, camera_y + 4),
        fill=(40, 53, 75, 255),
    )

    right = shadow_padding + phone_width
    draw.rounded_rectangle(
        (right - 1, shadow_padding + 235, right + 7, shadow_padding + 330),
        radius=4,
        fill=(22, 23, 31, 255),
    )
    draw.rounded_rectangle(
        (right - 1, shadow_padding + 360, right + 7, shadow_padding + 426),
        radius=4,
        fill=(22, 23, 31, 255),
    )
    return layer


def paste_scaled(canvas: Image.Image, image: Image.Image, xy: tuple[int, int], scale: float, angle: float = 0.0) -> None:
    scaled = image.resize(
        (max(1, round(image.width * scale)), max(1, round(image.height * scale))),
        Image.Resampling.LANCZOS,
    )
    if angle:
        scaled = scaled.rotate(angle, resample=Image.Resampling.BICUBIC, expand=True)
    canvas.alpha_composite(scaled, xy)


def draw_chip(draw: ImageDraw.ImageDraw, xy: tuple[int, int], label: str, fill: str, ink: str) -> None:
    label_font = font(22, weight="semibold")
    bounds = draw.textbbox((0, 0), label, font=label_font)
    width = bounds[2] - bounds[0] + 38
    x, y = xy
    draw.rounded_rectangle((x, y, x + width, y + 48), radius=24, fill=fill)
    draw.text((x + 19, y + 11), label, font=label_font, fill=ink)


def draw_touch(canvas: Image.Image, xy: tuple[int, int], progress: float) -> None:
    if progress <= 0.0 or progress >= 1.0:
        return
    x, y = xy
    draw = ImageDraw.Draw(canvas)
    radius = round(18 + 56 * progress)
    alpha = round(210 * (1.0 - progress))
    draw.ellipse((x - radius, y - radius, x + radius, y + radius), outline=(255, 255, 255, alpha), width=6)
    draw.ellipse((x - 13, y - 13, x + 13, y + 13), fill=(255, 255, 255, min(235, alpha + 25)))


def draw_swipe(canvas: Image.Image, progress: float) -> None:
    if progress <= 0.0 or progress >= 1.0:
        return
    draw = ImageDraw.Draw(canvas)
    x = 1580
    start_y = 900
    end_y = 590
    y = round(start_y + (end_y - start_y) * ease_in_out(progress))
    draw.line((x, start_y, x, y), fill=(255, 255, 255, 170), width=9)
    draw.ellipse((x - 17, y - 17, x + 17, y + 17), fill=(255, 255, 255, 230))


def title(draw: ImageDraw.ImageDraw, text: str, xy: tuple[int, int], *, size: int = 67) -> None:
    draw.multiline_text(xy, text, font=font(size, weight="semibold"), fill=INK, spacing=1)


def render_hook(local: float, time: float) -> Image.Image:
    canvas = background(time, accent=PINK)
    draw = ImageDraw.Draw(canvas)
    chip_progress = ease_out_quint(local / 0.48)
    draw_chip(
        draw,
        (round(-465 + 575 * chip_progress), 64),
        "MATERIAL 3 EXPRESSIVE  ·  OPEN SOURCE",
        "#ECE8FF",
        PURPLE,
    )

    logo = Image.open(LOGO).convert("RGBA")
    logo_scale = 0.86 * ease_out_quint(local / 0.52)
    logo_size = max(1, round(320 * logo_scale))
    logo = logo.resize((logo_size, logo_size), Image.Resampling.LANCZOS)
    canvas.alpha_composite(logo, (250, 340 + round(18 * math.sin(local * 5.5))))

    alpha = round(255 * min(1.0, max(0.0, (local - 0.12) / 0.38)))
    text_layer = Image.new("RGBA", canvas.size, (0, 0, 0, 0))
    text_draw = ImageDraw.Draw(text_layer)
    heading_font = font(108, weight="semibold")
    heading = "Candy Browser"
    bounds = text_draw.textbbox((0, 0), heading, font=heading_font)
    reveal = ease_out_quint((local - 0.08) / 0.54)
    text_draw.text(
        (700, 385 + round(38 * (1.0 - reveal))),
        heading,
        font=heading_font,
        fill=(32, 33, 43, alpha),
    )
    strap = "Gesture-first. Privacy-first. Yours."
    strap_font = font(43, weight="medium")
    bounds = text_draw.textbbox((0, 0), strap, font=strap_font)
    text_draw.text(
        (706, 535 + round(28 * (1.0 - reveal))),
        strap,
        font=strap_font,
        fill=(116, 87, 215, alpha),
    )
    canvas.alpha_composite(text_layer)
    return canvas


def draw_glass_card(
    canvas: Image.Image,
    bounds: tuple[int, int, int, int],
    *,
    radius: int,
    fill: tuple[int, int, int, int] = (255, 255, 255, 232),
) -> None:
    x1, y1, x2, y2 = bounds
    shadow = Image.new("RGBA", canvas.size, (0, 0, 0, 0))
    shadow_draw = ImageDraw.Draw(shadow)
    shadow_draw.rounded_rectangle((x1 + 8, y1 + 14, x2 + 8, y2 + 14), radius=radius, fill=(39, 28, 77, 54))
    canvas.alpha_composite(shadow.filter(ImageFilter.GaussianBlur(18)))
    draw = ImageDraw.Draw(canvas)
    draw.rounded_rectangle(bounds, radius=radius, fill=fill, outline=(255, 255, 255, 220), width=2)


def draw_device_row(
    draw: ImageDraw.ImageDraw,
    *,
    y: int,
    monogram: str,
    label: str,
    detail: str,
    selected: bool,
) -> None:
    fill = (236, 231, 255, 255) if selected else (255, 255, 255, 0)
    draw.rounded_rectangle((890, y, 1145, y + 90), radius=28, fill=fill)
    draw.ellipse((910, y + 19, 962, y + 71), fill=PURPLE if selected else "#E8E4F4")
    monogram_font = font(22, weight="semibold")
    bounds = draw.textbbox((0, 0), monogram, font=monogram_font)
    draw.text((936 - (bounds[2] - bounds[0]) // 2, y + 33), monogram, font=monogram_font, fill="white" if selected else MUTED)
    draw.text((980, y + 18), label, font=font(22, weight="semibold"), fill=INK)
    draw.text((980, y + 51), detail, font=font(17, weight="medium"), fill=MUTED)
    draw.ellipse((1115, y + 36, 1129, y + 50), fill="#20B486")


def render_sync(local: float, time: float) -> Image.Image:
    canvas = background(time, accent=PURPLE)
    draw = ImageDraw.Draw(canvas)
    reveal = ease_out_quint(local / 0.62)
    draw_chip(
        draw,
        (round(-380 + 490 * ease_out_quint(local / 0.44)), 64),
        "NEW  ·  CROSS-DEVICE SYNC",
        "#FFFFFFE8",
        PURPLE,
    )
    draw.multiline_text(
        (105, 220 + round(48 * (1.0 - reveal))),
        "YOUR TABS.\nEVERY DEVICE.",
        font=font(78, weight="semibold"),
        fill=INK,
        spacing=0,
    )
    draw.multiline_text(
        (110, 480 + round(24 * (1.0 - reveal))),
        "Android  ↔  Chromium  ↔  Firefox\n\nOpen · navigate · pin · reorder · close",
        font=font(31, weight="medium"),
        fill=MUTED,
        spacing=7,
    )

    entrance = ease_out_quint((local - 0.08) / 0.72)
    desktop_x = round(855 + 760 * (1.0 - entrance))
    desktop_bounds = (desktop_x, 135, desktop_x + 880, 790)
    draw_glass_card(canvas, desktop_bounds, radius=42)
    draw = ImageDraw.Draw(canvas)
    draw.rounded_rectangle((desktop_x, 135, desktop_x + 880, 215), radius=42, fill=(245, 242, 255, 245))
    draw.rectangle((desktop_x, 175, desktop_x + 880, 215), fill=(245, 242, 255, 245))
    for index, color in enumerate(("#FF6484", "#FFBF4B", "#3ACB7D")):
        cx = desktop_x + 38 + index * 30
        draw.ellipse((cx - 8, 167, cx + 8, 183), fill=color)
    draw.rounded_rectangle((desktop_x + 150, 157, desktop_x + 690, 196), radius=20, fill="white")
    draw.text((desktop_x + 370, 166), "candy://synced-tabs", font=font(17, weight="medium"), fill=MUTED)

    draw.rounded_rectangle((desktop_x + 22, 232, desktop_x + 290, 765), radius=30, fill=(248, 246, 255, 238))
    draw.text((desktop_x + 50, 260), "SYNCED DEVICES", font=font(18, weight="semibold"), fill=PURPLE)
    row_offset = desktop_x - 855
    # Device rows use stable positions; translate their layer together with the desktop entrance.
    device_layer = Image.new("RGBA", canvas.size, (0, 0, 0, 0))
    device_draw = ImageDraw.Draw(device_layer)
    for y, monogram, label, detail, selected in (
        (315, "P", "Pixel 11 Pro", "Android · live", True),
        (415, "F", "Firefox", "Desktop · synced", False),
        (515, "C", "Chromium", "Work · synced", False),
    ):
        draw_device_row(device_draw, y=y, monogram=monogram, label=label, detail=detail, selected=selected)
    if row_offset:
        device_layer = device_layer.transform(
            device_layer.size,
            Image.Transform.AFFINE,
            (1, 0, -row_offset, 0, 1, 0),
            resample=Image.Resampling.BICUBIC,
        )
    canvas.alpha_composite(device_layer)
    draw = ImageDraw.Draw(canvas)

    content_left = desktop_x + 320
    draw.text((content_left, 260), "Pixel 11 Pro", font=font(34, weight="semibold"), fill=INK)
    draw.rounded_rectangle((content_left + 250, 266, content_left + 325, 302), radius=18, fill="#DDF8ED")
    draw.text((content_left + 269, 274), "LIVE", font=font(15, weight="semibold"), fill="#12845E")
    for index, (tab_title, host, accent) in enumerate((
        ("Candy Browser", "github.com", PINK),
        ("Android Developers", "developer.android.com", PURPLE),
        ("Reader Studio", "local reading view", "#00A9A5"),
    )):
        y = 335 + index * 116
        draw.rounded_rectangle((content_left, y, desktop_x + 830, y + 94), radius=28, fill=(250, 249, 255, 255))
        draw.rounded_rectangle((content_left + 18, y + 18, content_left + 30, y + 76), radius=6, fill=accent)
        draw.text((content_left + 52, y + 17), tab_title, font=font(25, weight="semibold"), fill=INK)
        draw.text((content_left + 52, y + 53), host, font=font(18, weight="medium"), fill=MUTED)

    badge_progress = ease_out_quint((local - 0.40) / 0.58)
    badge_x = round(760 + 70 * (1.0 - badge_progress))
    badge_y = 745
    badge_width = 410
    draw_glass_card(canvas, (badge_x, badge_y, badge_x + badge_width, badge_y + 142), radius=42, fill=(255, 255, 255, 246))
    draw = ImageDraw.Draw(canvas)
    draw.ellipse((badge_x + 24, badge_y + 30, badge_x + 106, badge_y + 112), fill=PURPLE)
    draw.arc((badge_x + 48, badge_y + 44, badge_x + 82, badge_y + 78), 180, 360, fill="white", width=6)
    draw.rounded_rectangle((badge_x + 43, badge_y + 65, badge_x + 87, badge_y + 98), radius=8, fill="white")
    draw.text((badge_x + 126, badge_y + 32), "SELF-HOSTED · E2EE", font=font(22, weight="semibold"), fill=INK)
    draw.text((badge_x + 126, badge_y + 72), "Docker Compose · your server", font=font(18, weight="medium"), fill=MUTED)

    pulse_progress = (local * 0.72) % 1.0
    pulse_radius = round(7 + 5 * math.sin(pulse_progress * math.pi))
    pulse_x = badge_x + badge_width - 28
    pulse_y = badge_y + 71
    draw.ellipse(
        (pulse_x - pulse_radius, pulse_y - pulse_radius, pulse_x + pulse_radius, pulse_y + pulse_radius),
        fill=PINK,
    )
    return canvas


def render_feature(
    local: float,
    time: float,
    *,
    screen: Image.Image,
    headline: str,
    body: str,
    chip_label: str,
    accent: str,
    side: str,
    touch: tuple[int, int] | None = None,
    swipe: bool = False,
) -> Image.Image:
    canvas = background(time, accent=accent)
    draw = ImageDraw.Draw(canvas)
    chip_progress = ease_out_quint(local / 0.44)
    chip_x = 110 if side == "right" else 940
    draw_chip(
        draw,
        (round(chip_x - 440 * (1.0 - chip_progress)), 64),
        chip_label,
        "#FFFFFFD9",
        accent,
    )

    device = phone_image(screen, width=390)
    entrance = ease_out_quint(local / 0.64)
    bob = math.sin(local * 3.2) * 5
    if side == "right":
        phone_x = round(1280 + (WIDTH + 80 - 1280) * (1.0 - entrance))
        text_x = 110
    else:
        phone_x = round(150 - (580 * (1.0 - entrance)))
        text_x = 940
    phone_y = round(38 + bob)
    paste_scaled(canvas, device, (phone_x, phone_y), 0.96, angle=-1.0 if side == "right" else 1.0)

    text_progress = ease_out_quint((local - 0.10) / 0.62)
    text_alpha = round(255 * text_progress)
    text_offset = round(52 * (1.0 - text_progress))
    text_layer = Image.new("RGBA", canvas.size, (0, 0, 0, 0))
    text_draw = ImageDraw.Draw(text_layer)
    text_draw.multiline_text(
        (text_x, 230 + text_offset),
        headline,
        font=font(75, weight="semibold"),
        fill=(32, 33, 43, text_alpha),
        spacing=0,
    )
    text_draw.multiline_text(
        (text_x, 515 + round(text_offset * 0.65)),
        body,
        font=font(35, weight="medium"),
        fill=(85, 87, 103, text_alpha),
        spacing=7,
    )
    accent_width = round(104 * text_progress)
    text_draw.rounded_rectangle((text_x, 690, text_x + accent_width, 700), radius=5, fill=accent)
    canvas.alpha_composite(text_layer)

    if touch is not None:
        draw_touch(canvas, touch, (local - 0.35) / 0.85)
    if swipe:
        draw_swipe(canvas, (local - 0.25) / 0.9)
    return canvas


def video_screen(kind: str, local: float) -> Image.Image:
    paths = VIDEO_FRAME_PATHS[kind]
    frame_index = min(len(paths) - 1, max(0, int(local * SOURCE_FPS + 1e-6)))
    return Image.open(paths[frame_index]).convert("RGBA")


@functools.lru_cache(maxsize=None)
def static_screen(kind: str) -> Image.Image:
    return Image.open(STATIC_SOURCES[kind]).convert("RGBA")


def render_tab_variants(local: float, time: float) -> Image.Image:
    canvas = background(time, accent=PURPLE)
    draw = ImageDraw.Draw(canvas)
    progress = ease_out_quint(local / 0.62)
    draw_chip(
        draw,
        (round(-330 + 384 * ease_out_quint(local / 0.44)), 52),
        "TAB SWITCHER · 3 VIEWS",
        "#FFFFFFD9",
        PURPLE,
    )
    heading_y = 150 + round(46 * (1.0 - progress))
    title(draw, "YOUR TABS. YOUR VIEW.", (54, heading_y), size=64)
    draw.text(
        (56, 245 + round(28 * (1.0 - progress))),
        "Cover flow, grid or compact list.",
        font=font(30, weight="medium"),
        fill=MUTED,
    )

    variants = (
        ("tabs_cover", "COVER", 12, 1.5),
        ("tabs_grid", "GRID", 365, -0.8),
        ("tabs_list", "LIST", 718, 1.2),
    )
    for index, (kind, label, x, angle) in enumerate(variants):
        entrance = ease_out_quint((local - index * 0.12) / 0.68)
        device = phone_image(static_screen(kind), width=270)
        y = round(365 + (HEIGHT + 100 - 365) * (1.0 - entrance))
        paste_scaled(canvas, device, (x, y), 0.96, angle=angle)
        label_font = font(23, weight="semibold")
        label_bounds = draw.textbbox((0, 0), label, font=label_font)
        label_x = x + (340 - (label_bounds[2] - label_bounds[0])) // 2
        draw.rounded_rectangle(
            (label_x - 18, 320, label_x + label_bounds[2] - label_bounds[0] + 18, 364),
            radius=22,
            fill=(255, 255, 255, 220),
        )
        draw.text((label_x, 330), label, font=label_font, fill=PURPLE)
    return canvas


def draw_feature_card(
    canvas: Image.Image,
    *,
    xy: tuple[int, int],
    label: str,
    detail: str,
    accent: str,
    progress: float,
    delay: float,
) -> None:
    reveal = ease_out_quint((progress - delay) / 0.38)
    if reveal <= 0.0:
        return
    x, y = xy
    y += round(55 * (1.0 - reveal))
    alpha = round(245 * reveal)
    layer = Image.new("RGBA", canvas.size, (0, 0, 0, 0))
    draw = ImageDraw.Draw(layer)
    draw.rounded_rectangle((x, y, x + 510, y + 170), radius=42, fill=(255, 255, 255, alpha))
    draw.rounded_rectangle((x + 24, y + 25, x + 36, y + 145), radius=6, fill=accent)
    draw.text((x + 68, y + 35), label, font=font(34, weight="semibold"), fill=(32, 33, 43, alpha))
    draw.text((x + 68, y + 92), detail, font=font(25, weight="medium"), fill=(85, 87, 103, alpha))
    canvas.alpha_composite(layer)


def render_topping_intro(local: float, time: float) -> Image.Image:
    canvas = background(time, accent=PURPLE)
    draw = ImageDraw.Draw(canvas)
    draw_chip(
        draw,
        (round(-360 + 470 * ease_out_quint(local / 0.42)), 64),
        "TOPPINGS · FOCUSED USER SCRIPTS",
        "#FFFFFFD9",
        PURPLE,
    )
    reveal = ease_out_quint(local / 0.62)
    draw.multiline_text(
        (110, 215 + round(45 * (1.0 - reveal))),
        "MAKE THE WEB\nTASTE BETTER.",
        font=font(88, weight="semibold"),
        fill=INK,
        spacing=0,
    )
    draw.text(
        (115, 480),
        "Install focused upgrades. Toggle them anytime.",
        font=font(37, weight="medium"),
        fill=MUTED,
    )
    draw_feature_card(
        canvas,
        xy=(1170, 180),
        label="Spoilerfree Sports",
        detail="Hide scores until you choose",
        accent=PINK,
        progress=local,
        delay=0.10,
    )
    draw_feature_card(
        canvas,
        xy=(1270, 410),
        label="Hacker News Comfort",
        detail="Readable cards · bigger targets",
        accent=PURPLE,
        progress=local,
        delay=0.22,
    )
    draw_feature_card(
        canvas,
        xy=(1170, 640),
        label="Privacy Toppings",
        detail="Clean links before you leave",
        accent="#00A9A5",
        progress=local,
        delay=0.34,
    )
    return canvas


def draw_engine_choice(
    canvas: Image.Image,
    *,
    y: int,
    title_text: str,
    detail: str,
    badge: str,
    selected: bool,
    progress: float,
    delay: float,
) -> None:
    reveal = ease_out_quint((progress - delay) / 0.42)
    if reveal <= 0.0:
        return
    x = 105
    y += round(55 * (1.0 - reveal))
    layer = Image.new("RGBA", canvas.size, (0, 0, 0, 0))
    draw = ImageDraw.Draw(layer)
    draw.rounded_rectangle(
        (x, y, x + 770, y + 174),
        radius=40,
        fill=(255, 255, 255, round(248 * reveal)),
        outline=PURPLE if selected else "#D7D0EA",
        width=4 if selected else 2,
    )
    draw.ellipse((x + 28, y + 39, x + 124, y + 135), fill=PURPLE if selected else "#ECE8F5")
    monogram = "G" if selected else "W"
    monogram_font = font(38, weight="semibold")
    bounds = draw.textbbox((0, 0), monogram, font=monogram_font)
    draw.text(
        (x + 76 - (bounds[2] - bounds[0]) // 2, y + 62),
        monogram,
        font=monogram_font,
        fill="white" if selected else MUTED,
    )
    draw.text((x + 150, y + 38), title_text, font=font(30, weight="semibold"), fill=INK)
    draw.text((x + 150, y + 91), detail, font=font(23, weight="medium"), fill=MUTED)
    badge_font = font(17, weight="semibold")
    badge_bounds = draw.textbbox((0, 0), badge, font=badge_font)
    badge_width = badge_bounds[2] - badge_bounds[0] + 30
    badge_left = x + 740 - badge_width
    draw.rounded_rectangle((badge_left, y + 18, badge_left + badge_width, y + 56), radius=19, fill="#ECE8FF")
    draw.text((badge_left + 15, y + 28), badge, font=badge_font, fill=PURPLE)
    canvas.alpha_composite(layer)


def draw_extension_row(
    canvas: Image.Image,
    *,
    x: int,
    y: int,
    monogram: str,
    label: str,
    detail: str,
    progress: float,
    delay: float,
) -> None:
    reveal = ease_out_quint((progress - delay) / 0.38)
    if reveal <= 0.0:
        return
    y += round(45 * (1.0 - reveal))
    layer = Image.new("RGBA", canvas.size, (0, 0, 0, 0))
    draw = ImageDraw.Draw(layer)
    draw.rounded_rectangle((x, y, x + 690, y + 156), radius=36, fill=(250, 249, 255, round(255 * reveal)))
    draw.ellipse((x + 26, y + 30, x + 122, y + 126), fill=PINK if monogram == "u" else "#FF9A62")
    monogram_font = font(38, weight="bold")
    bounds = draw.textbbox((0, 0), monogram, font=monogram_font)
    draw.text(
        (x + 74 - (bounds[2] - bounds[0]) // 2, y + 54),
        monogram,
        font=monogram_font,
        fill="white",
    )
    draw.text((x + 150, y + 30), label, font=font(26, weight="semibold"), fill=INK)
    draw.text((x + 150, y + 79), detail, font=font(21, weight="medium"), fill=MUTED)
    draw.rounded_rectangle((x + 566, y + 99, x + 650, y + 133), radius=17, fill="#DDF8ED")
    draw.text((x + 587, y + 108), "ON", font=font(16, weight="semibold"), fill="#12845E")
    canvas.alpha_composite(layer)


def render_engines_extensions(local: float, time: float) -> Image.Image:
    canvas = background(time, accent=PURPLE)
    draw = ImageDraw.Draw(canvas)
    draw_chip(
        draw,
        (round(-430 + 540 * ease_out_quint(local / 0.42)), 52),
        "ANDROID · TWO WEB ENGINES",
        "#FFFFFFD9",
        PURPLE,
    )
    reveal = ease_out_quint(local / 0.62)
    draw.multiline_text(
        (105, 145 + round(48 * (1.0 - reveal))),
        "PICK YOUR\nWEB ENGINE.",
        font=font(72, weight="semibold"),
        fill=INK,
        spacing=0,
    )
    draw.text(
        (110, 365),
        "Extensions or Android's system runtime. Your call.",
        font=font(30, weight="medium"),
        fill=MUTED,
    )
    draw_engine_choice(
        canvas,
        y=470,
        title_text="Extensions · GeckoView",
        detail="Firefox add-ons + Toppings",
        badge="DEFAULT",
        selected=True,
        progress=local,
        delay=0.10,
    )
    draw_engine_choice(
        canvas,
        y=680,
        title_text="Android System WebView",
        detail="Toppings + Candy protection",
        badge="SYSTEM",
        selected=False,
        progress=local,
        delay=0.22,
    )

    panel_progress = ease_out_quint((local - 0.08) / 0.62)
    panel_x = round(970 + 830 * (1.0 - panel_progress))
    draw_glass_card(canvas, (panel_x, 115, panel_x + 850, 940), radius=46, fill=(255, 255, 255, 238))
    draw = ImageDraw.Draw(canvas)
    draw.ellipse((panel_x + 55, 168, panel_x + 151, 264), fill="#FF7139")
    draw.text((panel_x + 88, 190), "F", font=font(43, weight="bold"), fill="white")
    draw.text((panel_x + 180, 164), "Firefox extensions", font=font(38, weight="semibold"), fill=INK)
    draw.text(
        (panel_x + 180, 220),
        "Mozilla-signed add-ons in Candy chrome",
        font=font(22, weight="medium"),
        fill=MUTED,
    )
    draw_extension_row(
        canvas,
        x=panel_x + 70,
        y=330,
        monogram="u",
        label="uBlock Origin",
        detail="Bundled default · Mozilla-signed",
        progress=local,
        delay=0.24,
    )
    draw_extension_row(
        canvas,
        x=panel_x + 70,
        y=525,
        monogram="C",
        label="I still don't care about cookies",
        detail="Bundled default · Mozilla-signed",
        progress=local,
        delay=0.36,
    )
    draw = ImageDraw.Draw(canvas)
    draw.rounded_rectangle((panel_x + 70, 785, panel_x + 780, 875), radius=45, fill=PURPLE)
    button_label = "INSTALL MOZILLA-SIGNED XPI"
    button_font = font(24, weight="semibold")
    button_bounds = draw.textbbox((0, 0), button_label, font=button_font)
    draw.text(
        (panel_x + 425 - (button_bounds[2] - button_bounds[0]) // 2, 817),
        button_label,
        font=button_font,
        fill="white",
    )
    return canvas


def render_more(local: float, time: float) -> Image.Image:
    canvas = background(time, accent=PINK)
    draw = ImageDraw.Draw(canvas)
    draw_chip(
        draw,
        (round(-330 + 440 * ease_out_quint(local / 0.44)), 64),
        "AND THAT'S NOT ALL",
        "#FFFFFFD9",
        PINK,
    )
    progress = ease_out_quint(local / 0.66)
    draw.multiline_text(
        (105, 235 + round(55 * (1.0 - progress))),
        "READER. TRAILS.\nPROFILES. COMMANDS.",
        font=font(76, weight="semibold"),
        fill=INK,
        spacing=4,
    )
    draw.text((110, 500), "And many more.", font=font(38, weight="semibold"), fill=PURPLE)

    reader = phone_image(static_screen("reader"), width=310)
    trail = phone_image(static_screen("trail"), width=310)
    entrance = ease_out_quint(local / 0.72)
    paste_scaled(canvas, reader, (1110, round(45 + 900 * (1.0 - entrance))), 0.96, angle=-3.0)
    paste_scaled(canvas, trail, (1420, round(30 + 900 * (1.0 - entrance))), 0.99, angle=2.4)
    return canvas


def render_end(local: float, time: float) -> Image.Image:
    canvas = background(time, accent=PINK)
    draw = ImageDraw.Draw(canvas)
    progress = ease_out_quint(local / 0.68)
    content_offset = round(46 * (1.0 - progress))

    logo = Image.open(LOGO).convert("RGBA")
    logo_size = max(1, round(265 * progress))
    logo = logo.resize((logo_size, logo_size), Image.Resampling.LANCZOS)
    canvas.alpha_composite(logo, (245, 360 + content_offset))

    heading_font = font(98, weight="semibold")
    heading = "Candy Browser"
    bounds = draw.textbbox((0, 0), heading, font=heading_font)
    draw.text((650, 310 + content_offset), heading, font=heading_font, fill=INK)
    strap = "Gesture-first. Privacy-first."
    strap_font = font(39, weight="medium")
    bounds = draw.textbbox((0, 0), strap, font=strap_font)
    draw.text((656, 450 + content_offset), strap, font=strap_font, fill=PURPLE)

    button_width = 690
    button_left = 650
    button_top = 575 + content_offset
    pulse = 1.0 + 0.012 * math.sin(local * 7.0)
    button_height = round(114 * pulse)
    draw.rounded_rectangle(
        (button_left, button_top, button_left + button_width, button_top + button_height),
        radius=57,
        fill=PINK,
    )
    cta = "GET IT ON GITHUB"
    cta_font = font(32, weight="semibold")
    bounds = draw.textbbox((0, 0), cta, font=cta_font)
    draw.text(
        (button_left + (button_width - bounds[2]) // 2, button_top + 36),
        cta,
        font=cta_font,
        fill="white",
    )
    foot = "github.com/sk2andy/candy-browser"
    foot_font = font(25, weight="medium")
    bounds = draw.textbbox((0, 0), foot, font=foot_font)
    draw.text((button_left + (button_width - bounds[2]) // 2, 735 + content_offset), foot, font=foot_font, fill=MUTED)
    chip_label = "ANDROID 13+  ·  MPL 2.0"
    chip_font = font(22, weight="semibold")
    chip_bounds = draw.textbbox((0, 0), chip_label, font=chip_font)
    chip_width = chip_bounds[2] - chip_bounds[0] + 38
    draw_chip(
        draw,
        (button_left + (button_width - chip_width) // 2, 835 + content_offset),
        chip_label,
        "#FFFFFFD9",
        PURPLE,
    )
    return canvas


def camera_move(
    image: Image.Image,
    *,
    scale: float,
    focus: tuple[float, float],
    drift: tuple[float, float] = (0.0, 0.0),
) -> Image.Image:
    if scale <= 1.0001 and drift == (0.0, 0.0):
        return image
    scaled = image.resize(
        (round(WIDTH * scale), round(HEIGHT * scale)),
        Image.Resampling.BICUBIC,
    )
    focus_x, focus_y = focus
    left = round(focus_x * (scale - 1.0) + drift[0])
    top = round(focus_y * (scale - 1.0) + drift[1])
    left = min(max(0, left), scaled.width - WIDTH)
    top = min(max(0, top), scaled.height - HEIGHT)
    return scaled.crop((left, top, left + WIDTH, top + HEIGHT))


def apply_scene_camera(kind: str, image: Image.Image, local: float) -> Image.Image:
    if kind in {"hook", "sync", "topping_intro", "engines_extensions", "end"}:
        return image
    camera_progress = ease_in_out((local - 0.55) / 2.35)
    if kind == "spoiler":
        return camera_move(
            image,
            scale=1.0 + 0.12 * camera_progress,
            focus=(1000, 550),
            drift=(-55 * camera_progress, 35 * camera_progress),
        )
    if kind == "hackernews":
        return camera_move(
            image,
            scale=1.0 + 0.14 * camera_progress,
            focus=(410, 525),
            drift=(48 * camera_progress, -25 * camera_progress),
        )
    if kind == "privacy":
        return camera_move(
            image,
            scale=1.0 + 0.12 * camera_progress,
            focus=(1000, 540),
            drift=(-45 * camera_progress, -35 * camera_progress),
        )
    return camera_move(
        image,
        scale=1.0 + 0.07 * camera_progress,
        focus=(WIDTH / 2, HEIGHT / 2),
        drift=(25 * math.sin(local * 0.9), 12 * math.cos(local * 0.75)),
    )


def render_scene(scene: Scene, local: float, time: float) -> Image.Image:
    if scene.kind == "hook":
        image = render_hook(local, time)
    elif scene.kind == "sync":
        image = render_sync(local, time)
    elif scene.kind == "tabs":
        image = render_feature(
            local,
            time,
            screen=video_screen("tabs", local),
            headline="GESTURES\nLEAD THE WAY.",
            body="Switch, preview and manage tabs\nwith natural movement.",
            chip_label="GESTURE-FIRST · LIVE",
            accent=PURPLE,
            side="right",
        )
    elif scene.kind == "peek":
        image = render_feature(
            local,
            time,
            screen=video_screen("peek", local),
            headline="LONG-PRESS.\nPEEK FIRST.",
            body="Preview the link. Tap +\nto keep it as a tab.",
            chip_label="LIVE · LINK PEEK",
            accent=PURPLE,
            side="left",
            touch=(425, 610),
        )
    elif scene.kind == "topping_intro":
        image = render_topping_intro(local, time)
    elif scene.kind == "spoiler":
        image = render_feature(
            local,
            time,
            screen=video_screen("spoiler", local),
            headline="SCORES?\nYOUR CALL.",
            body="Spoilerfree Sports hides\nresults behind one toggle.",
            chip_label="LIVE · SPOILERFREE SPORTS",
            accent=PINK,
            side="right",
            touch=(1690, 145),
        )
    elif scene.kind == "hackernews":
        image = render_feature(
            local,
            time,
            screen=video_screen("hackernews", local),
            headline="HACKER NEWS.\nMORE COMFORT.",
            body="Readable cards, larger type\nand stronger tap targets.",
            chip_label="LIVE · HACKER NEWS TOPPING",
            accent=PURPLE,
            side="left",
        )
    elif scene.kind == "engines_extensions":
        image = render_engines_extensions(local, time)
    elif scene.kind == "privacy":
        image = render_feature(
            local,
            time,
            screen=video_screen("privacy", local),
            headline="SEE PRIVACY\nIN REAL TIME.",
            body="The new Privacy X-Ray shows\nblocks, categories and domains.",
            chip_label="NEW · PRIVACY X-RAY",
            accent=PINK,
            side="right",
            touch=(1555, 895),
        )
    elif scene.kind == "more":
        image = render_more(local, time)
    else:
        image = render_end(local, time)
    return apply_scene_camera(scene.kind, image, local)


def zoom_crop(image: Image.Image, scale: float) -> Image.Image:
    scaled = image.resize(
        (round(WIDTH * scale), round(HEIGHT * scale)),
        Image.Resampling.BICUBIC,
    )
    left = (scaled.width - WIDTH) // 2
    top = (scaled.height - HEIGHT) // 2
    return scaled.crop((left, top, left + WIDTH, top + HEIGHT))


def transition_frame(previous: Image.Image, current: Image.Image, progress: float, scene_index: int) -> Image.Image:
    progress = ease_in_out(progress)
    previous = zoom_crop(previous.convert("RGBA"), 1.0 + 0.028 * progress)
    current = zoom_crop(current.convert("RGBA"), 1.035 - 0.035 * progress)

    travel = WIDTH + 420
    boundary = round(-210 + travel * progress)
    slant = 110
    mask = Image.new("L", (WIDTH, HEIGHT), 0)
    mask_draw = ImageDraw.Draw(mask)
    mask_draw.polygon(
        ((0, 0), (boundary + slant, 0), (boundary - slant, HEIGHT), (0, HEIGHT)),
        fill=255,
    )
    if scene_index % 2 == 0:
        mask = mask.transpose(Image.Transpose.FLIP_LEFT_RIGHT)

    composed = Image.composite(current, previous, mask)
    glow = Image.new("RGBA", composed.size, (0, 0, 0, 0))
    glow_draw = ImageDraw.Draw(glow)
    if scene_index % 2 == 0:
        glow_draw.line(
            ((WIDTH - boundary - slant, 0), (WIDTH - boundary + slant, HEIGHT)),
            fill=(255, 47, 120, 150),
            width=14,
        )
    else:
        glow_draw.line(
            ((boundary + slant, 0), (boundary - slant, HEIGHT)),
            fill=(116, 87, 215, 150),
            width=14,
        )
    composed.alpha_composite(glow.filter(ImageFilter.GaussianBlur(16)))
    return composed.convert("RGB")


def render_frame(time: float) -> Image.Image:
    scene_index = next(
        (index for index, scene in enumerate(SCENES) if scene.start <= time < scene.end),
        len(SCENES) - 1,
    )
    scene = SCENES[scene_index]
    local = time - scene.start
    current = render_scene(scene, local, time)
    if scene_index == 0 or local >= TRANSITION:
        return current.convert("RGB")

    previous = SCENES[scene_index - 1]
    previous_frame = render_scene(previous, previous.end - previous.start, time)
    return transition_frame(previous_frame, current, local / TRANSITION, scene_index)


def synthesize_theme(path: Path, *, duration: float = DURATION, sample_rate: int = 48_000) -> None:
    rng = random.Random(0xC0DE51)
    total_samples = round(duration * sample_rate)
    left = [0.0] * total_samples
    right = [0.0] * total_samples
    bpm = 112.0
    beat = 60.0 / bpm

    def mix(index: int, value: float, pan: float = 0.0) -> None:
        if 0 <= index < total_samples:
            left[index] += value * (1.0 - max(0.0, pan) * 0.45)
            right[index] += value * (1.0 + min(0.0, pan) * 0.45)

    def add_kick(start: float, gain: float = 0.44) -> None:
        start_sample = round(start * sample_rate)
        length = round(0.30 * sample_rate)
        phase = 0.0
        for offset in range(length):
            index = start_sample + offset
            if index >= total_samples:
                break
            time = offset / sample_rate
            frequency = 92.0 * math.exp(-time * 18.0) + 43.0
            phase += 2.0 * math.pi * frequency / sample_rate
            click = math.exp(-time * 95.0) * math.sin(2.0 * math.pi * 1450.0 * time) * 0.08
            mix(index, (math.sin(phase) * math.exp(-time * 13.0) + click) * gain)

    def add_clap(start: float) -> None:
        start_sample = round(start * sample_rate)
        length = round(0.24 * sample_rate)
        previous = 0.0
        for offset in range(length):
            index = start_sample + offset
            if index >= total_samples:
                break
            time = offset / sample_rate
            noise = rng.uniform(-1.0, 1.0)
            high = noise - previous * 0.82
            previous = noise
            flutter = 0.65 + 0.35 * math.sin(2.0 * math.pi * 24.0 * time) ** 2
            mix(index, high * math.exp(-time * 19.0) * flutter * 0.10, pan=0.12)

    def add_hat(start: float, pan: float) -> None:
        start_sample = round(start * sample_rate)
        length = round(0.075 * sample_rate)
        previous = 0.0
        for offset in range(length):
            index = start_sample + offset
            if index >= total_samples:
                break
            time = offset / sample_rate
            noise = rng.uniform(-1.0, 1.0)
            high_passed = noise - previous * 0.84
            previous = noise
            mix(index, high_passed * math.exp(-time * 55.0) * 0.045, pan=pan)

    def add_sub(start: float, frequency: float, length: float) -> None:
        start_sample = round(start * sample_rate)
        sample_length = round(length * sample_rate)
        for offset in range(sample_length):
            index = start_sample + offset
            if index >= total_samples:
                break
            time = offset / sample_rate
            attack = min(1.0, time / 0.035)
            release = min(1.0, (length - time) / 0.20)
            envelope = max(0.0, min(attack, release))
            fundamental = math.sin(2.0 * math.pi * frequency * time)
            harmonic = math.sin(2.0 * math.pi * frequency * 2.0 * time + 0.35)
            mix(index, (fundamental * 0.88 + harmonic * 0.12) * envelope * 0.15)

    def add_pad(start: float, root: float, length: float) -> None:
        frequencies = (root, root * 1.5, root * 2.0)
        start_sample = round(start * sample_rate)
        sample_length = round(length * sample_rate)
        for offset in range(sample_length):
            index = start_sample + offset
            if index >= total_samples:
                break
            time = offset / sample_rate
            attack = ease_in_out(min(1.0, time / 0.42))
            release = ease_in_out(min(1.0, (length - time) / 0.58))
            envelope = max(0.0, min(attack, release))
            tone_left = sum(
                math.sin(2.0 * math.pi * frequency * time + voice * 0.35)
                for voice, frequency in enumerate(frequencies)
            ) / len(frequencies)
            tone_right = sum(
                math.sin(2.0 * math.pi * frequency * 1.002 * time + voice * 0.52)
                for voice, frequency in enumerate(frequencies)
            ) / len(frequencies)
            shimmer = 0.82 + 0.18 * math.sin(2.0 * math.pi * 0.24 * time)
            left[index] += tone_left * envelope * shimmer * 0.075
            right[index] += tone_right * envelope * shimmer * 0.075

    def add_pluck(start: float, frequency: float, pan: float) -> None:
        start_sample = round(start * sample_rate)
        length = round(0.52 * sample_rate)
        echo_delay = round(0.19 * sample_rate)
        for offset in range(length):
            index = start_sample + offset
            if index >= total_samples:
                break
            time = offset / sample_rate
            tone = (
                math.sin(2.0 * math.pi * frequency * time)
                + 0.32 * math.sin(2.0 * math.pi * frequency * 2.0 * time + 0.2)
            )
            value = tone * math.exp(-time * 7.2) * 0.065
            mix(index, value, pan=pan)
            mix(index + echo_delay, value * 0.34, pan=-pan)

    def add_whoosh(start: float) -> None:
        start_sample = round(max(0.0, start) * sample_rate)
        length_seconds = 0.62
        length = round(length_seconds * sample_rate)
        smooth = 0.0
        for offset in range(length):
            index = start_sample + offset
            if index >= total_samples:
                break
            time = offset / sample_rate
            noise = rng.uniform(-1.0, 1.0)
            smooth = smooth * 0.94 + noise * 0.06
            high = noise - smooth
            envelope = math.sin(math.pi * min(1.0, time / length_seconds)) ** 1.8
            pan = -0.75 + 1.5 * time / length_seconds
            mix(index, high * envelope * 0.075, pan=pan)

    def add_chime(start: float) -> None:
        for note_index, frequency in enumerate((523.25, 659.25, 783.99)):
            add_pluck(start + note_index * 0.045, frequency, -0.45 + note_index * 0.45)

    bar = beat * 4.0
    pad_roots = (110.0, 146.83, 130.81, 98.0)
    bar_index = 0
    while bar_index * bar < duration:
        root = pad_roots[bar_index % len(pad_roots)]
        start = bar_index * bar
        add_pad(start, root, min(bar + 0.18, duration - start))
        add_sub(start, root / 2.0, min(bar * 0.92, duration - start))
        bar_index += 1

    beat_index = 0
    while beat_index * beat < duration:
        start = beat_index * beat
        if start >= 0.9:
            add_kick(start, 0.42 if beat_index % 4 else 0.50)
            add_hat(start + beat * 0.5, -0.45 if beat_index % 2 else 0.45)
            if beat_index % 4 in (1, 3):
                add_clap(start)
        beat_index += 1

    arpeggio = (220.0, 329.63, 261.63, 392.0, 293.66, 440.0, 329.63, 261.63)
    step = beat / 2.0
    step_index = 0
    while step_index * step < duration:
        start = step_index * step
        if start >= 0.65:
            add_pluck(start, arpeggio[step_index % len(arpeggio)], -0.55 if step_index % 2 else 0.55)
        step_index += 1

    for scene in SCENES[1:]:
        add_whoosh(scene.start - 0.34)
        add_chime(scene.start + 0.03)

    peak = max(max(abs(sample) for sample in left), max(abs(sample) for sample in right), 0.01)
    gain = 0.86 / peak
    fade_samples = round(0.48 * sample_rate)
    for index in range(total_samples):
        fade_in = min(1.0, index / fade_samples)
        fade_out = min(1.0, (total_samples - index - 1) / fade_samples)
        fade = max(0.0, min(fade_in, fade_out))
        left[index] = math.tanh(left[index] * gain) * fade
        right[index] = math.tanh(right[index] * gain) * fade

    path.parent.mkdir(parents=True, exist_ok=True)
    with wave.open(str(path), "wb") as output:
        output.setnchannels(2)
        output.setsampwidth(2)
        output.setframerate(sample_rate)
        for left_sample, right_sample in zip(left, right):
            left_value = max(-32768, min(32767, round(left_sample * 32767)))
            right_value = max(-32768, min(32767, round(right_sample * 32767)))
            output.writeframesraw(struct.pack("<hh", left_value, right_value))


def render_video(output: Path, audio: Path, *, ffmpeg: str) -> None:
    command = [
        ffmpeg,
        "-y",
        "-loglevel",
        "error",
        "-f",
        "rawvideo",
        "-pix_fmt",
        "rgb24",
        "-s",
        f"{WIDTH}x{HEIGHT}",
        "-r",
        str(FPS),
        "-i",
        "-",
        "-i",
        str(audio),
        "-c:v",
        "libx264",
        "-preset",
        "medium",
        "-crf",
        "20",
        "-pix_fmt",
        "yuv420p",
        "-c:a",
        "aac",
        "-b:a",
        "192k",
        "-shortest",
        "-movflags",
        "+faststart",
        str(output),
    ]
    process = subprocess.Popen(command, stdin=subprocess.PIPE)
    assert process.stdin is not None
    try:
        for frame_index in range(round(DURATION * FPS)):
            frame = render_frame(frame_index / FPS)
            process.stdin.write(frame.tobytes())
    finally:
        process.stdin.close()
    return_code = process.wait()
    if return_code != 0:
        raise SystemExit(f"ffmpeg failed with exit code {return_code}")


def extract_video_frames(directory: Path, *, ffmpeg: str) -> None:
    VIDEO_FRAME_PATHS.clear()
    for kind, source in VIDEO_SOURCES.items():
        destination = directory / kind
        destination.mkdir(parents=True)
        frame_pattern = destination / "%04d.png"
        subprocess.run(
            [
                ffmpeg,
                "-y",
                "-loglevel",
                "error",
                "-i",
                str(source),
                "-vf",
                "fps=30,scale=478:-2",
                str(frame_pattern),
            ],
            check=True,
        )
        frames = tuple(sorted(destination.glob("*.png")))
        if not frames:
            raise SystemExit(f"No frames decoded from {source}")
        scene = next(scene for scene in SCENES if scene.kind == kind)
        expected_frames = round((scene.end - scene.start) * SOURCE_FPS)
        if len(frames) < expected_frames - 3:
            raise SystemExit(
                f"{source} decoded to {len(frames)} frames; expected at least {expected_frames - 3}"
            )
        VIDEO_FRAME_PATHS[kind] = frames


def validate_inputs() -> None:
    required = (
        LOGO,
        *VIDEO_SOURCES.values(),
        *STATIC_SOURCES.values(),
    )
    missing = [path for path in required if not path.exists()]
    if missing:
        joined = "\n".join(str(path) for path in missing)
        raise SystemExit(f"Missing promo inputs:\n{joined}")


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "--output",
        type=Path,
        default=OUTPUT_DIR / "candy-browser-showcase-16x9.mp4",
        help="Destination MP4 path.",
    )
    parser.add_argument(
        "--theme",
        type=Path,
        default=OUTPUT_DIR / "candy-browser-theme.wav",
        help="Destination WAV path.",
    )
    parser.add_argument(
        "--poster",
        type=Path,
        default=OUTPUT_DIR / "candy-browser-showcase-16x9-poster.jpg",
        help="Destination poster path.",
    )
    args = parser.parse_args()

    validate_inputs()
    ffmpeg = shutil.which("ffmpeg")
    if ffmpeg is None:
        raise SystemExit("ffmpeg is required; install it with `brew install ffmpeg`.")

    output = args.output.resolve()
    output.parent.mkdir(parents=True, exist_ok=True)
    theme = args.theme.resolve()
    poster = args.poster.resolve()
    theme.parent.mkdir(parents=True, exist_ok=True)
    poster.parent.mkdir(parents=True, exist_ok=True)

    with tempfile.TemporaryDirectory(prefix="candy-reddit-promo-") as temporary, tempfile.TemporaryDirectory(
        prefix=f".{output.stem}-",
        dir=output.parent,
    ) as export_temporary:
        extract_video_frames(Path(temporary), ffmpeg=ffmpeg)
        synthesize_theme(theme)
        render_frame(5.2).save(poster, quality=92, optimize=True)
        temporary_output = Path(export_temporary) / output.name
        render_video(temporary_output, theme, ffmpeg=ffmpeg)
        temporary_output.replace(output)
    print(output)


if __name__ == "__main__":
    main()
