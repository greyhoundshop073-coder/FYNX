# FYNX Future Development Plan — Nigeria-Focused Features

**Status:** PLANNING ONLY — NOT APPROVED FOR IMPLEMENTATION YET  
**Canonical branch reviewed:** `integration/fynx-single-apk-20261013`  
**Purpose:** Preserve these ideas in the repository so future development can resume from this document without relying on a previous ChatGPT conversation.

## Important instruction to future contributors

FYNX is already under active development. **Do not interrupt, replace, or divert current assigned work to implement this plan.** Do not create feature branches, commits, pull requests, migrations, screens, or backend changes for these ideas until the project owner explicitly says the current work is ready and authorizes a specific feature.

When authorized:
- Reinspect the current canonical branch, open pull requests, CI status, and existing implementations first.
- Reuse existing screens, services, navigation, data models, and design components wherever possible.
- Do not create duplicate tabs or duplicate features.
- Preserve all existing Marketplace, My Shop, Home/Feed, Status, Chat/Groups, Wallet, profile, privacy, and admin functionality.
- Implement one approved feature at a time, with focused tests, security review, full relevant CI, and evidence before reporting completion.
- Treat provider-dependent or backend-dependent functionality as incomplete until its real integration and end-to-end behaviour are verified.

## Existing foundations observed during planning

The following were found in source files on the reviewed branch. Recheck them before implementation because the branch may change.

- Main navigation already includes **Home, Chats, Friends, Marketplace, and More/Features**. Avoid adding several new primary navigation destinations.
- Social discovery already includes backend-backed post search and feed interactions. The social backend also has friend relationships and request handling.
- Groups already have backend-backed membership, messages, member permissions, invitations/join-request foundations, and moderation-related controls.
- Business accounts already support business information, services/prices, opening hours, service areas, product links, and advertising-related surfaces.
- Marketplace already covers a broad range of Nigerian-relevant categories and has location/discovery and seller/buyer workflows.
- Calendar currently behaves as a **personal, locally stored calendar with reminders**. It is not a public event discovery, ticketing, or registration platform.
- Wallet and transaction foundations exist, but this review did not verify an airtime/data provider integration or a complete airtime/data purchase flow.

These observations are not a claim that every related feature is fully production-certified. Verify actual client, backend, provider, tests, and runtime behaviour before relying on them.

## Proposed features and how they should fit FYNX

### 1. Nigerian Trends and improved Discover experience
**Suggested priority when work is authorized: First**

Build on the existing Home/social discovery and post-search foundations rather than creating a separate social network or another main tab.

Possible scope:
- Nigerian trending topics and hashtags.
- Discoverable conversations around entertainment, music, culture, lifestyle, technology, education, business, and community topics.
- Football conversations and other time-relevant public discussions.
- Useful filters such as topic and, where privacy and data quality permit, location.
- Clear controls for reporting, blocking, muting, and hiding topics or content.

Requirements:
- Ranking must be transparent enough to test and resistant to spam, coordinated manipulation, and engagement farming.
- Respect audience visibility, blocks, privacy settings, moderation rules, and deleted content.
- Do not imply live news or live scores unless a verified data source is integrated.

### 2. Jobs and Gigs
**Suggested priority when work is authorized: Second**

Fit this into Business accounts, Marketplace/service discovery, profiles, and existing chat rather than adding a new main tab.

Possible scope:
- Employers and verified businesses publish vacancies and short-term gigs.
- Listings include role, location/remote status, pay range where supplied, requirements, closing date, and contact/application method.
- Applicants apply using a controlled profile/application flow.
- Employers manage applications and update application status.
- Optional paid promotion only after clear pricing, consent, and billing rules are established.

Safety requirements:
- Employer identity and business verification options.
- Report/block controls and moderation queues.
- Scam warnings and restrictions on requests for application fees, passwords, OTPs, or sensitive financial information.
- Do not claim a job or employer is verified unless the verification process actually supports that claim.

### 3. Public Events and Entertainment Discovery
**Suggested priority when work is authorized: Third**

Extend the existing personal Calendar, social posts, Status, Groups, and location discovery. Keep personal calendar entries private by default.

Possible scope:
- Organizers publish public events with date, venue/location, description, age/access details where relevant, and organizer contact.
- Users discover events, save them, share invitations, and optionally indicate attendance.
- Event pages can link to relevant Groups or organizer/business profiles.
- Ticket sales or paid registration should be a later, separately approved phase with payment, refund, fraud, and organizer verification requirements.

