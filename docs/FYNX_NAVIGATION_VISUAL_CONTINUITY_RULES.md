# FYNX — Navigation, Motion & Visual Continuity Rules

Repository: greyhoundshop073-coder/FYNX
Branch: main

## Purpose

FYNX must feel easy to move through. A user should understand where they are, how they got there, how to return, and what will happen when they tap or swipe. Navigation and visual consistency are part of functionality, not optional polish.

## Locked rules

- Every secondary/detail/full-screen destination must have a clear way back to its logical previous destination. Use Android system back/gesture correctly and provide an in-app back control where the screen pattern requires a visible back affordance.
- Back must be predictable: return to the previous logical destination/state; do not unexpectedly jump to Home, close the wrong layer, loop, or discard user work.
- Support Android predictive back and preserve navigation state where applicable.
- Navigation between major FYNX areas must feel smooth and deliberate. Avoid unnecessary reloads, abrupt transitions, duplicate destinations, dead ends, excessive taps, or confusing nested flows.
- Audit every route for ENTRY -> DESTINATION -> BACK -> RETURN STATE. A route without a sensible return path is incomplete.
- Check keyboard, system bars, gesture insets, bottom navigation, floating buttons, dialogs, sheets and overlays for collisions or hidden controls.
- Check every clickable control for overlap, clipping, unreachable placement, incorrect z-order, insufficient touch area, or another control intercepting its touch.
- Detect visual anomalies across the APK: unexpected colors, hardcoded colors bypassing the FYNX theme, inconsistent backgrounds, incorrect text/icon colors, broken dark/light mode, accent-color leaks, mismatched cards, borders, shadows, or controls that visually belong to another screen.
- A different color is not automatically wrong: compare it with the intended FYNX theme role for that surface and record whether it is intentional or accidental/hardcoded.
- Check light mode, dark mode and accent-color variants. A fix is incomplete if a theme-driven component works in only one theme state.
- Check loading, empty, error, retry, success and returning states so the screen does not become visually or functionally stranded.
- Check scrolling and fixed controls so content is not covered by buttons, navigation bars, keyboard, camera controls, media controls or system UI.
- Keep familiar mobile navigation patterns where useful while preserving FYNX's existing visual language. Do not redesign FYNX from scratch.

## Smooth-navigation audit matrix

For every major and secondary surface record:

1. Entry point
2. Destination
3. Visible back control / system back behavior
4. Swipe-back behavior where applicable
5. Previous-state restoration
6. Transition/loading behavior
7. Keyboard/inset behavior
8. Overlay/modal dismissal behavior
9. Bottom navigation behavior
10. Touch-target and overlap check
11. Theme/background/color check
12. Light/dark/accent variants
13. Error/retry path
14. Exit/return destination

## Permanent examples to inspect

- Camera controls or buttons covering the camera preview.
- Home comment area appearing present but not allowing the expected interaction.
- Status controls that exist but are poorly positioned, hidden, clipped or difficult to use.
- Marketplace screens whose controls, colors, media, checkout/order states or back paths are inconsistent.
- Any screen where the user can enter a child screen but cannot clearly get back.
- Any screen where a button works only because of an accidental large clickable area or another element steals the touch.
- Any screen whose background/accent/text/icon color unexpectedly differs from the active FYNX theme.

## Acceptance rule

A navigation or visual-continuity issue remains YELLOW/RED until the actual APK behavior is corrected and the relevant build/verification/user journey passes. Do not mark it fixed because a route or color resource merely exists in source code.

The final FYNX continuity pass must verify not only whether features work, but whether moving through FYNX feels smooth, predictable, visually consistent and recoverable from every major destination.
