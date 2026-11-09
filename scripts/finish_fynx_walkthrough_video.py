#!/usr/bin/env python3
"""Add original spoken narration and burned-in captions to the real app recording."""
from pathlib import Path
import subprocess

root = Path("fynx-walkthrough")
source = root / "FYNX-Real-App-Walkthrough.mp4"
voice = root / "FYNX-Walkthrough-Narration.wav"
captions = root / "FYNX-Walkthrough-Captions.srt"
output = root / "FYNX-Narrated-Walkthrough.mp4"

narration = (
    "Welcome to FYNX. One place to connect, share, discover and shop. "
    "Start on Home and explore the social feed, posts and conversations. "
    "Open the camera experience to see how content creation begins. "
    "Visit Marketplace to discover products and explore the shopping experience. "
    "FYNX brings social discovery and commerce together in one application. "
    "Explore the available sections and see how the experience is designed to flow. "
    "This is a walkthrough of the actual FYNX Android application."
)
subprocess.run(
    ["espeak-ng", "-v", "en-ng", "-s", "145", "-a", "165", "-w", str(voice), narration],
    check=True
)

subtitle_text = """1
00:00:00,000 --> 00:00:07,000
WELCOME TO FYNX
Connect • Share • Discover • Shop

2
00:00:07,000 --> 00:00:16,000
HOME & SOCIAL
Explore the feed and conversations

3
00:00:16,000 --> 00:00:25,000
CREATE
Open the camera and explore content creation

4
00:00:25,000 --> 00:00:36,000
MARKETPLACE
Discover products and the shopping experience

5
00:00:36,000 --> 00:00:48,000
ONE FYNX EXPERIENCE
Social discovery and commerce in one app
"""
captions.write_text(subtitle_text, encoding="utf-8")

# The actual app recording remains the video source. Narration is an original
# generated voice track; subtitles are burned into the same real screen footage.
subprocess.run([
    "ffmpeg", "-y", "-i", str(source), "-i", str(voice),
    "-vf", f"subtitles={captions}:force_style='FontName=DejaVu Sans,FontSize=20,PrimaryColour=&H00FFFFFF,OutlineColour=&H00101010,BorderStyle=1,Outline=2,Shadow=1,Alignment=2,MarginV=42'",
    "-map", "0:v:0", "-map", "1:a:0", "-c:v", "libx264", "-preset", "medium",
    "-crf", "21", "-c:a", "aac", "-b:a", "160k", "-shortest",
    "-movflags", "+faststart", str(output)
], check=True)

probe = subprocess.run(
    ["ffprobe", "-v", "error", "-show_entries", "format=duration,size",
     "-of", "default=noprint_wrappers=1", str(output)],
    text=True, stdout=subprocess.PIPE, stderr=subprocess.STDOUT, check=True
)
if output.stat().st_size < 500_000:
    raise SystemExit("Narrated MP4 is unexpectedly small.")
(root / "narrated-video-report.md").write_text(
    "# Narrated FYNX walkthrough\n\n"
    "- Video source: actual Android emulator screen recording.\n"
    "- Audio: original generated English narration.\n"
    "- Captions: burned-in captions.\n"
    "- Output: FYNX-Narrated-Walkthrough.mp4\n\n"
    + probe.stdout + "\n", encoding="utf-8"
)
print(probe.stdout)
