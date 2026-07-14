from __future__ import annotations

import argparse
import os
import struct
from pathlib import Path

import cv2


ROOT = Path(__file__).resolve().parents[2]
SOURCE = ROOT / "src" / "client" / "resources" / "assets" / "opal" / "videos" / "background.mp4"
OUTPUT = ROOT / "src" / "client" / "resources" / "assets" / "opal" / "videos" / "background_hd.opv"

MAGIC = b"OPVF"
VERSION = 1
WIDTH = 1920
HEIGHT = 1080
FPS = 24
HEADER = struct.Struct(">4sIIIII")
ENTRY = struct.Struct(">II")


def main() -> None:
    parser = argparse.ArgumentParser(description="Build the OpenOpal HD menu background frame pack.")
    parser.add_argument("--quality", type=int, default=88)
    args = parser.parse_args()

    capture = cv2.VideoCapture(str(SOURCE))
    if not capture.isOpened():
        raise RuntimeError(f"Unable to open {SOURCE}")
    source_fps = capture.get(cv2.CAP_PROP_FPS) or 60.0
    source_frames = int(capture.get(cv2.CAP_PROP_FRAME_COUNT))
    duration = source_frames / source_fps
    frame_count = max(1, round(duration * FPS))

    temporary = OUTPUT.with_suffix(".tmp.opv")
    offsets: list[tuple[int, int]] = []
    OUTPUT.parent.mkdir(parents=True, exist_ok=True)
    with temporary.open("wb") as output:
        output.write(HEADER.pack(MAGIC, VERSION, WIDTH, HEIGHT, FPS, frame_count))
        output.write(b"\0" * (frame_count * ENTRY.size))

        for index in range(frame_count):
            capture.set(cv2.CAP_PROP_POS_MSEC, index * 1000.0 / FPS)
            success, frame = capture.read()
            if not success:
                raise RuntimeError(f"Unable to decode source frame {index}")
            frame = cv2.resize(frame, (WIDTH, HEIGHT), interpolation=cv2.INTER_LANCZOS4)
            softened = cv2.GaussianBlur(frame, (0, 0), 0.72)
            frame = cv2.addWeighted(frame, 1.10, softened, -0.10, 0.0)
            encoded, jpeg = cv2.imencode(
                ".jpg",
                frame,
                (cv2.IMWRITE_JPEG_QUALITY, max(70, min(96, args.quality))),
            )
            if not encoded:
                raise RuntimeError(f"Unable to encode frame {index}")
            data = jpeg.tobytes()
            offsets.append((output.tell(), len(data)))
            output.write(data)
            if index % FPS == 0:
                print(f"background {index}/{frame_count}", flush=True)

        output.seek(HEADER.size)
        for offset, length in offsets:
            output.write(ENTRY.pack(offset, length))

    capture.release()
    os.replace(temporary, OUTPUT)
    print(f"wrote {OUTPUT} ({OUTPUT.stat().st_size / 1048576:.2f} MiB)", flush=True)


if __name__ == "__main__":
    main()
