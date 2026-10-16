# Instructions for coding agents working in FYNX

**Read this first for every UI/design task.**

When a user supplies a screenshot/reference image or asks to make any FYNX screen match an image, you MUST read and follow the project-wide **[Reference-to-Implementation Engineering Standard](REFERENCE_TO_IMPLEMENTATION_STANDARD.md)** before editing code.

Required workflow:
1. Inspect the full reference and existing screen/parent components.
2. Measure important positions, proportions, spacing, and relationships; separate facts from estimates.
3. Define visual and functional acceptance criteria before coding.
4. Plan and implement the complete layout coherently; do not repeatedly guess at padding.
5. Make every displayed control real and truthful. Never fabricate data or success states.
6. Build/test the actual app, capture a fresh runtime screenshot from the exact commit, and compare it with the supplied reference.
7. Test interactions and relevant screen states, run regressions, and fix material defects before declaring completion.
8. Report build, functionality, visual match, and remaining issues separately. A green build alone is not proof of completion.

Do not skip the standard because the task is small or because the work is being performed in a separate chat. Preserve repository ownership and user constraints, avoid unrelated changes, and never merge without required authorization.
