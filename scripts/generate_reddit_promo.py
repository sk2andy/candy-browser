#!/usr/bin/env python3
"""Render a short Reddit promo for Candy Browser from repository assets."""

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

WIDTH = 1080
HEIGHT = 1350
FPS = 60
SOURCE_FPS = 30
DURATION = 22.6
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
    Scene(0.0, 1.3, "hook"),
    Scene(1.3, 3.6, "tabs"),
    Scene(3.6, 7.8, "peek"),
    Scene(7.8, 11.0, "privacy"),
    Scene(11.0, 13.1, "reader"),
    Scene(13.1, 15.2, "trail"),
    Scene(15.2, 18.1, "tab_variants"),
    Scene(18.1, 20.3, "more"),
    Scene(20.3, 22.6, "end"),
)

VIDEO_SOURCES = {
    "tabs": SOURCE_DIR / "tab-switcher-live.mp4",
    "peek": SOURCE_DIR / "link-peek-live.mp4",
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
    cx = round(180 + 60 * math.sin(time * 0.8))
    cy = round(260 + 80 * math.cos(time * 0.65))
    draw.ellipse(
        tuple(value // glow_scale for value in (cx - 260, cy - 260, cx + 260, cy + 260)),
        fill=accent_rgb + (70,),
    )
    cx2 = round(890 + 55 * math.cos(time * 0.7))
    cy2 = round(1050 + 70 * math.sin(time * 0.55))
    draw.ellipse(
        tuple(value // glow_scale for value in (cx2 - 300, cy2 - 300, cx2 + 300, cy2 + 300)),
        fill=(255, 47, 120, 55),
    )
    glow = glow.filter(ImageFilter.GaussianBlur(26)).resize(image.size, Image.Resampling.BILINEAR)
    image.alpha_composite(glow)

    draw = ImageDraw.Draw(image)
    for index in range(9):
        angle = time * (0.18 + index * 0.012) + index * 0.9
        x = round(WIDTH / 2 + math.cos(angle) * (410 + index * 13))
        y = round(HEIGHT / 2 + math.sin(angle * 1.1) * (470 + index * 7))
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


def phone_image(screen: Image.Image, *, width: int = 478) -> Image.Image:
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
    x = 810
    start_y = 1125
    end_y = 780
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
        (round(-465 + 519 * chip_progress), 52),
        "MATERIAL 3 EXPRESSIVE  ·  OPEN SOURCE",
        "#ECE8FF",
        PURPLE,
    )

    logo = Image.open(LOGO).convert("RGBA")
    logo_scale = 0.86 * ease_out_quint(local / 0.52)
    logo_size = max(1, round(280 * logo_scale))
    logo = logo.resize((logo_size, logo_size), Image.Resampling.LANCZOS)
    canvas.alpha_composite(logo, ((WIDTH - logo_size) // 2, 235 + round(18 * math.sin(local * 5.5))))

    alpha = round(255 * min(1.0, max(0.0, (local - 0.12) / 0.38)))
    text_layer = Image.new("RGBA", canvas.size, (0, 0, 0, 0))
    text_draw = ImageDraw.Draw(text_layer)
    heading_font = font(78, weight="semibold")
    heading = "Candy Browser"
    bounds = text_draw.textbbox((0, 0), heading, font=heading_font)
    reveal = ease_out_quint((local - 0.08) / 0.54)
    text_draw.text(
        ((WIDTH - bounds[2]) // 2, 580 + round(38 * (1.0 - reveal))),
        heading,
        font=heading_font,
        fill=(32, 33, 43, alpha),
    )
    strap = "Packed with features."
    strap_font = font(36, weight="medium")
    bounds = text_draw.textbbox((0, 0), strap, font=strap_font)
    text_draw.text(
        ((WIDTH - bounds[2]) // 2, 683 + round(28 * (1.0 - reveal))),
        strap,
        font=strap_font,
        fill=(116, 87, 215, alpha),
    )
    canvas.alpha_composite(text_layer)
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
    draw_chip(draw, (round(-330 + 384 * chip_progress), 52), chip_label, "#FFFFFFD9", accent)

    device = phone_image(screen)
    entrance = ease_out_quint(local / 0.64)
    bob = math.sin(local * 3.2) * 5
    if side == "right":
        phone_x = round(530 + (WIDTH + 80 - 530) * (1.0 - entrance))
        text_x = 54
    else:
        phone_x = round(-24 - (580 * (1.0 - entrance)))
        text_x = 610
    phone_y = round(164 + bob)
    paste_scaled(canvas, device, (phone_x, phone_y), 0.91, angle=-1.2 if side == "right" else 1.2)

    text_progress = ease_out_quint((local - 0.10) / 0.62)
    text_alpha = round(255 * text_progress)
    text_offset = round(52 * (1.0 - text_progress))
    text_layer = Image.new("RGBA", canvas.size, (0, 0, 0, 0))
    text_draw = ImageDraw.Draw(text_layer)
    text_draw.multiline_text(
        (text_x, 250 + text_offset),
        headline,
        font=font(62, weight="semibold"),
        fill=(32, 33, 43, text_alpha),
        spacing=0,
    )
    text_draw.multiline_text(
        (text_x, 454 + round(text_offset * 0.65)),
        body,
        font=font(30, weight="medium"),
        fill=(85, 87, 103, text_alpha),
        spacing=7,
    )
    accent_width = round(104 * text_progress)
    text_draw.rounded_rectangle((text_x, 595, text_x + accent_width, 603), radius=4, fill=accent)
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


def render_more(local: float, time: float) -> Image.Image:
    canvas = background(time, accent=PINK)
    draw = ImageDraw.Draw(canvas)
    draw_chip(
        draw,
        (round(-330 + 384 * ease_out_quint(local / 0.44)), 52),
        "AND THAT'S NOT ALL",
        "#FFFFFFD9",
        PINK,
    )

    text_progress = ease_out_quint(local / 0.62)
    alpha = round(255 * text_progress)
    text_offset = round(50 * (1.0 - text_progress))
    text_layer = Image.new("RGBA", canvas.size, (0, 0, 0, 0))
    text_draw = ImageDraw.Draw(text_layer)
    text_draw.multiline_text(
        (54, 210 + text_offset),
        "PROFILES.\nPINS.\nCOMMANDS.",
        font=font(65, weight="semibold"),
        fill=(32, 33, 43, alpha),
        spacing=2,
    )
    text_draw.text(
        (56, 485 + round(text_offset * 0.65)),
        "And many more.",
        font=font(36, weight="semibold"),
        fill=(116, 87, 215, alpha),
    )
    canvas.alpha_composite(text_layer)

    profile = phone_image(static_screen("profile"), width=285)
    commands = phone_image(static_screen("commands"), width=255)
    entrance = ease_out_quint(local / 0.72)
    paste_scaled(canvas, commands, (720, round(520 + 900 * (1.0 - entrance))), 0.94, angle=4.0)
    paste_scaled(canvas, profile, (470, round(555 + 900 * (1.0 - entrance))), 0.98, angle=-3.2)
    return canvas


def render_end(local: float, time: float) -> Image.Image:
    canvas = background(time, accent=PINK)
    draw = ImageDraw.Draw(canvas)
    progress = ease_out_quint(local / 0.68)
    content_offset = round(46 * (1.0 - progress))

    logo = Image.open(LOGO).convert("RGBA")
    logo_size = max(1, round(230 * progress))
    logo = logo.resize((logo_size, logo_size), Image.Resampling.LANCZOS)
    canvas.alpha_composite(logo, ((WIDTH - logo_size) // 2, 230 + content_offset))

    heading_font = font(80, weight="semibold")
    heading = "Candy Browser"
    bounds = draw.textbbox((0, 0), heading, font=heading_font)
    draw.text(((WIDTH - bounds[2]) // 2, 550 + content_offset), heading, font=heading_font, fill=INK)
    strap = "Gesture-first. Privacy-first."
    strap_font = font(35, weight="medium")
    bounds = draw.textbbox((0, 0), strap, font=strap_font)
    draw.text(((WIDTH - bounds[2]) // 2, 660 + content_offset), strap, font=strap_font, fill=PURPLE)

    button_width = 650
    button_left = (WIDTH - button_width) // 2
    button_top = 800 + content_offset
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
        ((WIDTH - bounds[2]) // 2, button_top + 36),
        cta,
        font=cta_font,
        fill="white",
    )
    foot = "github.com/sk2andy/candy-browser"
    foot_font = font(25, weight="medium")
    bounds = draw.textbbox((0, 0), foot, font=foot_font)
    draw.text(((WIDTH - bounds[2]) // 2, 965 + content_offset), foot, font=foot_font, fill=MUTED)
    chip_label = "ANDROID 14+  ·  PolyForm Noncommercial 1.0.0"
    chip_font = font(22, weight="semibold")
    chip_bounds = draw.textbbox((0, 0), chip_label, font=chip_font)
    chip_width = chip_bounds[2] - chip_bounds[0] + 38
    draw_chip(
        draw,
        ((WIDTH - chip_width) // 2, 1082 + content_offset),
        chip_label,
        "#FFFFFFD9",
        PURPLE,
    )
    return canvas


def render_scene(scene: Scene, local: float, time: float) -> Image.Image:
    if scene.kind == "hook":
        return render_hook(local, time)
    if scene.kind == "tabs":
        return render_feature(
            local,
            time,
            screen=video_screen("tabs", local),
            headline="SWIPE UP.\nSEE EVERY TAB.",
            body="One gesture opens your\nlive tab switcher.",
            chip_label="LIVE · TAB SWITCHER",
            accent=PURPLE,
            side="right",
            swipe=True,
        )
    if scene.kind == "peek":
        return render_feature(
            local,
            time,
            screen=video_screen("peek", local),
            headline="LONG-PRESS.\nPEEK FIRST.",
            body="Preview the link. Tap +\nto keep it as a tab.",
            chip_label="LIVE · LINK PEEK",
            accent=PURPLE,
            side="left",
            touch=(305, 720),
        )
    if scene.kind == "privacy":
        return render_feature(
            local,
            time,
            screen=video_screen("privacy", local),
            headline="OPEN PRIVACY\nX-RAY.",
            body="See what got blocked,\nright inside the tab.",
            chip_label="LIVE · PRIVACY X-RAY",
            accent=PINK,
            side="right",
            touch=(800, 1075),
        )
    if scene.kind == "reader":
        return render_feature(
            local,
            time,
            screen=static_screen("reader"),
            headline="READ WITHOUT\nTHE CLUTTER.",
            body="Reader Mode keeps the\narticle, drops the noise.",
            chip_label="READER MODE",
            accent=PURPLE,
            side="left",
        )
    if scene.kind == "trail":
        return render_feature(
            local,
            time,
            screen=static_screen("trail"),
            headline="FOLLOW EVERY\nBRANCH.",
            body="Candy Trails maps where\nyour browsing took you.",
            chip_label="CANDY TRAILS",
            accent=PINK,
            side="right",
        )
    if scene.kind == "tab_variants":
        return render_tab_variants(local, time)
    if scene.kind == "more":
        return render_more(local, time)
    return render_end(local, time)


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
        default=OUTPUT_DIR / "candy-browser-reddit-4x5.mp4",
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
        default=OUTPUT_DIR / "candy-browser-reddit-poster.jpg",
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

    with tempfile.TemporaryDirectory(prefix="candy-reddit-promo-") as temporary:
        extract_video_frames(Path(temporary), ffmpeg=ffmpeg)
        synthesize_theme(theme)
        render_frame(21.4).save(poster, quality=92, optimize=True)
        render_video(output, theme, ffmpeg=ffmpeg)
    print(output)


if __name__ == "__main__":
    main()
