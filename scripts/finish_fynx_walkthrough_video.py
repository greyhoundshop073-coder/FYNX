#!/usr/bin/env python3
"""Create a short, honestly scoped FYNX promo from selected real Android footage."""
from pathlib import Path
import subprocess

root = Path("fynx-walkthrough")
source = root / "FYNX-Real-App-Walkthrough.mp4"
neural_voice = root / "FYNX-Walkthrough-Narration.mp3"
fallback_voice = root / "FYNX-Walkthrough-Narration.wav"
captions = root / "FYNX-Walkthrough-Captions.srt"
output = root / "FYNX-Narrated-Walkthrough.mp4"

if not source.is_file() or source.stat().st_size < 500_000:
    raise SystemExit("A usable real Android screen recording is required.")

def probe(path: Path, entries: str) -> str:
    result = subprocess.run(
        ["ffprobe", "-v", "error", "-show_entries", entries,
         "-of", "default=noprint_wrappers=1:nokey=1", str(path)],
        text=True, stdout=subprocess.PIPE, stderr=subprocess.STDOUT,
        check=True, timeout=30
    )
    return result.stdout.strip()

def duration(path: Path) -> float:
    return float(probe(path, "format=duration"))

raw_duration = duration(source)
# Inspected real Android frames:
# 10.0-12.8: FYNX splash/logo.
# 75.0-94.5: Marketplace banner, discovery copy, category row and quick shortcuts.
# The Home service-warning interval is intentionally omitted from the promotion.
segments = [(10.0, 12.8), (75.0, 94.5)]
for start, end in segments:
    if end > raw_duration or end <= start:
        raise SystemExit(f"Recording is too short for the inspected segment {start}-{end}s.")
crossfade = 0.25
d1 = segments[0][1] - segments[0][0]
d2 = segments[1][1] - segments[1][0]
video_duration = d1 + d2 - crossfade
second_offset = d1 - crossfade

narration = (
    "Welcome to FYNX. Discover more with FYNX Marketplace. "
    "Explore announcements, special offers, featured sellers and product discovery. "
    "Browse categories, with quick access to Wallet, My Shop, Orders and saved products. "
    "FYNX brings discovery and commerce into one connected experience."
)

voice_mode = "Microsoft Edge neural voice en-NG-AbeoNeural"
voice = neural_voice
try:
    subprocess.run(
        [
            "edge-tts", "--voice", "en-NG-AbeoNeural", "--rate=-6%",
            "--volume=+3%", "--text", narration, "--write-media", str(neural_voice)
        ],
        check=True, timeout=75, stdout=subprocess.PIPE,
        stderr=subprocess.STDOUT, text=True
    )
except (FileNotFoundError, subprocess.CalledProcessError, subprocess.TimeoutExpired) as exc:
    voice_mode = "eSpeak NG local fallback (neural voice unavailable)"
    voice = fallback_voice
    subprocess.run(
        ["espeak-ng", "-v", "en-ng", "-s", "140", "-a", "165", "-w", str(voice), narration],
        check=True, timeout=45, stdout=subprocess.PIPE,
        stderr=subprocess.STDOUT, text=True
    )
    (root / "voice-generation-note.txt").write_text(
        voice_mode + "\n" + str(exc) + "\n", encoding="utf-8"
    )

voice_duration = duration(voice)
if voice_duration < 5:
    raise SystemExit("Narration audio is unexpectedly short.")
tempo = max(0.80, min(1.20, voice_duration / video_duration))

cue_times = [
    (0.0, d1 - crossfade, "WELCOME TO FYNX", "Discover • Connect • Grow"),
    (d1 - crossfade, 12.4, "MARKETPLACE", "Announcements, offers and featured sellers"),
    (12.4, video_duration, "EXPLORE MORE", "Categories, Wallet, My Shop and Orders"),
]

def srt_time(seconds: float) -> str:
    millis = max(0, int(round(seconds * 1000)))
    hours, remainder = divmod(millis, 3_600_000)
    minutes, remainder = divmod(remainder, 60_000)
    secs, millis = divmod(remainder, 1000)
    return f"{hours:02d}:{minutes:02d}:{secs:02d},{millis:03d}"

srt_parts = []
for index, (start, end, title, subtitle) in enumerate(cue_times, start=1):
    srt_parts.append(
        f"{index}\n{srt_time(start)} --> {srt_time(end)}\n{title}\n{subtitle}"
    )
captions.write_text("\n\n".join(srt_parts) + "\n", encoding="utf-8")

