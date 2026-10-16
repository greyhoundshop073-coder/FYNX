# FYNX Reference-to-Implementation Standard

**Status:** Project-wide required engineering standard  
**Applies to:** Every screen, component, feature, platform, branch, chat, and developer working on FYNX when a user supplies a reference image or asks to match/upgrade an interface.  
**Primary rule:** The supplied reference is the visual specification. The implementation must be real, functional, measurable, and verified against the actual running app.

## 1. Mandatory starting point: inspect before coding

Before editing:
1. Read this standard and the repository's `AGENTS.md` instructions.
2. Confirm the requested scope, target branch, ownership boundaries, and any explicit do-not-touch areas.
3. Inspect the reference image at full resolution. Do not rely on a thumbnail or memory.
4. Inspect the existing screen, its parent/navigation container, reusable components, state, theme, and tests.
5. Search for existing implementations before creating replacements.
6. Identify the cause of the current mismatch or defect before changing code.
7. Write down visual and functional acceptance criteria. Do not start with speculative padding tweaks.

Do not modify unrelated features or files. Do not take over another workstream's code. Never merge to a protected/default branch without authorization when approval is required.

## 2. Turn the reference into measurable requirements

Treat the image as a design artifact, not a vague inspiration.

Record:
- Image pixel width and height, and the actual app viewport boundaries.
- Major regions: system bars/insets, app header, content, panels/sheets, action bars, navigation.
- Important elements' approximate bounding boxes: x, y, width, height.
- Relative dimensions and normalized coordinates (x/W, y/H, width/W, height/H) where useful.
- Margins, gaps, alignment baselines, grouping, padding, corner radii, borders, typography, icon sizes, and colour relationships.
- Which elements are fixed, flexible, scrollable, anchored, overlapping intentionally, or conditional.
- The visible UI state represented by the image (e.g. selected, expanded, keyboard hidden, empty state).

Prefer actual design-file measurements, tokens, and annotations when available. For screenshot-only references, estimate carefully and label estimates as estimates. Do not claim pixel-perfect certainty when the screenshot does not expose implementation details.

Measure relationships as well as individual coordinates: equal margins, aligned edges, consistent row spacing, relative panel height, and distance between related controls. Use overlays or image-difference tools when useful.

## 3. Resolve ambiguity without inventing a design

A screenshot cannot always reveal implementation details such as whether a panel is a modal sheet or part of the main layout. Choose the implementation that preserves the reference and required interaction while fitting the existing architecture.

Ask a concise question only when a missing decision materially affects the result and cannot be reasonably inferred. Otherwise, state the assumption internally/in the work summary and proceed with a conservative, reference-faithful implementation.

Do not redesign, recolour, reorder, rename, or remove elements simply because another arrangement is easier. Follow explicit user instructions about which properties should or should not be copied from the image.

## 4. Plan the whole layout before making local changes

Before coding, map the screen's parent-child structure and how the regions share available space. Determine:
- Which component owns each region.
- Which content scrolls and which controls stay visible.
- How panels open and close.
- How keyboard and system insets affect available space.
- How state changes affect size, visibility, and focus.
- Whether parent navigation already supplies a header or toolbar.
- How the layout adapts to supported screen sizes and font scales.

Use the platform's appropriate layout primitives. In Jetpack Compose, for example, use suitable combinations of Row, Column, Box, weight, scrolling, window insets, and adaptive layout decisions. These are tools, not a recipe; select based on the intended composition.

Do not compensate for a structural problem by repeatedly changing arbitrary fixed padding. Fixed dimensions are acceptable when they represent a real design constraint, but the overall layout must respond correctly to available space.

Implement related regions as one coordinated composition. Check the entire screen, not just the element currently being edited.

## 5. Every visible control must be real

The image specifies what the user should see; it does not authorize fake functionality.

- Every control presented as interactive must perform its intended action.
- Switches must change the real setting they represent.
- Search fields must invoke actual search behaviour or honestly show that search is unavailable.
- Privacy, blocked-user, notification, and account controls must affect the corresponding real state.
- Publishing, payments, uploads, and other consequential actions must use the actual supported workflow.
- Do not fabricate users, server records, payment results, uploads, or success messages.
- Do not use static mock data as if it were a real user's data.
- Do not replace the actual app screen with a generated mockup when the task is to implement the feature.

If a backend, permission, or external service is unavailable, implement supported local behaviour and clear pending, unavailable, or error states. Never claim that a remote operation succeeded when it did not. A mockup is acceptable only when the task explicitly asks for a mockup/prototype, and it must not be represented as production functionality.

## 6. Match appearance and behaviour

### Visual requirements
Check position, dimensions, spacing, typography, icons, colour, contrast, borders, grouping, clipping, alignment, layering, and scroll behaviour against the reference.

### Functional requirements
Check actions, state transitions, persistence, navigation, focus, keyboard behaviour, permission handling, loading/empty/error/success states, and the expected outcome of every relevant control.

The two categories are independent: a beautiful screen can be broken, and a functional screen can be visually wrong. Both must pass.

## 7. Responsive and accessible implementation

