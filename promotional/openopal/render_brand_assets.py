from __future__ import annotations

import argparse
import math
import os
from pathlib import Path

import cv2
import numpy as np
from PIL import Image, ImageDraw, ImageFilter, ImageFont


ROOT = Path(__file__).resolve().parents[2]
OUTPUT = ROOT / "promotional" / "openopal"
RESOURCE_ROOT = ROOT / "src" / "client" / "resources" / "assets" / "opal"
FONT_PATH = RESOURCE_ROOT / "fonts" / "borel-regular.ttf"

VIDEO_WIDTH = 3840
VIDEO_HEIGHT = 2160
VIDEO_FPS = 30
VIDEO_SECONDS = 4.0

INNER_PATH = (
    ((58, 13), (54, 17), (51, 25), (53, 32)),
    ((53, 32), (54, 39), (66, 49), (97, 64)),
    ((97, 64), (84, 66), (72, 80), (61, 91)),
    ((61, 91), (55, 96), (50, 98), (47, 98)),
    ((47, 98), (50, 92), (52, 86), (51, 79)),
    ((51, 79), (50, 70), (42, 59), (40, 51)),
    ((40, 51), (37, 40), (40, 29), (47, 21)),
    ((47, 21), (51, 17), (55, 14), (58, 13)),
)


def clamp01(value: float) -> float:
    return max(0.0, min(1.0, value))


def smoothstep(value: float) -> float:
    value = clamp01(value)
    return value * value * (3.0 - 2.0 * value)


def smootherstep(value: float) -> float:
    value = clamp01(value)
    return value * value * value * (value * (value * 6.0 - 15.0) + 10.0)


def cubic_point(segment, t: float) -> tuple[float, float]:
    p0, p1, p2, p3 = segment
    u = 1.0 - t
    x = u**3 * p0[0] + 3.0 * u * u * t * p1[0] + 3.0 * u * t * t * p2[0] + t**3 * p3[0]
    y = u**3 * p0[1] + 3.0 * u * u * t * p1[1] + 3.0 * u * t * t * p2[1] + t**3 * p3[1]
    return x, y


def render_logo_mask(size: int) -> Image.Image:
    supersample = 2
    render_size = size * supersample
    points = []
    for segment in INNER_PATH:
        for index in range(64):
            x, y = cubic_point(segment, index / 64.0)
            points.append((round(x / 128.0 * render_size), round(y / 128.0 * render_size)))

    mask = Image.new("L", (render_size, render_size), 0)
    draw = ImageDraw.Draw(mask)
    draw.ellipse((2, 2, render_size - 3, render_size - 3), fill=255)
    draw.polygon(points, fill=0)
    return mask.resize((size, size), Image.Resampling.LANCZOS)


def render_gradient(size: int) -> Image.Image:
    gradient_size = min(size, 1024)
    y, x = np.mgrid[0:gradient_size, 0:gradient_size].astype(np.float32)
    x /= gradient_size - 1
    y /= gradient_size - 1

    top = np.stack((83.0 - 18.0 * x, 188.0 + 8.0 * x, 253.0 - 15.0 * x), axis=2)
    bottom = np.stack((52.0 + 72.0 * x, 161.0 + 56.0 * x, 247.0 - x), axis=2)
    color = top * (1.0 - y[..., None]) + bottom * y[..., None]

    def radial(cx: float, cy: float, sx: float, sy: float) -> np.ndarray:
        return np.exp(-0.5 * (((x - cx) / sx) ** 2 + ((y - cy) / sy) ** 2))[..., None]

    color += radial(0.25, 0.73, 0.22, 0.24) * np.array((-24.0, -25.0, -2.0))
    color += radial(0.58, 0.82, 0.25, 0.22) * np.array((28.0, 28.0, 2.0))
    color += radial(0.55, 0.05, 0.35, 0.22) * np.array((8.0, 5.0, 4.0))

    image = Image.fromarray(np.clip(color, 0, 255).astype(np.uint8), "RGB")
    if gradient_size != size:
        image = image.resize((size, size), Image.Resampling.BICUBIC)
    return image


