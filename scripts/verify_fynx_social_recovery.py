from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]

def read(path):
    return (ROOT / path).read_text(encoding="utf-8")

app = read("app/src/main/java/com/fynx/app/ui/FynxApp.kt")
home = read("app/src/main/java/com/fynx/app/ui/FynxRemoteHomeSocialPanel.kt")
updates = read("app/src/main/java/com/fynx/app/ui/FynxVisibleUpdatesPanel.kt")
status = read("app/src/main/java/com/fynx/app/ui/FynxStatusTimelinePanel.kt")
profile = read("app/src/main/java/com/fynx/app/ui/FynxProfileContent.kt")
social = read("app/src/main/java/com/fynx/app/ui/FynxRemoteSocialClient.kt")
audience = read("app/src/main/java/com/fynx/app/ui/FynxPostAudienceEditor.kt")
caption = read("app/src/main/java/com/fynx/app/ui/FynxExpandableCaption.kt")
connection = read("app/src/main/java/com/fynx/app/ui/FynxAppConnectionManager.kt")
backend = read("backend/socialRoutes.js")
workflow = read(".github/workflows/android-build.yml")

checks = []
def check(name, ok):
    checks.append((name, bool(ok)))

check("post deep-link is one-shot when leaving Home", 'if (selected != "Home")' in app and 'postOpenId = null' in app and 'postOpenCommentId = null' in app)
check("post/comment target is consumed only after the target post exists", 'LaunchedEffect(posts, initialPostId, initialCommentId)' in home and 'posts.firstOrNull { it.id == target }' in home and 'onInitialPostConsumed()' in home)
check("comment deep-link reaches the comments panel", 'FynxHomeCommentsPanel(post = post, initialCommentId = initialCommentId' in home)
check("Home feed owns bottom system inset", 'LazyColumn(state = feedListState' in home and '.navigationBarsPadding()' in home)
check("floating Home navigation owns system bottom inset", '.navigationBarsPadding()' in app and 'NavigationBar(' in app and 'windowInsets = androidx.compose.foundation.layout.WindowInsets(0, 0, 0, 0)' in app)
check("client delete uses authenticated social post route", 'suspend fun deletePost' in social and 'FynxBackendClient.delete(context, "/api/social/posts/$numericId")' in social)
check("server delete is owner-authorized", 'app.delete(\'/api/social/posts/:id\',auth' in backend and 'DELETE FROM social_posts WHERE id=$1 AND author_id=$2' in backend)
check("audience editor exposes all four supported audiences", 'FynxPostVisibility.PUBLIC' in audience and 'FynxPostVisibility.FRIENDS_ONLY' in audience and 'FynxPostVisibility.SELECTED_PEOPLE' in audience and 'FynxPostVisibility.ONLY_ME' in audience)
check("audience update is owner-authorized and selected audience is friend-checked", "app.patch('/api/social/posts/:id',auth" in backend and 'WHERE id=$1 AND author_id=$2' in backend and "selected audience must contain your accepted friends" in backend)
check("Home status row opens the exact tapped owner", 'onOpenStatusOwner(status.ownerUsername.removePrefix("@").trim())' in updates)
check("Home post author status lookup filters by the tapped owner", 'filter { it.ownerUsername.equals(owner, true) }' in home and 'sortedBy { it.createdAtMillis }' in home)
check("status open commands wait for initial load, then are consumed", 'LaunchedEffect(statuses, openOwnerUsername, openStatusId, loading)' in status and 'if (loading) return@LaunchedEffect' in status and 'onOpenCommandConsumed()' in status)
check("active status ring is green on Home and Status surfaces", 'Color(0xFF22C55E)' in home and 'Color(0xFF22C55E)' in updates and 'Color(0xFF22C55E)' in status)
check("all production feed/profile captions use the shared expandable component", 'FynxExpandableCaption' in home and 'FynxExpandableCaption' in profile and 'TextOverflow.Ellipsis' in caption and 'TextButton' in caption and '"More"' in caption)
check("only the current user's audience icon is interactive", 'if (post.authorUsername.equals(currentUsername.removePrefix("@"), true)) IconButton' in home)
check("connection manager distinguishes network, connecting and connected", 'WAITING_FOR_NETWORK' in connection and 'CONNECTING' in connection and 'CONNECTED' in connection)
check("connected state requires successful FYNX backend health", 'FynxBackendClient.health(appContext).isSuccess' in connection and '_state.value = State.CONNECTED' in connection)
check("header text matches the required three states", '"Waiting for network..."' in app and '"Connecting" + ".".repeat(connectingDotCount)' in app and 'Text("FYNX"' in app and 'Icons.Default.Verified' in app)

check("main push has the full static/unit/lint Android build", 'on:\n  push:' in workflow and './gradlew testDebugUnitTest lintDebug assembleDebug --no-daemon' in workflow)
# The workflow intentionally uses a commit-named file and commit-named artifact.
# Keep this gate aligned with the live android-build.yml contract.
check("main push publishes an exact-commit APK artifact", 'name: FYNX-debug-apk-${{ github.sha }}' in workflow and 'path: FYNX-debug-${{ github.sha }}.apk' in workflow and 'GITHUB_SHA' in workflow and 'if-no-files-found: error' in workflow and 'uses: actions/upload-artifact@v4' in workflow)
check("authenticated runtime certification is available as the explicit full-runtime path", 'verify_authenticated_runtime_navigation.py' in workflow and 'FYNX_E2E_USERNAME' in workflow and 'FYNX_E2E_PASSWORD' in workflow and 'connectedDebugAndroidTest' in workflow and "github.event_name == 'workflow_dispatch' && inputs.full_runtime == 'true'" in workflow and 'Upload exact-commit debug APK' in workflow)

failed = [name for name, ok in checks if not ok]
for name, ok in checks:
    print(("PASS: " if ok else "FAIL: ") + name)
if failed:
    raise SystemExit("FYNX social recovery regression gate failed: " + "; ".join(failed))
print(f"FYNX social recovery regression gate passed ({len(checks)} checks)")
