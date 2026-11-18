#!/usr/bin/env python3
"""Keep the app, the server and the camera agreeing on how large a post's media can be."""
import re
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
UI = ROOT / "app/src/main/java/com/fynx/app/ui"


def read(path):
    return path.read_text(encoding="utf-8")


def check(name, condition):
    if not condition:
        raise SystemExit("FAIL: " + name)
    print("PASS: " + name)


client = read(UI / "FynxMultiMediaPostClient.kt")
camera = read(UI / "FynxCameraCapturePanel.kt")
messaging = read(UI / "FynxProductionMessaging.kt")
server = read(ROOT / "backend/server.js")
storage = read(ROOT / "backend/mediaStorage.js")

MB = 1024 * 1024
client_limit = int(re.search(r"MAX_SINGLE_MEDIA_BYTES\s*=\s*(\d+)L\s*\*\s*1024L\s*\*\s*1024L", client).group(1)) * MB
server_limit = int(re.search(r"const MAX_MEDIA_BYTES\s*=\s*(\d+)\s*\*\s*1024\s*\*\s*1024", server).group(1)) * MB
storage_limit = int(re.search(r"MAX_MEDIA_OBJECT_BYTES\s*=\s*(\d+)\s*\*\s*1024\s*\*\s*1024", storage).group(1)) * MB
check("the app, the server and the storage layer allow the same media size", client_limit == server_limit == storage_limit)

json_limit = int(re.search(r'express\.json\(\{\s*limit:\s*"(\d+)mb"', server).group(1)) * MB
check("the JSON body limit holds a maximum-size upload after base64 encoding", json_limit >= server_limit * 4 / 3 + MB // 4)

safe_bytes = int(re.search(r"FYNX_UPLOAD_SAFE_VIDEO_BYTES\s*=\s*(\d+)L\s*\*\s*1024L\s*\*\s*1024L", client).group(1)) * MB
check("a recording stops safely under the upload limit", 0 < safe_bytes < client_limit)

bitrate = int(re.search(r"FYNX_UPLOAD_SAFE_VIDEO_BITRATE\s*=\s*([\d_]+)", client).group(1).replace("_", ""))
seconds = safe_bytes * 8 / (bitrate + 128_000)
check("the post video bitrate leaves at least 30 seconds of recording", seconds >= 30)

check("camera recordings for posts and chats use the upload-safe size cap", "val maximumBytes = if (statusCapture) FYNX_STATUS_MAX_VIDEO_BYTES else FYNX_UPLOAD_SAFE_VIDEO_BYTES" in camera)
check("camera recordings for posts and chats use the upload-safe bitrate", "setTargetVideoEncodingBitRate(if (statusCapture) 700_000 else FYNX_UPLOAD_SAFE_VIDEO_BITRATE)" in camera)
check("camera recordings no longer start at UHD or FHD", "Quality.UHD" not in camera.split("val qualitySelector")[1].split("val recorder")[0])
check("chat uploads use the same 12 MB limit", re.search(r"MAX_MEDIA_BYTES\s*=\s*12", messaging) is not None)
print("Post upload limit verification passed")
