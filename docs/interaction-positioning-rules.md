# FYNX interaction-positioning rules

This document is the durable interaction research reference for FYNX. It records patterns from established social products so future implementation work does not repeatedly rediscover the same positioning decisions. These are interaction patterns, not branding or visual-copy instructions.

## Research anchors

- WhatsApp Status: Status is a dedicated temporary-sharing surface. Creation supports text, photo, video, and voice, with editing/preview and audience selection before sharing. Published Status is opened from the Status area, and viewers can reply/react; owners can inspect views/likes. Status is designed as a separate sharing flow rather than a normal feed post. Source: official WhatsApp Help Center, `How to create and share a status` and `How to view, like, and reply to status`.
- Facebook creation: use a creation entry point/menu to distinguish creation destinations instead of making one generic camera action responsible for unrelated content types. For FYNX, the confirmed product decision is that Home Create contains exactly Post, Status, and Marketplace.
- WhatsApp chat: conversation is the primary surface; composer stays attached to the conversation and media/actions are secondary to message composition.
- Facebook-style feed/profile pattern: the feed is for normal social posts, while a person's profile is the identity surface that organizes that person's own content and relationship information.
- Marketplace pattern: commercial listings remain a distinct content type/surface from ordinary personal posts. FYNX must use real seller-owned listing data and its protected transaction architecture.
- TikTok profile/content pattern: separate content/business surfaces can exist when the underlying account/content model genuinely supports them. FYNX must not create empty or fabricated Business tabs; Business is only shown when a real FYNX business-content dataset exists.

## FYNX surface map

### Home / Feed
Use established feed conventions: identity, feed content, lightweight actions, and a clear creation entry point. Do not move Status, Marketplace, Groups, or Money Tools into the Home Create menu merely because they are other app features.

### Home Create
The menu is an entry selector, not a direct camera shortcut. Exactly three destinations are currently approved:
1. Post
2. Status
3. Marketplace

Do not add Groups, Camera/Media, or Money Tools to this menu unless the product decision explicitly changes.

### Status
Follow the mature temporary-sharing pattern: Status area -> My Status/Add Status -> Text/Photo/Video/Voice -> full-screen preview/edit -> audience/privacy -> Share -> published Status viewer -> reply/reaction -> owner views/interactions. Keep the existing FYNX backend and mature composer; do not reintroduce the old immature flow or Google text-to-speech.

### Chat
Keep message composition primary. Typing, send, attachments, voice notes, and other actions belong around the conversation composer, not scattered across unrelated surfaces.

### Profile
Profile is identity plus the person's own authorized FYNX content. Normal Posts remain distinct from Marketplace listings. A Business tab is only valid when FYNX has genuine business identity/content data. Do not add Reposts or Likes tabs because another platform uses them.

Normal post content follows FYNX privacy rules. Private/friends-only content must not become public merely because someone visits a profile. Marketplace visibility is governed by marketplace listing state and seller/discovery rules, not by blindly applying personal-post visibility.

### Marketplace
Marketplace is a commercial surface with real listing data, seller identity, listing details, and protected transaction flow. Do not convert ordinary social posts into marketplace items or fabricate seller listings.

### Camera / Media
Media-first flows should use the established pattern: capture/select -> preview/edit -> caption or context -> explicit publish/send. Camera is a tool inside the surface that needs media, not a replacement for every creation flow.

### Notifications / Deep links
A notification or share link should land on the specific relevant destination (profile, chat, group, marketplace, Status, etc.) rather than only opening Home.

### Settings / Privacy
Keep privacy controls organized as a mature settings surface. Visibility decisions must be enforced by backend authorization, not only hidden in UI.

## Pre-push rule
Before every FYNX feature push:
1. Inspect the actual existing implementation and backend model.
2. Research the established interaction pattern for that exact surface.
3. Compare the intended FYNX placement/flow against the research.
4. Confirm the feature is a genuine FYNX content/data type before adding a new surface.
5. Implement only confirmed changes; preserve stronger existing code.
6. Update verification to test behavior/structure without brittle assumptions about helper declarations or formatting.
7. Build and verify before calling the result GREEN.

## Anti-regression rules
- Never count a function/composable declaration as a UI action when verifying action call sites.
- Never make a verifier depend on a variable/function name when the actual architecture has a semantically equivalent established implementation; inspect the implementation first.
- Never add a tab or menu item only because another social platform has one.
- Never fabricate users, posts, listings, business content, likes, followers, views, transactions, or statistics.
- Never put API/provider secrets in the APK.
- Never reintroduce removed Google text-to-speech behavior or AI image/video generation unless the product decision explicitly changes.

## Current confirmed FYNX positioning decisions
- Home Create: Post / Status / Marketplace only.
- Profile normal content: 4-column real-post grid with dedicated full-screen post viewer; the grid itself is not a swipe carousel.
- Profile Marketplace: shown only when genuine seller-owned listings exist.
- Profile Business: not shown until genuine business-content data exists.
- Profile Reposts/Likes tabs: not used.
- Status: mature full-screen Text / Photo / Video / Voice composer with audience selection and backend publishing.