# Caption strip is placed in the free gap below the Marketplace banner and
# above the app's bottom navigation bar; it does not cover the navigation labels.
font_bold = "/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf"
font_regular = "/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf"
filters = [
    f"[0:v]trim=start={segments[0][0]}:end={segments[0][1]},setpts=PTS-STARTPTS,"
    "scale=1080:1920:flags=lanczos,fps=30,setsar=1,format=yuv420p[v0]",
    f"[0:v]trim=start={segments[1][0]}:end={segments[1][1]},setpts=PTS-STARTPTS,"
    "scale=1080:1920:flags=lanczos,fps=30,setsar=1,format=yuv420p[v1]",
    f"[v0][v1]xfade=transition=fade:duration={crossfade}:offset={second_offset}[v01]",
]
last = "v01"
for index, (start, end, title, subtitle) in enumerate(cue_times, start=1):
    title_file = root / f"caption-{index}-title.txt"
    subtitle_file = root / f"caption-{index}-subtitle.txt"
    title_file.write_text(title, encoding="utf-8")
    subtitle_file.write_text(subtitle, encoding="utf-8")
    next_label = f"cap{index}"
    filters.append(
        f"[{last}]drawbox=x=52:y=ih-550:w=iw-104:h=70:color=0x002B52@0.82:t=fill:"
        f"enable='between(t\\,{start:.3f}\\,{end:.3f})',"
        f"drawtext=fontfile={font_bold}:textfile={title_file}:fontsize=25:fontcolor=white:"
        f"x=(w-text_w)/2:y=h-535:enable='between(t\\,{start:.3f}\\,{end:.3f})',"
        f"drawtext=fontfile={font_regular}:textfile={subtitle_file}:fontsize=18:fontcolor=white:"
        f"x=(w-text_w)/2:y=h-505:enable='between(t\\,{start:.3f}\\,{end:.3f})'[{next_label}]"
    )
    last = next_label
filters.append(
    f"[{last}]fade=t=in:st=0:d=0.25,fade=t=out:st={video_duration-0.45:.3f}:d=0.45,"
    "format=yuv420p[vout]"
)
filter_complex = ";".join(filters)

subprocess.run(
    [
        "ffmpeg", "-y", "-i", str(source), "-i", str(voice),
        "-filter_complex", filter_complex,
        "-map", "[vout]", "-map", "1:a:0",
        "-af", f"atempo={tempo:.5f},apad,atrim=duration={video_duration:.3f}",
        "-c:v", "libx264", "-preset", "medium", "-crf", "19",
        "-r", "30", "-pix_fmt", "yuv420p",
        "-c:a", "aac", "-b:a", "192k", "-ar", "48000",
        "-t", f"{video_duration:.3f}", "-movflags", "+faststart",
        str(output)
    ],
    check=True, timeout=600
)

final_duration = duration(output)
probe_report = probe(
    output,
    "format=duration,size:stream=codec_type,codec_name,width,height,avg_frame_rate"
)
if output.stat().st_size < 200_000:
    raise SystemExit("Narrated MP4 is unexpectedly small.")
if abs(final_duration - video_duration) > 0.35:
    raise SystemExit(
        f"Final MP4 duration {final_duration:.3f}s does not match highlight duration {video_duration:.3f}s."
    )

(root / "narrated-video-report.md").write_text(
    "# FYNX narrated Marketplace highlight\n\n"
    "- Source: actual Android screenrecord from the canonical FYNX APK.\n"
    "- Edit: real FYNX splash, followed by the actual Marketplace banner and quick-shortcut screen.\n"
    "- The Home service-warning interval is excluded from the promotional edit.\n"
    "- Full raw screen recording is preserved separately in this artifact.\n"
    "- Audio: " + voice_mode + ".\n"
    "- Captions: burned-in lower-third captions plus the matching SRT file.\n"
    "- Captions are placed above the app navigation bar.\n"
    "- Scope: shows real UI; does not claim Marketplace listings or backend-dependent services are live.\n"
    "- No listing, post, comment, order, wallet funding or transfer was created.\n"
    "- Output: FYNX-Narrated-Walkthrough.mp4\n\n"
    + f"- Raw recording duration: {raw_duration:.3f} seconds.\n"
    + f"- Highlight duration: {final_duration:.3f} seconds.\n"
    + f"- Voice playback rate multiplier: {tempo:.3f}.\n\n"
    + probe_report + "\n",
    encoding="utf-8"
)
print(voice_mode)
print(f"Raw recording: {raw_duration:.3f}s; narrated highlight: {final_duration:.3f}s")
print(probe_report)
