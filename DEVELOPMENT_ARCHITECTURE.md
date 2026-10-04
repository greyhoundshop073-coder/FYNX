# FYNX Development Architecture Rules

## Large-file rule
Do not keep adding unrelated features to a large UI/controller file. If a file becomes difficult to safely review or is already very large, create a focused module/component for the new responsibility instead of making the large file larger.

## Safe extraction
- Preserve existing behavior before improving it.
- Extract presentation/UI first when possible; keep business logic, realtime listeners, persistence, upload, and playback systems unchanged unless the task specifically requires them.
- Give each extracted component a clear responsibility and stable public API.
- Prefer small, reversible commits.
- Group related extractions into one milestone when they can be moved safely together.
- Use a dedicated branch for larger refactors; keep `main` stable until the branch is verified.
- Never replace a large file wholesale when a surgical change or extraction is safer.
- Before modifying a shared component, trace its callers/usages so another feature does not break.

## Chat example
Chat presentation should live in focused components such as media, voice, video note, reactions, replies, bubbles, and composer/attachment UI rather than continually expanding `ConversationPanel.kt`.

## Cross-feature rule
The same approach applies to every large file in FYNX, not only Chat. If another feature encounters a large or heavily shared file, isolate the new responsibility in an appropriate component/module and leave the existing stable behavior intact.