Important distinction: the current personal Calendar is not itself a public event directory. Public events need a separate shared event data model and backend workflow.

### 4. Football and Sports Communities
**Suggested priority when work is authorized: Build as part of Discover and Groups**

Use topic feeds and Groups for club supporters, local teams, match conversations, and fan communities.

Possible scope:
- Follow clubs, leagues, and sports topics.
- Match discussion spaces and community posts.
- Share reactions, predictions, and post-match conversations.
- Add fixtures, scores, tables, and live updates only through a reliable licensed/approved sports data source. Do not fabricate or manually imply real-time accuracy.

### 5. Student and Campus Communities
**Suggested priority when work is authorized: Build as part of Groups and Discover**

Use existing Groups, profiles, posts, and messaging.

Possible scope:
- Campus/school-based public or private communities.
- Student discussions, announcements, study groups, accommodation notices, and opportunity sharing.
- Optional school affiliation claims must be clearly distinguished from verified student status.
- Provide reporting, moderation, and controls against harassment, impersonation, and fraudulent accommodation or fee requests.

### 6. Local Deals
**Suggested priority when work is authorized: Extend Marketplace and Business accounts**

Do not create another marketplace. Explore a promotion/deal format within existing Marketplace and business discovery.

Possible scope:
- Time-limited seller offers and local promotions.
- Clear original/current price and offer expiry where applicable.
- Link offers to the seller's real storefront and existing product/service listing.
- Sponsored placements must be labelled and governed by transparent advertising rules.

### 7. Airtime and Data
**Suggested priority when work is authorized: Later, after provider and financial checks**

If approved, place this as a service inside Wallet/Money tools rather than creating another primary tab.

Possible scope:
- Supported Nigerian mobile networks, airtime denominations, and data bundles.
- Confirm recipient number, network, amount, and total before payment.
- Show processing, successful, failed, and reversed states accurately.
- Provide receipts and transaction history.

Non-negotiable requirements:
- Select and contract with a reliable provider; confirm supported networks, products, fees, settlement, refunds, and service-level expectations.
- Server-side provider credentials only; never embed secret keys in the Android app.
- Use idempotency, transaction reconciliation, audit trails, and safe retry handling to prevent duplicate debits or purchases.
- Verify provider callbacks and transaction status server-side.
- Test failures, timeouts, reversals, duplicate requests, and reconciliation before release.
- Do not advertise this feature as available until a real end-to-end provider integration is verified.

## Recommended delivery sequence — only after explicit authorization

1. **Nigerian Trends / Discover:** smallest fit with existing social foundations; validate privacy, ranking, moderation, and performance.
2. **Jobs & Gigs:** define listing, application, employer, moderation, and scam-protection workflows.
3. **Public Events:** create public event discovery separately from personal calendar entries.
4. **Football and Student Communities:** introduce topic/campus experiences through existing Discover and Groups.
5. **Local Deals:** extend existing Marketplace/Business promotion features.
6. **Airtime & Data:** proceed only after provider, financial controls, and backend readiness are confirmed.

This is a suggested sequence, not a commitment or instruction to start work automatically. The project owner may change priorities.

## Design and product principles

- Keep FYNX's existing navigation familiar and uncluttered.
- Follow the established FYNX visual system: Azure Blue primary (`#007FFF`), pale blue action surfaces, and existing semantic success/error colours.
- Make features useful on Nigerian mobile networks and lower-cost Android devices; minimize unnecessary background requests, media downloads, and memory use.
- Support relevant Nigerian locations and local context without exposing a user's precise location without consent.
- Make prices and transactions clear; support the currencies already used by FYNX where relevant. Do not regress existing NGN/USD Marketplace behaviour.
- Build moderation, reporting, privacy, accessibility, and performance into each feature from the beginning.

## Approval checklist before future implementation

- [ ] Project owner confirms current active development work is ready for this roadmap.
- [ ] Project owner selects one feature and approves its scope.
- [ ] Inspect canonical branch, open PRs, CI, existing code, and backend/provider state.
- [ ] Document what already exists and identify only the missing pieces.
- [ ] Agree on acceptance criteria and tests before coding.
- [ ] Implement without overwriting unrelated work.
- [ ] Run targeted tests and full relevant CI; investigate and fix failures before pushing.
- [ ] Verify real app behaviour and backend/provider flows where applicable.
- [ ] Report exact commit/PR, test results, limitations, and evidence.

---
**Planning note:** This document records future ideas only. It does not authorize implementation, deployment, payment-provider activation, or changes to the current FYNX development priorities.
