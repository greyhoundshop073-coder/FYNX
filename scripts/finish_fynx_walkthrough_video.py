#!/usr/bin/env python3
"""Narrate the real FYNX Android walkthrough and burn in timed captions."""
from pathlib import Path
import subprocess

root = Path("fynx-walkthrough")
source = root / "FYNX-Real-App-Walkthrough.mp4"
neural_voice = root / "FYNX-Walkthrough-Narration.mp3"
fallback_voice = root / "FYNX-Walkthrough-Narration.wav"
captions = root / "FYNX-Walkthrough-Captions.srt"
output = root / "FYNX-Narrated-Walkthrough.mp4"

if not source.is_file() or source.stat().st_size < 500_000:
    raise SystemExit("A usable real Android screen recording is required before narration.")

# This script deliberately matches the recording driver's read-only scope:
# Home as entry point, Marketplace, My Shop and Wallet. It does not claim to
# show the camera, social feed, chat or other screens that are not in this take.
narration = (
    "Welcome to FYNX, a connected place for discovering products, shopping, and selling. "
    "From Home, open Marketplace to explore the product discovery experience. "
    "Use the seller entry point to preview the product listing flow, without publishing a listing. "
    "My Shop brings your store management tools together. "
    "Explore the shop profile, listings, order management, delivery and driver coordination, "
    "and earnings and payouts. "
    "Wallet is also accessible within the wider FYNX experience. "
    "This walkthrough shows real screens from the running Android app. "
    "No live listing, order, or payment is being created."
)

voice_mode = "Microsoft Edge neural voice en-NG-AbeoNeural"
voice = neural_voice
try:
    subprocess.run(
        [
            "edge-tts",
            "--voice", "en-NG-AbeoNeural",
            "--rate=-4%",
            "--volume=+3%",
            "--text", narration,
            "--write-media", str(neural_voice),
        ],
        check=True, timeout=75, stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True
    )
except (FileNotFoundError, subprocess.CalledProcessError, subprocess.TimeoutExpired) as exc:
    voice_mode = "eSpeak NG local fallback (neural voice unavailable)"
    voice = fallback_voice
    subprocess.run(
        ["espeak-ng", "-v", "en-ng", "-s", "142", "-a", "165", "-w", str(voice), narration],
        check=True, timeout=45
    )
    (root / "voice-generation-note.txt").write_text(
        voice_mode + "\n" + str(exc) + "\n", encoding="utf-8"
    )

def probe_duration(path: Path) -> float:
    result = subprocess.run(
        [
            "ffprobe", "-v", "error", "-show_entries", "format=duration",
            "-of", "default=noprint_wrappers=1:nokey=1", str(path)
        ],
        text=True, stdout=subprocess.PIPE, stderr=subprocess.STDOUT, check=True, timeout=30
    )
    return float(result.stdout.strip())

voice_duration = probe_duration(voice)
if voice_duration <= 5:
    raise SystemExit("Narration audio is unexpectedly short.")

# Captions are distributed across the measured voice duration, not fixed
# timestamps, so the last line remains aligned with either voice engine.
caption_cards = [
    ("WELCOME TO FYNX", "Discovery, shopping and selling in one place"),
    ("MARKETPLACE", "Explore products and seller listings"),
    ("START SELLING", "Preview the product listing flow"),
    ("MY SHOP", "Storefront and seller management"),
    ("ORDERS & DELIVERY", "Explore fulfillment and driver tools"),
    ("EARNINGS & PAYOUTS", "Seller management and payout areas"),
    ("WALLET", "A connected FYNX money area"),
    ("REAL APP FOOTAGE", "No live listing, order or payment is created"),
]
weights = [max(1, len((headline + " " + subtitle).split())) for headline, subtitle in caption_cards]
total_weight = sum(weights)
usable_duration = max(1.0, voice_duration - 0.2)

def srt_time(seconds: float) -> str:
    milliseconds = max(0, int(round(seconds * 1000)))
    hours, remainder = divmod(milliseconds, 3_600_000)
    minutes, remainder = divmod(remainder, 60_000)
    secs, millis = divmod(remainder, 1000)
    return f"{hours:02d}:{minutes:02d}:{secs:02d},{millis:03d}"

subtitle_blocks = []
elapsed_weight = 0
for index, (headline, subtitle) in enumerate(caption_cards, start=1):
    start = usable_duration * elapsed_weight / total_weight
    elapsed_weight += weights[index - 1]
    end = usable_duration * elapsed_weight / total_weight
    subtitle_blocks.append(
        f"{index}\n{srt_time(start)} --> {srt_time(end)}\n{headline}\n{subtitle}"
    )
captions.write_text("\n\n".join(subtitle_blocks) + "\n", encoding="utf-8")

# The actual Android capture remains the video source. The voiceover and
# time-matched captions are added to that footage; no screenshot slideshow is made.
filter_value = (
    f"subtitles={captions}:"
    "force_style='FontName=DejaVu Sans,FontSize=20,"
    "PrimaryColour=&H00FFFFFF,OutlineColour=&H00101010,"
    "BorderStyle=1,Outline=2,Shadow=1,Alignment=2,MarginV=42'"
)
subprocess.run(
    [
        "ffmpeg", "-y", "-i", str(source), "-i", str(voice),
        "-vf", filter_value,
        "-map", "0:v:0", "-map", "1:a:0",
        "-c:v", "libx264", "-preset", "medium", "-crf", "21",
        "-pix_fmt", "yuv420p", "-c:a", "aac", "-b:a", "192k",
        "-shortest", "-movflags", "+faststart", str(output)
    ],
    check=True, timeout=600
)

probe = subprocess.run(
    [
        "ffprobe", "-v", "error", "-show_entries", "format=duration,size",
        "-show_entries", "stream=codec_type,codec_name,width,height",
        "-of", "default=noprint_wrappers=1", str(output)
    ],
    text=True, stdout=subprocess.PIPE, stderr=subprocess.STDOUT, check=True, timeout=30
)
if output.stat().st_size < 500_000:
    raise SystemExit("Narrated MP4 is unexpectedly small.")
final_duration = probe_duration(output)
if final_duration < 5:
    raise SystemExit("Narrated MP4 duration is unexpectedly short.")

(root / "narrated-video-report.md").write_text(
    "# Narrated FYNX Marketplace walkthrough\n\n"
    "- Video source: actual Android emulator screen recording from the canonical FYNX APK.\n"
    "- Recorded scope: Home entry point, Marketplace, My Shop and Wallet.\n"
    "- Audio: " + voice_mode + ".\n"
    "- Captions: burned into the footage and timed to the measured narration duration.\n"
    "- Safety: the walkthrough does not publish a listing or create an order or payment.\n"
    "- Output: FYNX-Narrated-Walkthrough.mp4\n\n"
    + probe.stdout + "\n",
    encoding="utf-8"
)
print(voice_mode)
print(probe.stdout)