def render_logo(size: int) -> Image.Image:
    mask = render_logo_mask(size)
    logo = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    logo.paste(render_gradient(size), (0, 0), mask)
    return logo


def render_wordmark() -> Image.Image:
    text = "OpenOpal"
    font = ImageFont.truetype(str(FONT_PATH), 420)
    measurement = Image.new("L", (4096, 1024), 0)
    draw = ImageDraw.Draw(measurement)
    box = draw.textbbox((0, 0), text, font=font)
    padding = 48
    width = box[2] - box[0] + padding * 2
    height = box[3] - box[1] + padding * 2
    image = Image.new("RGBA", (width, height), (0, 0, 0, 0))
    ImageDraw.Draw(image).text(
        (padding - box[0], padding - box[1]),
        text,
        font=font,
        fill=(255, 255, 255, 255),
    )
    return image


def render_glow(logo: Image.Image, size: int = 1024) -> Image.Image:
    alpha = logo.getchannel("A").resize((700, 700), Image.Resampling.LANCZOS)
    canvas = Image.new("L", (size, size), 0)
    canvas.paste(alpha, ((size - 700) // 2, (size - 700) // 2))
    canvas = canvas.filter(ImageFilter.GaussianBlur(64))
    canvas = canvas.point(lambda value: round(value * 0.46))
    glow = Image.new("RGBA", (size, size), (53, 179, 255, 0))
    glow.putalpha(canvas)
    return glow


def render_menu_button(fill: tuple[int, int, int], alpha: int, border_alpha: int) -> Image.Image:
    scale = 4
    width = 256 * scale
    height = 32 * scale
    radius = 8 * scale
    mask = Image.new("L", (width, height), 0)
    ImageDraw.Draw(mask).rounded_rectangle((1, 1, width - 2, height - 2), radius=radius, fill=255)

    gradient = Image.new("RGBA", (width, height), (0, 0, 0, 0))
    pixels = gradient.load()
    for y in range(height):
        position = y / max(1, height - 1)
        light = round(10 * (1.0 - position) - 4 * position)
        row_alpha = round(alpha * (0.92 + 0.08 * (1.0 - position)))
        color = tuple(max(0, min(255, channel + light)) for channel in fill)
        for x in range(width):
            pixels[x, y] = (*color, row_alpha)
    gradient.putalpha(Image.composite(gradient.getchannel("A"), Image.new("L", (width, height), 0), mask))

    draw = ImageDraw.Draw(gradient)
    draw.rounded_rectangle(
        (2, 2, width - 3, height - 3),
        radius=radius - 1,
        outline=(255, 255, 255, border_alpha),
        width=3,
    )
    draw.line((radius, 4, width - radius, 4), fill=(255, 255, 255, min(62, border_alpha)), width=2)
    return gradient.resize((256, 32), Image.Resampling.LANCZOS)


def render_boot_spinner_dot(size: int = 96) -> Image.Image:
    """Create a soft, antialiased white dot for the startup loading ring."""
    coordinate_y, coordinate_x = np.mgrid[0:size, 0:size].astype(np.float32)
    center = (size - 1) * 0.5
    distance = np.sqrt((coordinate_x - center) ** 2 + (coordinate_y - center) ** 2)
    core_radius = size * 0.205
    edge_radius = size * 0.405
    falloff = np.clip((edge_radius - distance) / (edge_radius - core_radius), 0.0, 1.0)
    alpha = np.where(distance <= core_radius, 1.0, falloff * falloff * (3.0 - 2.0 * falloff))

    image = np.full((size, size, 4), 255, dtype=np.uint8)
    image[:, :, 3] = np.round(alpha * 255.0).astype(np.uint8)
    return Image.fromarray(image, "RGBA")


def save_png(image: Image.Image, path: Path) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    image.save(path, optimize=True)


def save_runtime_assets(master_logo: Image.Image, wordmark: Image.Image) -> None:
    game_logo = master_logo.resize((1024, 1024), Image.Resampling.LANCZOS)
    save_png(game_logo, RESOURCE_ROOT / "images" / "logo_hd.png")
    save_png(render_glow(game_logo), RESOURCE_ROOT / "images" / "logo_glow.png")
    save_png(master_logo.resize((512, 512), Image.Resampling.LANCZOS), RESOURCE_ROOT / "icon.png")
    save_png(wordmark, RESOURCE_ROOT / "images" / "openopal_wordmark.png")
    save_png(render_menu_button((51, 57, 62), 148, 72), RESOURCE_ROOT / "images" / "menu_button.png")
    save_png(render_menu_button((64, 73, 79), 178, 104), RESOURCE_ROOT / "images" / "menu_button_hover.png")
    save_png(render_menu_button((39, 43, 47), 112, 48), RESOURCE_ROOT / "images" / "menu_button_disabled.png")
    save_png(render_boot_spinner_dot(), RESOURCE_ROOT / "images" / "boot_spinner_dot.png")

    for size in (16, 32, 48, 128, 256):
        icon = master_logo.resize((size, size), Image.Resampling.LANCZOS)
        if size <= 48:
            icon = icon.filter(ImageFilter.UnsharpMask(radius=0.55, percent=115, threshold=2))
        save_png(icon, RESOURCE_ROOT / "window-icons" / f"icon_{size}x{size}.png")


def write_svg_master(path: Path) -> None:
    path.write_text(
        """<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 128 128">
  <defs>
    <linearGradient id="brand" x1="0.12" y1="0.08" x2="0.82" y2="0.92">
      <stop offset="0" stop-color="#53bcfd"/>
      <stop offset="0.48" stop-color="#36a8ef"/>
      <stop offset="1" stop-color="#83dff2"/>
    </linearGradient>
  </defs>
  <path fill="url(#brand)" fill-rule="evenodd" d="M64 0A64 64 0 1 1 64 128A64 64 0 1 1 64 0Z
    M58 13C54 17 51 25 53 32C54 39 66 49 97 64C84 66 72 80 61 91C55 96 50 98 47 98
    C50 92 52 86 51 79C50 70 42 59 40 51C37 40 40 29 47 21C51 17 55 14 58 13Z"/>
</svg>
""",
        encoding="ascii",
    )


def premultiplied_bgra(image: Image.Image) -> np.ndarray:
    rgba = np.asarray(image.convert("RGBA"), dtype=np.uint8)
    alpha = rgba[:, :, 3:4].astype(np.float32) / 255.0
    rgb = np.round(rgba[:, :, :3].astype(np.float32) * alpha).astype(np.uint8)
    return np.dstack((rgb[:, :, 2], rgb[:, :, 1], rgb[:, :, 0], rgba[:, :, 3]))


def warp_centered(image: np.ndarray, center_x: float, center_y: float, size: float, angle: float) -> np.ndarray:
    source_height, source_width = image.shape[:2]
    scale = size / max(source_width, source_height)
    matrix = cv2.getRotationMatrix2D((source_width / 2.0, source_height / 2.0), angle, scale)
    matrix[0, 2] += center_x - source_width / 2.0
    matrix[1, 2] += center_y - source_height / 2.0
    return cv2.warpAffine(
        image,
        matrix,
        (VIDEO_WIDTH, VIDEO_HEIGHT),
        flags=cv2.INTER_LANCZOS4,
        borderMode=cv2.BORDER_CONSTANT,
        borderValue=(0, 0, 0, 0),
    )


def composite(frame: np.ndarray, overlay: np.ndarray, opacity: float = 1.0) -> None:
    opacity = clamp01(opacity)
    if opacity <= 0.0:
        return
    alpha = overlay[:, :, 3:4].astype(np.float32) * (opacity / 255.0)
    source = overlay[:, :, :3].astype(np.float32) * opacity
    frame[:] = np.clip(source + frame.astype(np.float32) * (1.0 - alpha), 0, 255).astype(np.uint8)


def composite_at(
    frame: np.ndarray,
    overlay: np.ndarray,
    x: int,
    y: int,
    opacity: float = 1.0,
    visible_width: int | None = None,
) -> None:
    height, width = overlay.shape[:2]
    visible_width = width if visible_width is None else max(0, min(width, visible_width))
    if visible_width <= 0:
        return
    left = max(0, x)
    top = max(0, y)
    right = min(VIDEO_WIDTH, x + visible_width)
    bottom = min(VIDEO_HEIGHT, y + height)
    if left >= right or top >= bottom:
        return
    source = overlay[top - y : bottom - y, left - x : right - x]
    destination = frame[top:bottom, left:right]
    alpha = source[:, :, 3:4].astype(np.float32) * (clamp01(opacity) / 255.0)
    premultiplied = source[:, :, :3].astype(np.float32) * clamp01(opacity)
    destination[:] = np.clip(
        premultiplied + destination.astype(np.float32) * (1.0 - alpha), 0, 255
    ).astype(np.uint8)


def resize_premultiplied(image: np.ndarray, width: int, height: int) -> np.ndarray:
    return cv2.resize(image, (width, height), interpolation=cv2.INTER_AREA)


def open_writer(path: Path) -> tuple[cv2.VideoWriter, Path]:
    temporary = path.with_name(path.stem + ".tmp.mp4")
    if temporary.exists():
        temporary.unlink()
    writer = cv2.VideoWriter(
        str(temporary),
        cv2.VideoWriter_fourcc(*"avc1"),
        VIDEO_FPS,
        (VIDEO_WIDTH, VIDEO_HEIGHT),
    )
    if not writer.isOpened():
        writer = cv2.VideoWriter(
            str(temporary),
            cv2.VideoWriter_fourcc(*"mp4v"),
            VIDEO_FPS,
            (VIDEO_WIDTH, VIDEO_HEIGHT),
        )
    if not writer.isOpened():
        raise RuntimeError(f"Unable to create video writer for {path}")
    return writer, temporary


def render_intro(logo: np.ndarray, glow: np.ndarray) -> None:
    output = OUTPUT / "01_logo_spin_intro.mp4"
    writer, temporary = open_writer(output)
    frame_count = round(VIDEO_FPS * VIDEO_SECONDS)
    first_frame = None
    last_frame = None
    try:
        for index in range(frame_count):
            t = index / (frame_count - 1)
            ease = smootherstep(t)
            logo_size = 430.0 * ((5100.0 / 430.0) ** ease)
            angle = 18.0 + 540.0 * ease
            opacity = smoothstep(t / 0.12)
            frame = np.zeros((VIDEO_HEIGHT, VIDEO_WIDTH, 3), dtype=np.uint8)
            glow_layer = warp_centered(glow, VIDEO_WIDTH / 2.0, VIDEO_HEIGHT / 2.0, logo_size * 1.55, angle * 0.18)
            composite(frame, glow_layer, (0.58 - 0.33 * ease) * opacity)
            logo_layer = warp_centered(logo, VIDEO_WIDTH / 2.0, VIDEO_HEIGHT / 2.0, logo_size, angle)
            composite(frame, logo_layer, opacity)
            writer.write(frame)
            if first_frame is None:
                first_frame = frame.copy()
            last_frame = frame
            if index % VIDEO_FPS == 0:
                print(f"intro {index}/{frame_count}", flush=True)
    finally:
        writer.release()
    os.replace(temporary, output)
    cv2.imwrite(str(OUTPUT / "01_logo_center_frame.png"), first_frame)
    cv2.imwrite(str(OUTPUT / "01_logo_fill_frame.png"), last_frame)


def render_reverse_lockup(logo: np.ndarray, glow: np.ndarray, wordmark: np.ndarray) -> None:
    output = OUTPUT / "02_wordmark_reverse_lockup.mp4"
    writer, temporary = open_writer(output)
    frame_count = round(VIDEO_FPS * VIDEO_SECONDS)

    final_logo_size = 420
    final_wordmark_height = 380
    final_wordmark_width = round(wordmark.shape[1] * final_wordmark_height / wordmark.shape[0])
    final_wordmark = resize_premultiplied(wordmark, final_wordmark_width, final_wordmark_height)
    gap = 96
    group_width = final_wordmark_width + gap + final_logo_size
    group_x = (VIDEO_WIDTH - group_width) // 2
    final_logo_x = group_x + final_wordmark_width + gap + final_logo_size / 2.0
    final_logo_y = VIDEO_HEIGHT / 2.0

    last_frame = None
    try:
        for index in range(frame_count):
            t = index / (frame_count - 1)
            ease = smootherstep(t)
            logo_size = 5100.0 * ((final_logo_size / 5100.0) ** ease)
            logo_x = VIDEO_WIDTH / 2.0 + (final_logo_x - VIDEO_WIDTH / 2.0) * ease
            logo_y = VIDEO_HEIGHT / 2.0 + (final_logo_y - VIDEO_HEIGHT / 2.0) * ease
            angle = 558.0 * (1.0 - ease)
            frame = np.zeros((VIDEO_HEIGHT, VIDEO_WIDTH, 3), dtype=np.uint8)
            glow_layer = warp_centered(glow, logo_x, logo_y, logo_size * 1.55, angle * 0.18)
            composite(frame, glow_layer, 0.25 + 0.25 * ease)
            logo_layer = warp_centered(logo, logo_x, logo_y, logo_size, angle)
            composite(frame, logo_layer)

            reveal = smootherstep((t - 0.58) / 0.34)
            wordmark_x = group_x - round((1.0 - reveal) * 72.0)
            wordmark_y = round(VIDEO_HEIGHT / 2.0 - final_wordmark_height / 2.0)
            composite_at(
                frame,
                final_wordmark,
                wordmark_x,
                wordmark_y,
                reveal,
                round(final_wordmark_width * reveal),
            )
            writer.write(frame)
            last_frame = frame
            if index % VIDEO_FPS == 0:
                print(f"lockup {index}/{frame_count}", flush=True)
    finally:
        writer.release()
    os.replace(temporary, output)
    cv2.imwrite(str(OUTPUT / "02_openopal_lockup_frame.png"), last_frame)


def main() -> None:
    parser = argparse.ArgumentParser(description="Render OpenOpal brand assets and promotional footage.")
    parser.add_argument("--skip-video", action="store_true")
    args = parser.parse_args()

    OUTPUT.mkdir(parents=True, exist_ok=True)
    master_logo = render_logo(4096)
    wordmark = render_wordmark()
    glow = render_glow(master_logo.resize((1024, 1024), Image.Resampling.LANCZOS), 1024)

    save_png(master_logo, OUTPUT / "openopal_logo_hd.png")
    save_png(wordmark, OUTPUT / "openopal_wordmark.png")
    save_png(glow, OUTPUT / "openopal_logo_glow.png")
    save_runtime_assets(master_logo, wordmark)
    write_svg_master(OUTPUT / "openopal_logo_master.svg")

    if not args.skip_video:
        logo_bgra = premultiplied_bgra(master_logo)
        glow_bgra = premultiplied_bgra(glow)
        wordmark_bgra = premultiplied_bgra(wordmark)
        render_intro(logo_bgra, glow_bgra)
        render_reverse_lockup(logo_bgra, glow_bgra, wordmark_bgra)


if __name__ == "__main__":
    main()
