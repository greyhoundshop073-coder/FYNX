# FYNX Master UI Specification

This document is the visual contract for the existing FYNX Android app. It supplements the existing design system; it does not replace working UI or create duplicate screens.

## Master visual reference

Use the approved FYNX master reference image as the visual source of truth for layout, colour, spacing, hierarchy and screen composition.

## Core visual language

- Primary background: deep navy / blue-black.
- Primary accent: FYNX blue.
- Secondary accent: purple/violet.
- Important actions may use a blue-to-purple visual treatment when it matches the reference.
- Surfaces/cards: slightly raised dark navy surfaces with subtle separation.
- Primary text: white / near-white.
- Secondary text: muted light grey-blue.
- Controls: rounded, clean, compact and professional.
- Icons: professional Material/outline icons; do not use emoji as feature icons.
- Images and media: rounded corners and consistent spacing.
- Navigation: compact, clear and consistent with the existing FYNX navigation.
- Light mode must remain a genuine light theme; do not force the dark palette into light mode.
- Black AMOLED remains available where already supported.

## Welcome / Sign In

Match the approved reference composition:

- centred FYNX branding
- dark navy/blue/purple background
- clear FYNX identity
- Create Account as the prominent filled action
- Sign In as the clean outlined action
- generous vertical spacing
- no unnecessary cards or decorative clutter
- preserve the existing real authentication flow

## Home / Feed

- dark navy page surface
- compact FYNX header
- story circles at the top
- clean post composer
- readable post cards
- professional media treatment
- consistent bottom navigation
- preserve existing real post/backend behaviour

## Chats / Conversation

- dark navy chat surface
- compact conversation header
- profile photo, name and online state
- call/video/more actions aligned cleanly
- incoming and outgoing bubbles clearly differentiated
- blue accent for outgoing/primary actions
- rounded media cards
- clean typing indicator
- composer anchored naturally above the keyboard
- professional attachment/voice/camera icons
- preserve existing realtime, attachment, voice, search and call functionality

## Profile / Privacy

Match the reference profile composition rather than the current generic stacked-card layout:

- clean top profile header
- profile photo/avatar prominently positioned
- real Posts / Followers / Following counts in one horizontal row
- display name and username beneath/alongside the profile identity as appropriate for the reference
- concise bio/about information
- prominent rounded Edit Profile action
- privacy section beneath the profile header
- privacy rows use clear labels, secondary descriptions and chevrons
- settings/privacy controls should feel like one coherent screen, not unrelated cards
- followers/following lists remain protected by the existing privacy rules
- profile photo remains optional; do not require one before posting
- preserve the existing real profile API, counts, editing, photo upload and privacy behaviour

## Stories / Status

- same dark navy foundation
- circular story avatars with consistent rings
- media cards use the same radius and spacing language
- Photo / Video / Text actions use professional icons and the FYNX accent system
- preserve real upload, visibility and expiry behaviour

## Marketplace

- same FYNX dark navy surface/card language
- product media and price hierarchy should be obvious
- search/filter controls use existing rounded controls
- purchase actions use the FYNX primary accent
- protected checkout must remain server-authoritative

## Seller Shipping Settings

When implemented, the screen must look native to FYNX:

- same dark navy background
- same blue/purple accents
- same card/control radius
- simple grouped sections
- professional delivery/shipping icons
- clear method, fee, coverage and note controls
- no new visual system

## Buyer Tracking / Fulfilment

- order header and product identity first
- clear current shipment state
- timeline uses consistent FYNX accent colours and typography
- tracking/reference information in a clean card
- delivery/pickup distinction must be obvious
- preserve the carrier-neutral backend architecture

## Inspection / Disputes / Returns

- calm, trustworthy presentation
- clear protected-payment state
- evidence upload and status controls use the same FYNX controls
- dispute actions must never look like a simple automatic refund button
- buyer and seller evidence should be visually distinguishable
- preserve server-authoritative dispute/payment logic

## Admin / Safety

- same FYNX visual language
- professional icons and compact cards
- clear severity/state indicators
- audit/evidence information readable without clutter
- preserve authorization and server-side enforcement

## Money Tools / Settings

Do not redesign existing functionality. Bring visual details into alignment with this master language where needed:

- consistent surfaces
- consistent typography
- consistent icon sizing
- consistent spacing
- consistent controls
- consistent privacy/settings row structure

## Implementation rule

Before changing a screen:

1. Inspect the existing implementation.
2. Preserve working behaviour and backend connections.
3. Reuse the existing FYNX design system whenever possible.
4. Do not create a duplicate screen/system.
5. Change presentation only where required to match the master reference.
6. Build and verify Android CI.
7. RED means investigate and fix before moving forward.
8. GREEN means the change can be considered for the next grouped UI batch.

## One-pass visual audit checklist

Audit every major screen before claiming the visual pass complete:

- Welcome / Sign In
- Register / verification
- Home / Feed
- Chats list
- Private conversation
- Group conversation
- Friends
- Stories
- Status composer
- Profile
- Privacy
- Settings
- Notifications
- Marketplace browse
- Product details
- Checkout
- Protected order
- Seller Center
- Seller Shipping Settings
- Buyer tracking
- Inspection
- Dispute
- Return/refund states
- Money Tools
- AI/Extra Tools
- Camera/media screens
- Call screens
- Admin/safety screens

The audit is intended to find inconsistent colours, spacing, typography, card shapes, icons, navigation, loading/empty/error states and accidental redesigns before those screens are individually declared complete.
