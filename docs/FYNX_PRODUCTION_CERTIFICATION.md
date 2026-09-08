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
