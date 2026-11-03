#!/usr/bin/env python3
"""Regression guard for professional Home feed video and nested conversations."""
from pathlib import Path
R=Path(__file__).resolve().parents[1]
H=(R/"app/src/main/java/com/fynx/app/ui/FynxRemoteHomeSocialPanel.kt").read_text()
C=(R/"app/src/main/java/com/fynx/app/ui/FynxHomeCommentsPanel.kt").read_text()
B=(R/"backend/realtimeIsolationBootstrap.js").read_text()
W=(R/".github/workflows/android-build.yml").read_text()
checks={
"inline video height cap":"heightIn(max = 420.dp).aspectRatio(videoAspectRatio)" in H,
"post context":"post.authorDisplayName.ifBlank" in C,
"reply-to-reply target":"expandedReplies.values.flatten()" in C,
"nested reply UI":"expandedReplies[reply.id].orEmpty().forEach" in C,
"reply stores exact parent":"const directParentId = parentId;" in B and "[postId, req.user.sub, directParentId, text]" in B,
"pagination cursor avoids skipped row":"String(result.rows[limit - 1].id)" in B,
"CI runs source guard":"python3 scripts/verify_home_feed_professional.py" in W,
"CI runs emulator certification":"feat/home-feed-professional-feed-conversations-20261103" in W}
for k,ok in checks.items(): print(("PASS " if ok else "FAIL ")+k)
bad=[k for k,ok in checks.items() if not ok]
if bad: raise SystemExit("HOME FEED PROFESSIONAL RED: "+", ".join(bad))
print("HOME FEED PROFESSIONAL GREEN")