The reference is the target for visual relationships, not permission to break other supported devices.

Test relevant device/window sizes, density, font scaling, keyboard-visible and keyboard-hidden states, system bars/insets, and orientation where supported. Keep controls visible and reachable; prevent clipped text and unintended overlaps. Use scroll containers when content can exceed available space.

Use meaningful accessibility labels/semantics, appropriate touch targets, keyboard/back behaviour, and stable test identifiers where practical. Do not rely exclusively on fragile screen-coordinate taps for automated tests.

## 8. Define acceptance criteria before implementation

Write a small checklist specific to the requested screen.

Visual examples:
- Header appears exactly once.
- Main regions have the reference's intended proportions.
- Margins, rows, baselines, and control groupings match.
- No essential content is unintentionally covered.
- No text or options are clipped.
- Selected, expanded, and closed states are composed correctly.

Functional examples:
- Every requested control works.
- Selected values persist when required.
- Back/close actions behave correctly.
- Scrolling and panel gestures work.
- Error and unavailable states are truthful.
- Existing flows remain intact.

Mark each requirement as pass/fail/not tested. Do not silently omit a difficult criterion.

## 9. Verify the real rendered app against the reference

After implementation:
1. Run relevant source checks, unit tests, UI tests, and build/lint tasks.
2. Launch the actual app in an emulator/device and navigate to the real screen.
3. Reproduce the same state as the supplied reference.
4. Capture a fresh runtime screenshot from the exact build/commit under review.
5. Compare it side-by-side with the reference. Use transparency overlays or difference images when useful.
6. Inspect the whole screen for position, proportions, clipping, overlap, and state-dependent layout errors.
7. Interact with the controls and verify their actual outcomes.
8. Test relevant device sizes and adjacent navigation flows.
9. Fix meaningful mismatches and repeat verification.

A generated concept image is not a runtime screenshot. A screenshot from an older commit is not proof of a newer change. If a fresh screenshot cannot be produced, say so explicitly and do not claim visual verification.

## 10. Automated tests and regression safety

Use automated checks to protect requirements, not merely to make CI green. Update tests when requirements legitimately change, but never weaken or delete a meaningful test just to pass.

Use screenshot tests for stable visual states where supported, UI tests for interaction/state transitions, and unit/integration tests for underlying logic. Keep assertions tied to user-visible requirements and real behaviour.

Before editing, identify existing behaviour that must be preserved. After editing, run relevant regression checks and inspect the diff for accidental or unrelated changes. A green build does not prove that the reference was matched.

## 11. Git and collaboration rules

- Confirm repository, branch, ownership, and scope before committing.
- Keep the change focused and review the diff.
- Do not push unrelated fixes as part of a reference task.
- Do not merge without the required approval.
- Preserve work owned by other chats/developers.
- Report the commit SHA and CI result accurately.
- Attach or link fresh screenshots from the exact verified commit.
- Report visual, functional, and build status separately.
- If a check is red, investigate the actual failing cause and fix it before continuing.

## 12. Completion gate: no unsupported claims

A task is **not complete** merely because code was written, pushed, or compiled.

Report these independently:
- **Code review:** relevant implementation inspected.
- **Automated checks:** which checks passed or failed.
- **Build:** whether the exact change compiled.
- **Runtime/functionality:** which interactions were exercised.
- **Visual match:** whether a fresh runtime screenshot was compared with the reference.
- **Regression:** which existing flows were tested.
- **Remaining issues:** every known defect or unverified requirement.

Do not say “everything works” if only a build ran. Do not call the visual work finished while the screenshot still has material differences. If a requirement is not tested, label it not tested.

## 13. Required checklist

### Before coding
- [ ] Read this standard and repository instructions.
- [ ] Inspect the full reference and measure key regions/relationships.
- [ ] Inspect current implementation, parents, reusable components, and ownership.
- [ ] Identify root causes and preserve existing behaviour.
- [ ] Define visual and functional acceptance criteria.

### Before completion
- [ ] Implemented the whole layout coherently; no repeated guesswork adjustments.
- [ ] Real functionality used; no fake records or false success states.
- [ ] Relevant build, tests, lint, and regression checks run.
- [ ] Actual app screen captured from the exact build/commit.
- [ ] Screenshot compared against reference; material mismatches fixed.
- [ ] Important controls and screen states tested.
- [ ] Relevant adaptive/accessibility checks completed.
- [ ] Commit, CI status, screenshot evidence, and remaining issues reported honestly.

Any essential unchecked item means the task must not be described as complete.

## 14. Further reading

- Android screenshot testing: https://developer.android.com/training/testing/ui-tests/screenshot
- Compose UI testing: https://developer.android.com/develop/ui/compose/testing
- Compose semantics: https://developer.android.com/develop/ui/compose/testing/semantics
- Adaptive Compose layouts: https://developer.android.com/develop/ui/compose/build-adaptive-apps
- Figma Dev Mode: https://help.figma.com/hc/en-us/articles/15023124644247-Guide-to-Dev-Mode-in-Figma
- Figma design inspection: https://help.figma.com/hc/en-us/articles/22012921621015-Guide-to-inspecting
