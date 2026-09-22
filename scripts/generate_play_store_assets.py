#!/usr/bin/env python3
"""Generate localized Google Play artwork from Candy's website design system."""

from __future__ import annotations

import shutil
import tempfile
from dataclasses import dataclass
from pathlib import Path

from PIL import Image, ImageDraw, ImageFilter, ImageFont


ROOT = Path(__file__).resolve().parents[1]
SCREENSHOT_DIR = ROOT / "docs" / "screenshots"
SITE_ASSET_DIR = ROOT / "site" / "assets"
PROMO_SOURCE_DIR = ROOT / "docs" / "promo" / "source"
CONCEPT_DIR = ROOT / "docs" / "promo" / "play-store-concepts"
METADATA_DIR = ROOT / "fastlane" / "metadata" / "android"
DISPLAY_FONT = SITE_ASSET_DIR / "fonts" / "Nunito-wght.ttf"

SCREENSHOT_SIZE = (1080, 1920)
FEATURE_GRAPHIC_SIZE = (1024, 500)

PAGE = "#FFF8FA"
SURFACE_HIGH = "#F1E2E7"
INK = "#201A1C"
MUTED = "#514347"
PRIMARY = "#963A5E"
PRIMARY_CONTAINER = "#FFD9E3"
ON_PRIMARY_CONTAINER = "#3D001D"
SECONDARY = "#715760"
SECONDARY_CONTAINER = "#F3DDEA"
TERTIARY_CONTAINER = "#C3ECDD"
ON_TERTIARY_CONTAINER = "#002019"
OUTLINE = "#D7C2C9"
HERO = "#7A2F67"
HERO_ACCENT = "#FFD9E3"


@dataclass(frozen=True)
class Slide:
    kind: str
    source: str | None
    eyebrow_en: str
    eyebrow_de: str
    headline_en: str
    headline_de: str
    stage_color: str
    shape_color: str
    shape: str = "round-square"


SLIDES = (
    Slide(
        "addons",
        None,
        "FIREFOX ADD-ONS",
        "FIREFOX-ADD-ONS",
        "Add-ons. Right in Candy.",
        "Add-ons. Direkt in Candy.",
        SECONDARY_CONTAINER,
        PRIMARY_CONTAINER,
        "cookie",
    ),
    Slide(
        "phone",
        "candy-privacy.png",
        "UBLOCK ORIGIN + PRIVACY X-RAY",
        "UBLOCK ORIGIN + PRIVACY X-RAY",
        "Block ads. Inspect the rest.",
        "Werbung blocken. Rest prüfen.",
        TERTIARY_CONTAINER,
        PRIMARY_CONTAINER,
        "arch",
    ),
    Slide(
        "tabs-0",
        None,
        "TAB STYLE 1 OF 3",
        "TAB-ANSICHT 1 VON 3",
        "Cover flow.",
        "Cover flow.",
        PRIMARY_CONTAINER,
        SECONDARY_CONTAINER,
        "cookie",
    ),
    Slide(
        "tabs-1",
        None,
        "TAB STYLE 2 OF 3",
        "TAB-ANSICHT 2 VON 3",
        "Grid.",
        "Raster.",
        SECONDARY_CONTAINER,
        TERTIARY_CONTAINER,
        "clover",
    ),
    Slide(
        "tabs-2",
        None,
        "TAB STYLE 3 OF 3",
        "TAB-ANSICHT 3 VON 3",
        "List.",
        "Liste.",
        TERTIARY_CONTAINER,
        SECONDARY_CONTAINER,
        "arch",
    ),
    Slide(
        "phone",
        "candy-link-peek.png",
        "LINK PEEK",
        "LINK PEEK",
        "Peek before you open.",
        "Schau rein, bevor du öffnest.",
        SECONDARY_CONTAINER,
        TERTIARY_CONTAINER,
        "round-square",
    ),
    Slide(
        "phone",
        "candy-trail.png",
        "CANDY TRAILS",
        "CANDY TRAILS",
        "See where every link led.",
        "Sieh, wohin jeder Link führte.",
        SECONDARY_CONTAINER,
        TERTIARY_CONTAINER,
        "clover",
    ),
    Slide(
        "sync",
        None,
        "SYNC ACROSS DEVICES",
        "SYNC AUF ALLEN GERÄTEN",
        "Your tabs. Wherever you go.",
        "Deine Tabs. Überall bei dir.",
        TERTIARY_CONTAINER,
        PRIMARY_CONTAINER,
        "round-square",
    ),
)


