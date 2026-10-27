# FYNX Master UI Guidelines

This document is the visual contract for all existing and future FYNX screens. It is based on the approved FYNX master reference image and must be followed without redesigning the application.

## Visual identity
- Primary background: dark navy / blue-black.
- Main surfaces: deep navy cards and raised navy surfaces.
- Primary action/accent: Azure Blue (`#007FFF`).
- Successful and confirmed states: Classic Green (`#008000`).
- Error and failed states: Crimson Red (`#DC143C`); dark-mode error text may use the brighter contrast-safe red token.
- Purple remains an optional user-selected accent, not the default brand color for controls.
- Use solid Azure Blue for primary actions and active states; do not introduce new blue-to-purple action gradients.
- Primary text: white / very light blue-white.
- Secondary text: muted cool grey-blue.
- Borders: subtle blue-grey, never heavy.
- Cards: rounded, clean, consistent corner radius.
- Controls: rounded/pill-like where appropriate.
- Icons: professional Material/outline icons; do not use emoji as feature icons.


## Supporting palette

These tones support the three official FYNX brand colors; they are not additional primary brand colors.

- Soft Cloud White (`#F5F7FA`): the light-mode page background. Keep surfaces clean and content contrast high.
- Midnight Slate (`#1C2434`): raised dark-mode surfaces and panels, used over the deeper navy background.
- Slate Grey (`#64748B`): secondary text and quiet icons in light mode; never use it for essential text on a dark surface.
- Pale Cobalt (`#E8EEFA`): gentle light-mode selection/focus containers. Use it for selected/highlighted cards; keep ordinary content cards neutral and primary actions solid Azure Blue.
- Premium Gold (`#D4AF37`): reserved for premium/verified/reward details only. Do not use it for success/error states, large backgrounds, or small text on its own; pair it with a readable dark foreground.

Use semantic tokens from `FynxDesign` instead of repeating raw hex values. Preserve the existing deep-navy, Charcoal Black and Black AMOLED appearance options. Do not scatter every palette color across every screen.

## Layout language
- Keep screens uncluttered and mobile-first.
- Use consistent horizontal padding and vertical spacing.
- Use clear hierarchy: title -> primary information -> actions -> secondary information.
- Use cards for grouped information rather than dense blocks of text.
- Bottom navigation should remain compact and consistent with the master reference.
- Preserve existing FYNX navigation and working behaviour.

## Reference screens
The same visual language applies to:
- Welcome / Sign In
- Home Feed
- Chat list
- Private conversation
- Groups
- Profile
- Privacy
- Stories
- Status
- Marketplace
- Money Tools
- Settings
- Seller Center
- Seller shipping settings
- Buyer order/tracking
- Inspection
- Disputes
- Returns
- Admin/moderation
- Notifications
- Camera/media
- Calls
- AI tools

## Profile reference
Profile should visually follow the approved reference arrangement:
1. FYNX title/header area.
2. Profile photo/avatar.
3. Real Posts / Followers / Following counts.
4. Display name and username.
5. Bio/about information.
6. Primary Edit Profile action.
7. Privacy/settings information below.
8. Real privacy controls remain functional; visual changes must not weaken access control.

Followers/following data must remain server-authoritative and privacy-controlled. Profile photo must not be mandatory before posting.

## Marketplace future screens
Seller shipping settings, buyer tracking, inspection, disputes, returns and admin screens must look like native FYNX screens. They must reuse the same background, surfaces, blue/purple accents, cards, typography, spacing and professional icons. Do not create a separate marketplace visual theme.

## Implementation rules
- Reuse `FynxDesign` and the existing Material 3 theme wherever possible.
- Do not create duplicate design systems.
- Do not replace working backend/API/state logic merely to change appearance.
- Do not fabricate data for visual demonstrations.
- Every visual change must preserve real loading, error, empty, retry and permission states.
- Build and verify after grouped changes; RED means investigate and fix before continuing.
- Future screens must be checked against this document before being considered visually complete.
