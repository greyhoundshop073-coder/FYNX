# FYNX Global Readiness Guide

This file is a coordination guide for every FYNX feature area. It does not require global infrastructure to be deployed now. Its purpose is to prevent feature work from creating country-, server-, locale-, or provider-specific assumptions that would make later global expansion expensive.

## Rule for every feature push

Before pushing a feature, inspect whether the feature creates or touches any of these:

- Country or region assumptions
- Language or hard-coded user-facing strings
- Locale/date/time/timezone assumptions
- Phone-number or country-code assumptions
- Currency/payment-provider assumptions
- User/account/data ownership assumptions
- Server-instance or in-memory state assumptions
- Realtime/WebSocket assumptions
- Media URL/storage/CDN assumptions
- Notification/deep-link assumptions
- Privacy, retention, deletion, export, or data-residency behavior
- Network/weak-connectivity behavior
- Rate limits or resource limits

If relevant, make the smallest safe architectural preparation in the same feature section. Do not build expensive multi-region infrastructure prematurely.

## Current strategy

1. Finish and stabilize the existing feature.
2. Preserve working implementations.
3. Remove global blockers when the relevant feature is already being changed.
4. Keep infrastructure inexpensive while FYNX is small.
5. Introduce multi-region/distributed infrastructure only when usage or reliability requirements justify it.
6. Every change must still follow the normal FYNX process: investigate callers/dependencies -> implement one consolidated section -> CI GREEN -> APK/runtime verification.

## Priority global foundations

- Region-independent account/data model
- Locale-ready UI and formatting
- Extensible currency/payment model
- Distributed-safe realtime design
- Server-independent background/state handling
- Durable database ownership/transactions/indexing
- Authenticated and scalable media delivery
- Global notification/retry behavior
- Central monitoring/observability
- Privacy/data lifecycle boundaries

## Important

This is a checklist, not permission to make unrelated global changes during another feature. A chat/group/profile/home/etc. worker should only apply global-readiness changes that are directly relevant to the code being touched, and should document any larger dependency for a later dedicated infrastructure section.
