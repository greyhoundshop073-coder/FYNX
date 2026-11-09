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
# These time ranges were inspected in the real 1080x1920 Android recording.
# They show the FYNX splash, the clean Home surface before the service warning,
# and the Marketplace feature banner/shortcut row. Backend-error frames are not
# used in the promotional highlight; the untouched full recording remains included.
segments = [(10.0, 12.8), (39.0, 40.8), (75.0, 94.5)]
for start, end in segments:
    if end > raw_duration or end <= start:
        raise SystemExit(f"Recording is too short for the inspected segment {start}-{end}s.")
crossfade = 0.25
d1 = segments[0][1] - segments[0][0]
d2 = segments[1][1] - segments[1][0]
d3 = segments[2][1] - segments[2][0]
first_join_duration = d1 + d2 - crossfade
video_duration = first_join_duration + d3 - crossfade
second_offset = first_join_duration - crossfade

narration = (
    "Welcome to FYNX. Connect, share, trade and grow. "
    "Start at Home, then open Marketplace. "
    "Explore featured announcements, special offers and seller highlights. "
    "Search and category shortcuts sit alongside quick access to My Shop, Wallet and Orders. "
    "FYNX brings discovery and commerce together in one connected experience."
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
        check=True, timeout=45, stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True
    )
    (root / "voice-generation-note.txt").write_text(
        voice_mode + "\n" + str(exc) + "\n", encoding="utf-8"
    )

voice_duration = duration(voice)
if voice_duration < 5:
    raise SystemExit("Narration audio is unexpectedly short.")
tempo = max(0.80, min(1.20, voice_duration / video_duration))

# Captions follow the three visible sections. The lower caption band is kept
# below the Marketplace card and above the Android navigation bar.
cue_times = [
    (0.0, d1 - crossfade, "WELCOME TO FYNX", "Connect • Share • Trade • Grow"),
    (d1 - crossfade, first_join_duration - crossfade, "HOME", "Your starting point"),
    (first_join_duration - crossfade, first_join_duration + 9.0, "MARKETPLACE", "Featured announcements, offers and seller highlights"),
    (first_join_duration + 9.0, video_duration, "EXPLORE MORE", "Search, categories and quick seller shortcuts"),
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

# Store caption strings in small text files so drawtext renders legible, compact
# titles without the oversized/vertically misplaced SRT rendering from the first take.
font_bold = "/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf"
font_regular = "/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf"
filters = [
    f"[0:v]trim=start={segments[0][0]}:end={segments[0][1]},setpts=PTS-STARTPTS,"
    "scale=1080:1920:flags=lanczos,fps=30,setsar=1,format=yuv420p[v0]",
    f"[0:v]trim=start={segments[1][0]}:end={segments[1][1]},setpts=PTS-STARTPTS,"
    "scale=1080:1920:flags=lanczos,fps=30,setsar=1,format=yuv420p[v1]",
    f"[0:v]trim=start={segments[2][0]}:end={segments[2][1]},setpts=PTS-STARTPTS,"
    "scale=1080:1920:flags=lanczos,fps=30,setsar=1,format=yuv420p[v2]",
    f"[v0][v1]xfade=transition=fade:duration={crossfade}:offset={d1-crossfade}[v01]",
    f"[v01][v2]xfade=transition=fade:duration={crossfade}:offset={second_offset}[v02]",
]
last = "v02"
for index, (start, end, title, subtitle) in enumerate(cue_times, start=1):
    title_file = root / f"caption-{index}-title.txt"
    subtitle_file = root / f"caption-{index}-subtitle.txt"
    title_file.write_text(title, encoding="utf-8")
    subtitle_file.write_text(subtitle, encoding="utf-8")
    next_label = f"cap{index}"
    filters.append(
        f"[{last}]drawbox=x=52:y=ih-288:w=iw-104:h=88:color=0x002B52@0.86:t=fill:"
        f"enable='between(t\\,{start:.3f}\\,{end:.3f})',"
        f"drawtext=fontfile={font_bold}:textfile={title_file}:fontsize=29:fontcolor=white:"
        f"x=(w-text_w)/2:y=h-270:enable='between(t\\,{start:.3f}\\,{end:.3f})',"
        f"drawtext=fontfile={font_regular}:textfile={subtitle_file}:fontsize=20:fontcolor=white:"
        f"x=(w-text_w)/2:y=h-231:enable='between(t\\,{start:.3f}\\,{end:.3f})'[{next_label}]"
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
    "# FYNX narrated app highlight\n\n"
    "- Source: actual Android screenrecord from the canonical FYNX APK.\n"
    "- Edit: real FYNX splash, clean Home view, then the Marketplace feature banner and shortcut row.\n"
    "- Full raw screen recording is preserved separately in this artifact.\n"
    "- Audio: " + voice_mode + ".\n"
    "- Captions: burned-in lower-third captions plus the matching SRT file.\n"
    "- Scope: this highlight does not claim that Marketplace listings or backend-dependent services are live.\n"
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