def font(size: int, *, weight: str = "regular") -> ImageFont.FreeTypeFont:
    text_font = ImageFont.truetype(str(DISPLAY_FONT), size=size)
    variation = {"regular": "Regular", "semibold": "SemiBold", "bold": "ExtraBold"}[weight]
    try:
        text_font.set_variation_by_name(variation)
    except OSError:
        pass
    return text_font


def rounded_mask(size: tuple[int, int], radius: int) -> Image.Image:
    mask = Image.new("L", size, 0)
    ImageDraw.Draw(mask).rounded_rectangle((0, 0, size[0] - 1, size[1] - 1), radius=radius, fill=255)
    return mask


def rounded_image(image: Image.Image, width: int, radius: int) -> Image.Image:
    height = round(width * image.height / image.width)
    resized = image.convert("RGBA").resize((width, height), Image.Resampling.LANCZOS)
    resized.putalpha(rounded_mask(resized.size, radius))
    return resized


def cover_image(image: Image.Image, size: tuple[int, int], radius: int = 0) -> Image.Image:
    target_width, target_height = size
    scale = max(target_width / image.width, target_height / image.height)
    resized = image.convert("RGBA").resize(
        (round(image.width * scale), round(image.height * scale)),
        Image.Resampling.LANCZOS,
    )
    left = (resized.width - target_width) // 2
    top = (resized.height - target_height) // 2
    cropped = resized.crop((left, top, left + target_width, top + target_height))
    if radius:
        cropped.putalpha(rounded_mask(cropped.size, radius))
    return cropped


def contain_image(image: Image.Image, size: tuple[int, int]) -> Image.Image:
    target_width, target_height = size
    scale = min(target_width / image.width, target_height / image.height)
    return image.convert("RGBA").resize(
        (round(image.width * scale), round(image.height * scale)),
        Image.Resampling.LANCZOS,
    )


def content_card(
    image: Image.Image,
    size: tuple[int, int],
    *,
    radius: int = 28,
    shadow_pad: int = 24,
) -> Image.Image:
    content = cover_image(image, size, radius)
    layer = Image.new(
        "RGBA",
        (content.width + shadow_pad * 2, content.height + shadow_pad * 2),
        (0, 0, 0, 0),
    )
    shadow = Image.new("RGBA", layer.size, (0, 0, 0, 0))
    ImageDraw.Draw(shadow).rounded_rectangle(
        (
            shadow_pad + 2,
            shadow_pad + 10,
            shadow_pad + content.width + 2,
            shadow_pad + content.height + 10,
        ),
        radius=radius,
        fill=(75, 42, 55, 40),
    )
    layer.alpha_composite(shadow.filter(ImageFilter.GaussianBlur(18)))
    layer.alpha_composite(content, (shadow_pad, shadow_pad))
    return layer


def cropped_content_card(
    image: Image.Image,
    crop_box: tuple[int, int, int, int],
    size: tuple[int, int],
    *,
    radius: int = 30,
    shadow_pad: int = 24,
) -> Image.Image:
    return content_card(
        image.crop(crop_box),
        size,
        radius=radius,
        shadow_pad=shadow_pad,
    )


def wrapped_lines(draw: ImageDraw.ImageDraw, text: str, text_font: ImageFont.FreeTypeFont, max_width: int) -> list[str]:
    words = text.split()
    lines: list[str] = []
    current = ""
    for word in words:
        candidate = f"{current} {word}".strip()
        if current and draw.textlength(candidate, font=text_font) > max_width:
            lines.append(current)
            current = word
        else:
            current = candidate
    if current:
        lines.append(current)
    return lines


def centered_text(draw: ImageDraw.ImageDraw, y: int, text: str, text_font: ImageFont.FreeTypeFont, fill: str) -> None:
    width = draw.textlength(text, font=text_font)
    draw.text(((SCREENSHOT_SIZE[0] - width) / 2, y), text, font=text_font, fill=fill)


