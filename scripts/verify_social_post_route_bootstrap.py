#!/usr/bin/env python3
"""Regression gate for the social-post route and backend bootstrap newline defects."""
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]

def read(path: str) -> str:
    file_path = ROOT / path
    if not file_path.is_file():
        raise SystemExit(f"FAIL: required source file missing: {path}")
    return file_path.read_text(encoding="utf-8")

social = read("backend/socialRoutes.js")
server = read("backend/server.js")
bootstrap = read("backend/serverBootstrap.js")
foundation = read("backend/phase1FoundationBootstrap.js")
package_json = read("backend/package.json")

checks = [
    ("POST /api/social/posts is declared on a real source line",
     any(line.lstrip().startswith("app.post('/api/social/posts'")
         or line.lstrip().startswith('app.post("/api/social/posts"')
         for line in social.splitlines())),
    ("post route declaration is not swallowed by an escaped newline comment",
     "\\n  app.post('/api/social/posts'" not in social),
    ("server bootstrap keeps the guarded post-route fallback",
     "/api/social/posts" in bootstrap and "registerSocialRoutes" in bootstrap),
    ("foundation bootstrap writes patched source back to disk",
     'await writeFile(bootstrapPath, source, "utf8");' in foundation
     and "\\nawait writeFile(bootstrapPath" not in foundation),
    ("message schema statements are separated by an actual newline",
     "message_payload JSONB;\n    ALTER TABLE messages ADD COLUMN IF NOT EXISTS reaction TEXT;" in server
     and "message_payload JSONB;\\n    ALTER TABLE messages ADD COLUMN IF NOT EXISTS reaction TEXT;" not in server),
    ("startup chain executes foundation and notification bootstraps",
     "phase1FoundationBootstrap.js" in package_json and "notificationBootstrap.js" in package_json),
]

for name, passed in checks:
    print(f"{'PASS' if passed else 'FAIL'}: {name}")

failed = [name for name, passed in checks if not passed]
if failed:
    raise SystemExit(f"Social post bootstrap regression gate RED: {len(failed)} failure(s)")
print(f"Social post bootstrap regression gate GREEN ({len(checks)} checks)")
