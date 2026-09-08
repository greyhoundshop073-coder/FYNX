# FYNX Production Certification

## Scope

Final certification checklist for the existing FYNX Android social and marketplace application.

## Core journeys

- [ ] Authentication and authenticated app gate
- [ ] Home/feed navigation
- [ ] Profiles and private connection statistics
- [ ] Friends and follow flows
- [ ] Chat and media messaging
- [ ] Voice/video calling and permission recovery
- [ ] Stories
- [ ] Groups
- [ ] Marketplace and protected transaction flow
- [ ] Notifications
- [ ] Money tools
- [ ] Privacy and safety controls
- [ ] Owner/admin controls
- [ ] Sharing and deep links

## Camera and media placement

- [ ] Camera capture opens as the intended full-screen FYNX surface.
- [ ] Camera preview fills the available capture area without unintended clipping.
- [ ] Camera controls remain positioned in the bottom safe area and respect navigation bars.
- [ ] Front/back camera, flash, zoom, exposure, photo/video mode, capture, retake, rotate and use controls are present and reachable.
- [ ] Captured photo/video returns to the post composer correctly.
- [ ] Group and chat camera entry points use the same existing camera capture surface.

## Deep-link contract

- [ ] `fynx://` routes remain supported.
- [ ] `https://fynx.app` routes resolve to the matching FYNX destination.
- [ ] Profile and chat routes resolve using real account identifiers.
- [ ] Group routes preserve the real group identifier.
- [ ] Marketplace routes preserve the real listing identifier when supplied.
- [ ] Unknown external hosts are rejected.
- [ ] Signed-out and signed-in navigation is verified.
- [ ] Back navigation returns to the previous FYNX surface without duplicate stacks.

## Release gate

A production build is considered certified only after lint, unit tests, Android tests, and debug assembly complete successfully and the journey audit reports no failures.