def draw_header(canvas: Image.Image, locale: str, slide: Slide) -> None:
    draw = ImageDraw.Draw(canvas)
    eyebrow = slide.eyebrow_de if locale == "de-DE" else slide.eyebrow_en
    headline = slide.headline_de if locale == "de-DE" else slide.headline_en
    centered_text(draw, 58, eyebrow, font(24, weight="bold"), PRIMARY)

    headline_font = font(78, weight="bold")
    lines = wrapped_lines(draw, headline, headline_font, 920)
    top = 100 if len(lines) > 1 else 126
    for index, line in enumerate(lines):
        centered_text(draw, top + index * 80, line, headline_font, INK)


def shadowed_panel(canvas: Image.Image, box: tuple[int, int, int, int], fill: str, radius: int) -> None:
    x0, y0, x1, y1 = box
    shadow = Image.new("RGBA", canvas.size, (0, 0, 0, 0))
    ImageDraw.Draw(shadow).rounded_rectangle((x0, y0 + 12, x1, y1 + 12), radius=radius, fill=(75, 42, 55, 28))
    canvas.alpha_composite(shadow.filter(ImageFilter.GaussianBlur(24)))
    ImageDraw.Draw(canvas).rounded_rectangle(box, radius=radius, fill=fill)


def shape_layer(size: tuple[int, int], fill: str, shape: str) -> Image.Image:
    layer = Image.new("RGBA", size, (0, 0, 0, 0))
    draw = ImageDraw.Draw(layer)
    width, height = size
    if shape == "arch":
        draw.rounded_rectangle((0, 0, width - 1, height - 1), radius=width // 2, fill=fill)
        draw.rectangle((0, width // 2, width - 1, height - 1), fill=fill)
    elif shape == "clover":
        diameter = min(width, height) * 3 // 5
        offsets = ((0, 0), (width - diameter, 0), (0, height - diameter), (width - diameter, height - diameter))
        for x, y in offsets:
            draw.ellipse((x, y, x + diameter, y + diameter), fill=fill)
        draw.rectangle((width // 5, height // 5, width * 4 // 5, height * 4 // 5), fill=fill)
    elif shape == "cookie":
        draw.rounded_rectangle((12, 42, width - 12, height - 42), radius=width // 3, fill=fill)
        draw.ellipse((0, height // 5, width * 2 // 5, height * 3 // 5), fill=fill)
        draw.ellipse((width * 3 // 5, height * 2 // 5, width, height * 4 // 5), fill=fill)
    else:
        draw.rounded_rectangle((0, 0, width - 1, height - 1), radius=90, fill=fill)
    return layer


def paste_shape(
    canvas: Image.Image,
    center: tuple[int, int],
    size: tuple[int, int],
    fill: str,
    shape: str,
    angle: float,
) -> None:
    layer = shape_layer(size, fill, shape).rotate(angle, resample=Image.Resampling.BICUBIC, expand=True)
    canvas.alpha_composite(layer, (center[0] - layer.width // 2, center[1] - layer.height // 2))


def screenshot_card(screen_path: Path, *, width: int = 720) -> Image.Image:
    screen = rounded_image(Image.open(screen_path), width, radius=32)
    shadow_pad = 32
    layer = Image.new("RGBA", (screen.width + shadow_pad * 2, screen.height + shadow_pad * 2), (0, 0, 0, 0))

    shadow = Image.new("RGBA", layer.size, (0, 0, 0, 0))
    ImageDraw.Draw(shadow).rounded_rectangle(
        (shadow_pad + 2, shadow_pad + 12, shadow_pad + screen.width + 2, shadow_pad + screen.height + 12),
        radius=36,
        fill=(75, 42, 55, 40),
    )
    layer.alpha_composite(shadow.filter(ImageFilter.GaussianBlur(24)))
    layer.alpha_composite(screen, (shadow_pad, shadow_pad))
    return layer


def paste_centered(canvas: Image.Image, image: Image.Image, y: int, *, angle: float = 0.0) -> None:
    rotated = image.rotate(angle, resample=Image.Resampling.BICUBIC, expand=True)
    canvas.alpha_composite(rotated, ((canvas.width - rotated.width) // 2, y))


def draw_pill(
    draw: ImageDraw.ImageDraw,
    box: tuple[int, int, int, int],
    label: str,
    fill: str,
    text_fill: str,
    text_size: int = 24,
) -> None:
    draw.rounded_rectangle(box, radius=(box[3] - box[1]) // 2, fill=fill)
    label_font = font(text_size, weight="bold")
    bounds = draw.textbbox((0, 0), label, font=label_font)
    x = (box[0] + box[2] - (bounds[2] - bounds[0])) / 2
    y = (box[1] + box[3] - (bounds[3] - bounds[1])) / 2 - bounds[1]
    draw.text((x, y), label, font=label_font, fill=text_fill)


def draw_toggle(draw: ImageDraw.ImageDraw, box: tuple[int, int, int, int]) -> None:
    draw.rounded_rectangle(box, radius=(box[3] - box[1]) // 2, fill=PRIMARY)
    diameter = box[3] - box[1] - 8
    draw.ellipse((box[2] - diameter - 4, box[1] + 4, box[2] - 4, box[1] + 4 + diameter), fill="#FFFFFF")


def draw_extension_row(
    draw: ImageDraw.ImageDraw,
    y: int,
    title: str,
    detail: str,
    badge: str,
    badge_fill: str,
) -> None:
    draw.rounded_rectangle((144, y, 936, y + 190), radius=48, fill=PAGE)
    draw.rounded_rectangle((176, y + 35, 296, y + 155), radius=38, fill=badge_fill)
    badge_font = font(40, weight="bold")
    bounds = draw.textbbox((0, 0), badge, font=badge_font)
    draw.text(
        (236 - (bounds[2] - bounds[0]) / 2, y + 95 - (bounds[3] - bounds[1]) / 2 - bounds[1]),
        badge,
        font=badge_font,
        fill="#FFFFFF",
    )
    draw.text((330, y + 42), title, font=font(34 if len(title) < 25 else 27, weight="bold"), fill=INK)
    draw.text((330, y + 96), detail, font=font(22, weight="semibold"), fill=MUTED)
    draw_toggle(draw, (788, y + 69, 884, y + 121))


def draw_dotted_route(
    draw: ImageDraw.ImageDraw,
    start: tuple[int, int],
    end: tuple[int, int],
    *,
    fill: str = PRIMARY,
    spacing: int = 24,
    radius: int = 5,
) -> None:
    dx = end[0] - start[0]
    dy = end[1] - start[1]
    distance = max(1, round((dx * dx + dy * dy) ** 0.5))
    steps = max(1, distance // spacing)
    for index in range(steps + 1):
        x = start[0] + dx * index / steps
        y = start[1] + dy * index / steps
        draw.ellipse((x - radius, y - radius, x + radius, y + radius), fill=fill)


def render_addons_slide(canvas: Image.Image, locale: str, slide: Slide) -> None:
    shadowed_panel(canvas, (44, 330, 1036, 1948), slide.stage_color, radius=56)
    draw = ImageDraw.Draw(canvas)

    illustration = contain_image(
        Image.open(PROMO_SOURCE_DIR / "addons-hero-v2.png"),
        (880, 520),
    )
    canvas.alpha_composite(illustration, ((canvas.width - illustration.width) // 2, 398))

    bridge = "Mehr Kontrolle. Weniger Ablenkung." if locale == "de-DE" else "More control. Fewer distractions."
    centered_text(draw, 868, bridge, font(29, weight="bold"), ON_PRIMARY_CONTAINER)

    ad_detail = "Blockiert Werbung und Tracker" if locale == "de-DE" else "Blocks ads and trackers"
    cookie_detail = "Räumt Cookie-Banner weg" if locale == "de-DE" else "Clears cookie banners"
    draw_extension_row(draw, 956, "uBlock Origin", ad_detail, "uO", "#B31218")
    draw_extension_row(draw, 1178, "I still don't care about cookies", cookie_detail, "C", PRIMARY)

    capabilities = (
        ("Weniger Werbung", "Weniger Tracking", "Keine Cookie-Nerverei")
        if locale == "de-DE"
        else ("Fewer ads", "Less tracking", "No cookie banner clutter")
    )
    positions = ((116, 1466, 380), (400, 1466, 664), (684, 1466, 964))
    for label, (x0, y0, x1) in zip(capabilities, positions, strict=True):
        draw_pill(draw, (x0, y0, x1, y0 + 68), label, PAGE, INK, 18)

    note = "Einmal einschalten. Entspannter surfen." if locale == "de-DE" else "Switch on. Browse in peace."
    centered_text(draw, 1622, note, font(24, weight="bold"), MUTED)


def tab_sources() -> tuple[Path, Path, Path]:
    return (
        SITE_ASSET_DIR / "candy-tabs-coverflow.png",
        SITE_ASSET_DIR / "candy-tabs-grid.png",
        SITE_ASSET_DIR / "candy-tabs-list.png",
    )


def centered_panel_text(
    draw: ImageDraw.ImageDraw,
    panel_index: int,
    y: int,
    text: str,
    text_font: ImageFont.FreeTypeFont,
    fill: str,
) -> None:
    panel_left = panel_index * SCREENSHOT_SIZE[0]
    width = draw.textlength(text, font=text_font)
    x = panel_left + (SCREENSHOT_SIZE[0] - width) / 2
    draw.text((x, y), text, font=text_font, fill=fill)


def render_tabs_triptych(locale: str) -> Image.Image:
    panel_width, panel_height = SCREENSHOT_SIZE
    canvas = Image.new("RGBA", (panel_width * 3, panel_height), PAGE)
    draw = ImageDraw.Draw(canvas)

    # One continuous Candy stage is cropped into three connected Play Store images.
    shadowed_panel(canvas, (44, 330, canvas.width - 44, 1948), PRIMARY_CONTAINER, radius=56)
    paste_shape(canvas, (960, 1050), (1420, 1230), SECONDARY_CONTAINER, "cookie", -6)
    paste_shape(canvas, (2350, 1030), (1490, 1180), TERTIARY_CONTAINER, "clover", 7)
    paste_shape(canvas, (1650, 1510), (920, 420), PAGE, "round-square", -3)

    headings = (
        ("TAB-ANSICHT 1 VON 3", "Cover flow."),
        ("TAB-ANSICHT 2 VON 3", "Raster."),
        ("TAB-ANSICHT 3 VON 3", "Liste."),
    ) if locale == "de-DE" else (
        ("TAB STYLE 1 OF 3", "Cover flow."),
        ("TAB STYLE 2 OF 3", "Grid."),
        ("TAB STYLE 3 OF 3", "List."),
    )
    for panel_index, (eyebrow, headline) in enumerate(headings):
        centered_panel_text(draw, panel_index, 58, eyebrow, font(24, weight="bold"), PRIMARY)
        centered_panel_text(draw, panel_index, 126, headline, font(82, weight="bold"), INK)

    images = tuple(Image.open(path).convert("RGBA") for path in tab_sources())
    cards = (
        cropped_content_card(images[0], (0, 70, 1080, 2050), (720, 1320), radius=38, shadow_pad=24),
        cropped_content_card(images[1], (0, 70, 1080, 1665), (760, 1122), radius=38, shadow_pad=24),
        cropped_content_card(images[2], (0, 70, 1080, 895), (820, 626), radius=38, shadow_pad=24),
    )
    placements = ((156, 370), (1244, 468), (2270, 620))
    for card, placement in zip(cards, placements, strict=True):
        canvas.alpha_composite(card, placement)

    # The dotted path makes the three separate store images read as one scene.
    draw_dotted_route(draw, (430, 1740), (2810, 1740), fill=PRIMARY, spacing=34, radius=5)
    for x in (540, 1620, 2700):
        draw.ellipse((x - 18, 1722, x + 18, 1758), fill=PRIMARY)
        draw.ellipse((x - 7, 1733, x + 7, 1747), fill=PAGE)

    phrases = (
        ("Ein Wisch.", "Jeder Tab.", "In Reichweite."),
        ("One swipe.", "Every tab.", "In reach."),
    )[locale != "de-DE"]
    for panel_index, phrase in enumerate(phrases):
        centered_panel_text(draw, panel_index, 1790, phrase, font(34, weight="bold"), ON_PRIMARY_CONTAINER)

    return canvas


def render_tab_concept(output_root: Path) -> None:
    concept_root = output_root / "concepts"
    concept_root.mkdir(parents=True, exist_ok=True)
    output = concept_root / "tabs-triptych.png"
    render_tabs_triptych("de-DE").convert("RGB").save(output, optimize=True, compress_level=9)
    validate_output(output, (SCREENSHOT_SIZE[0] * 3, SCREENSHOT_SIZE[1]))


def render_sync_slide(canvas: Image.Image, locale: str, slide: Slide) -> None:
    draw = ImageDraw.Draw(canvas)

    illustration = contain_image(
        Image.open(PROMO_SOURCE_DIR / "sync-hero-v2.png"),
        (900, 1180),
    )
    canvas.alpha_composite(illustration, ((canvas.width - illustration.width) // 2, 338))

    sync_title = "Dein eigener Sync." if locale == "de-DE" else "Your own sync."
    sync_detail = "Du bestimmst, wo er läuft." if locale == "de-DE" else "You choose where it runs."
    centered_text(draw, 1550, sync_title, font(38, weight="bold"), INK)
    centered_text(draw, 1606, sync_detail, font(25, weight="semibold"), MUTED)

    privacy_note = "Privat von Handy bis Computer." if locale == "de-DE" else "Private from phone to computer."
    centered_text(draw, 1708, privacy_note, font(23, weight="bold"), PRIMARY)


def render_phone_slide(canvas: Image.Image, locale: str, slide: Slide) -> None:
    if slide.source is None:
        raise ValueError("Phone slide requires a source image")
    shadowed_panel(canvas, (44, 330, 1036, 1948), slide.stage_color, radius=56)
    paste_shape(canvas, (540, 1080), (760, 1160), slide.shape_color, slide.shape, -6)
    screenshot = screenshot_card(SCREENSHOT_DIR / slide.source, width=720)
    paste_centered(canvas, screenshot, 398)

def render_slide(output_root: Path, locale: str, index: int, slide: Slide) -> None:
    if slide.kind.startswith("tabs-"):
        panel_index = int(slide.kind.removeprefix("tabs-"))
        triptych = render_tabs_triptych(locale)
        left = panel_index * SCREENSHOT_SIZE[0]
        canvas = triptych.crop((left, 0, left + SCREENSHOT_SIZE[0], SCREENSHOT_SIZE[1]))
    else:
        canvas = Image.new("RGBA", SCREENSHOT_SIZE, PAGE)
        draw_header(canvas, locale, slide)
        if slide.kind == "addons":
            render_addons_slide(canvas, locale, slide)
        elif slide.kind == "sync":
            render_sync_slide(canvas, locale, slide)
        else:
            render_phone_slide(canvas, locale, slide)

    output = output_root / locale / "images" / "phoneScreenshots" / f"{index}.png"
    output.parent.mkdir(parents=True, exist_ok=True)
    canvas.convert("RGB").save(output, optimize=True, compress_level=9)
    validate_output(output, SCREENSHOT_SIZE)


def render_feature_graphic(output_root: Path, locale: str) -> None:
    background = Image.open(PROMO_SOURCE_DIR / "play-store-light-motion.png")
    canvas = cover_image(background, FEATURE_GRAPHIC_SIZE)
    canvas.alpha_composite(Image.new("RGBA", FEATURE_GRAPHIC_SIZE, (255, 248, 250, 52)))
    draw = ImageDraw.Draw(canvas)
    logo = Image.open(SITE_ASSET_DIR / "candy-icon.png").convert("RGBA").resize((58, 58), Image.Resampling.LANCZOS)
    canvas.alpha_composite(logo, (54, 34))
    draw.text((124, 47), "Candy Browser", font=font(25, weight="bold"), fill=INK)

    draw.text((58, 132), "A sweeter way", font=font(54, weight="bold"), fill=INK)
    draw.text((58, 194), "to browse.", font=font(54, weight="bold"), fill=PRIMARY)
    draw.text((60, 296), "Material 3 Expressive Web Browser", font=font(21, weight="bold"), fill=MUTED)

    privacy_card = content_card(Image.open(SCREENSHOT_DIR / "candy-privacy.png"), (142, 292), radius=22, shadow_pad=18)
    trail_card = content_card(Image.open(SCREENSHOT_DIR / "candy-trail.png"), (142, 292), radius=22, shadow_pad=18)
    tabs_card = content_card(Image.open(SITE_ASSET_DIR / "candy-tabs-coverflow.png"), (194, 402), radius=26, shadow_pad=20)
    canvas.alpha_composite(privacy_card, (548, 118))
    canvas.alpha_composite(trail_card, (842, 126))
    canvas.alpha_composite(tabs_card, (676, 48))

    output = output_root / locale / "images" / "featureGraphic.png"
    output.parent.mkdir(parents=True, exist_ok=True)
    canvas.convert("RGB").save(output, optimize=True, compress_level=9)
    validate_output(output, FEATURE_GRAPHIC_SIZE)


def validate_output(path: Path, expected_size: tuple[int, int]) -> None:
    with Image.open(path) as image:
        if image.size != expected_size:
            raise ValueError(f"{path}: expected {expected_size}, got {image.size}")
        if image.mode != "RGB":
            raise ValueError(f"{path}: expected RGB PNG without alpha, got {image.mode}")


def validate_bundle(output_root: Path) -> None:
    expected_names = {f"{index}.png" for index in range(1, len(SLIDES) + 1)}
    for locale in ("en-US", "de-DE"):
        screenshot_dir = output_root / locale / "images" / "phoneScreenshots"
        actual_names = {path.name for path in screenshot_dir.glob("*.png")}
        if actual_names != expected_names:
            raise ValueError(f"{screenshot_dir}: expected {sorted(expected_names)}, got {sorted(actual_names)}")
        validate_output(output_root / locale / "images" / "featureGraphic.png", FEATURE_GRAPHIC_SIZE)
        for name in sorted(expected_names):
            validate_output(screenshot_dir / name, SCREENSHOT_SIZE)
    validate_output(
        output_root / "concepts" / "tabs-triptych.png",
        (SCREENSHOT_SIZE[0] * 3, SCREENSHOT_SIZE[1]),
    )


def publish_bundle(staged_root: Path) -> None:
    expected_names = {f"{index}.png" for index in range(1, len(SLIDES) + 1)}
    for locale in ("en-US", "de-DE"):
        final_screenshot_dir = METADATA_DIR / locale / "images" / "phoneScreenshots"
        final_screenshot_dir.mkdir(parents=True, exist_ok=True)
        unexpected = {path.name for path in final_screenshot_dir.glob("*.png")} - expected_names
        if unexpected:
            raise ValueError(f"{final_screenshot_dir}: unexpected screenshots {sorted(unexpected)}")

    for locale in ("en-US", "de-DE"):
        staged_images = staged_root / locale / "images"
        final_images = METADATA_DIR / locale / "images"
        final_images.mkdir(parents=True, exist_ok=True)
        shutil.copy2(staged_images / "featureGraphic.png", final_images / "featureGraphic.png")
        for index in range(1, len(SLIDES) + 1):
            shutil.copy2(
                staged_images / "phoneScreenshots" / f"{index}.png",
                final_images / "phoneScreenshots" / f"{index}.png",
            )

    CONCEPT_DIR.mkdir(parents=True, exist_ok=True)
    for obsolete_name in ("tabs-bouquet.png", "tabs-spotlight.png", "tabs-stack.png"):
        (CONCEPT_DIR / obsolete_name).unlink(missing_ok=True)
    shutil.copy2(
        staged_root / "concepts" / "tabs-triptych.png",
        CONCEPT_DIR / "tabs-triptych.png",
    )


def main() -> None:
    with tempfile.TemporaryDirectory(prefix="candy-play-store-") as temporary_directory:
        staged_root = Path(temporary_directory)
        for locale in ("en-US", "de-DE"):
            render_feature_graphic(staged_root, locale)
            for index, slide in enumerate(SLIDES, start=1):
                render_slide(staged_root, locale, index, slide)
        render_tab_concept(staged_root)
        validate_bundle(staged_root)
        publish_bundle(staged_root)

    for locale in ("en-US", "de-DE"):
        print(METADATA_DIR / locale / "images")


if __name__ == "__main__":
    main()
