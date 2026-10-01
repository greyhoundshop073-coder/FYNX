# Home Feed Interaction Research — 2026-09-16

## Positioning decision

FYNX Home keeps its existing professional visual language. The feed action row should remain compact like a mature social feed while preserving a reliable touch target.

## Interaction sizing

Android's current accessibility guidance recommends a minimum 48dp x 48dp touch target for interactive elements. Material 3 documents a typical 24dp internal icon inside a 48dp minimum icon-button target.

FYNX therefore uses 48dp as the interaction floor and 24dp as the target icon size rather than claiming an unpublished exact Facebook dp value.

## Existing FYNX behavior to preserve

- Like, Comment and Share remain real feed actions.
- Save and Repost remain backed by the existing social interaction APIs.
- Video remains width-responsive using its actual media aspect ratio.
- Feed privacy, blocking, ownership and rollback/reconciliation remain backend-controlled.
- No decorative or fake reaction counts are introduced.
- No duplicate sharing system is introduced.

## Next implementation boundary

Persistent emoji reactions require a real backend model and API before the UI can expose them. The implementation must support one current reaction per user/post, change/remove behavior, aggregate counts, current-user state, authorization/privacy checks and rollback/reconciliation. Until those backend pieces are verified, the existing Like path remains authoritative.
