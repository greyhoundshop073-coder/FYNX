# FYNX Privacy Policy — Draft for Legal and Operational Review

**Status: DRAFT — NOT PUBLISHED, NOT A FINAL LEGAL NOTICE.**  
**Prepared:** 12 November 2026  
**App:** FYNX (Android package: `com.fynx.app`)  
**Operator/controller:** [LEGAL ENTITY AND REGISTERED ADDRESS REQUIRED]  
**Privacy contact:** [MONITORED PRIVACY EMAIL REQUIRED]  
**Effective date:** [SET ONLY AFTER APPROVAL AND PUBLICATION]

This draft is based on a source-level review recorded in `docs/PRIVACY_DATA_INVENTORY.md`. It must be reconciled with live production configuration, actual processors and contracts, legal operator identity, retention schedule and runtime testing before publication. Bracketed fields must not be left in a published version.

## 1. What FYNX is

FYNX is a social and commerce platform. Depending on the features a person uses and the services enabled for their account, it may provide profiles, contacts discovery, posts, comments, reactions, reposts, Status/Stories, private and group messaging, voice/video calls, camera and media sharing, AI assistance, business profiles, advertising tools, a multi-vendor marketplace, order and delivery coordination, buyer/seller support, and payment or wallet features where separately enabled and approved.

Not every feature or payment method is necessarily available to every user or in every country. A feature being described here does not mean that its provider, regulatory approval or production activation has been completed.

## 2. Information FYNX may process

Depending on the feature used, FYNX may process:

- **Account and profile information:** username, display name, phone number, email address, password hash, profile details, profile image, account settings, verification and session/security records.
- **Contacts matching:** if a user chooses the contacts feature and grants permission, the app reads contact names and phone numbers (including SIM-backed contacts where available). The reviewed matching request sends normalised phone numbers individually for matching; contact names are not included in that lookup request. This behavior and hosting access-log handling must be reverified before publication.
- **Content and communications:** posts, comments, replies, reactions, reposts, Status/Stories, messages, group content, media attachments, call/signalling metadata, report records and content visibility choices.
- **Camera, microphone and selected media:** photos, videos and audio a user chooses to capture, record, select, upload or send; technical metadata such as MIME type and file size; and temporary local previews/cache.
- **Location:** general place labels for features such as posts or marketplace discovery; precise coordinates may be processed when a user explicitly shares live/location information in a supported feature. Location access should occur only after the user invokes the relevant feature and grants permission.
- **Marketplace and business information:** seller/business profile details, listings, descriptions, prices, currency, inventory, product images, order status, delivery/pickup details, tracking and delivery evidence, reviews, ratings, disputes and support communications.
- **Payment and wallet information:** transaction identifiers, amounts, currencies, status, provider references, order/ledger records and payout or funding metadata when a payment feature is enabled. Payment card credentials are handled by the payment provider's hosted/SDK flow where applicable; FYNX must not store card security codes.
- **AI inputs and outputs:** prompts, conversation text, bounded conversation context and user-selected image attachments sent to an AI provider to fulfil the requested feature. Users may still type personal information into a prompt.
- **Device and technical information:** push notification tokens, app/device and operating-system information made available to the app or service, network/security logs, timestamps, diagnostics and feature interaction events.
- **Support, safety and compliance information:** reports, appeals, moderation decisions, identity/business verification information supplied to a payment or compliance provider, and communications with support.

The precise categories collected in production must be confirmed against the final release build, SDK inventory and live backend configuration before this document is approved.

## 3. Why information is used

FYNX may use information to:
- create and secure accounts, authenticate users and recover access;
- display profiles and operate social, messaging, group, calling and media features;
- match contacts when the user deliberately enables that feature;
- publish, deliver and moderate user content;
- operate business profiles, marketplace listings, orders, delivery coordination, reviews and buyer/seller support;
- process payment-provider requests and reconcile order/payment states when enabled;
- provide AI assistance requested by the user;
- send notifications selected by the user;
- detect fraud, scams, abuse, security incidents and policy violations;
- respond to reports, disputes, appeals, legal requests and support enquiries;
- maintain, troubleshoot, measure and improve service reliability and security; and
- comply with applicable law and provider requirements.

## 4. Sharing and service providers

Information may be shared only as needed for the relevant feature, with:
- other FYNX users according to the user's profile, content, group, messaging, marketplace and visibility choices;
- payment providers and financial institutions to initiate, verify, reconcile or refund a payment, where enabled;
- hosting, database, media/object-storage, email-delivery, push-notification and AI service providers that support FYNX;
- delivery providers or drivers where the buyer/seller selects or uses a delivery arrangement;
- moderators, support staff and authorised administrators for safety, support and platform operation; and
- regulators, law enforcement or other parties where legally required or necessary to protect users, the service or legal rights.

The exact production providers, their legal names, processing locations, contractual terms and retention settings must be confirmed before publication. Source code contains integrations or adapters for Google Firebase Cloud Messaging, Google's Gemini API, email delivery, payment providers and PostgreSQL/S3-compatible storage; this does not prove which provider configuration is active in production.

## 5. AI features

When a user invokes FYNX AI features, the relevant prompt, bounded conversation context and user-selected attachments may be processed by the configured AI provider. FYNX's reviewed AI tool boundary excludes private wallet balances, reserved funds, ledgers, transaction histories, payment/bank credentials, passwords, PINs, tokens, private messages, private addresses, KYC documents and non-public orders from approved AI tools. Users should not submit passwords, payment credentials, government identifiers or other highly sensitive information in ordinary prompts.

Provider retention, training settings, data region and contractual safeguards must be verified before publication.

## 6. Payments, marketplace and wallet

FYNX's application code contains payment, protected-order, wallet and settlement foundations. Availability is subject to provider activation, business compliance, legal review and feature-specific approval. FYNX does not represent itself as a bank or licensed escrow institution by virtue of maintaining an internal order-protection or accounting ledger. Do not interpret an in-app pending/protected status as a bank deposit or independently regulated escrow service.

Where a payment feature is active, the relevant payment provider processes payment credentials and payment instructions under its own terms and privacy notice. FYNX may retain transaction and order records needed for reconciliation, refunds, disputes, fraud prevention and legal obligations.

## 7. Retention

FYNX intends to retain personal information only for as long as needed for the stated purposes, account operation, security, dispute handling and applicable legal obligations. Exact retention periods, backup expiry, provider log retention and financial-record retention have not yet been verified. A final retention schedule must be approved and published before this policy is made effective. Do not infer that deletion from the primary account database immediately erases backups or copies held by processors.

## 8. Security

FYNX uses technical and organisational controls appropriate to the data and feature, including authenticated access, server-side authorization, encrypted local session-token storage and protected media access paths in the reviewed source. No internet service can guarantee absolute security. Production configuration, access controls, backup protection, incident response and provider security terms must be independently verified.

## 9. User choices and permissions

Users can control feature use through app settings and Android permissions. Contacts, location, camera, microphone, photos/videos and notifications should be requested in context. Revoking an Android permission may prevent the associated feature from working. Users should use in-app privacy controls where available and contact [PRIVACY CONTACT] to exercise applicable privacy rights.

## 10. Account closure and deletion

FYNX must provide a discoverable in-app account deletion request and a separate public web deletion resource before Google Play release. A source review identified financial, order, dispute, delivery and audit relationships that make immediate deletion of every user row unsafe. The deletion workflow and public deletion page are not confirmed as implemented. Any legally required financial, fraud, dispute or security records may need to be retained for a defined period, with unnecessary personal data removed or de-identified where lawful. The final process, retention exceptions, verification steps and completion timelines must be approved and tested before this section is published.

Deletion requests: [PUBLIC ACCOUNT-DELETION URL REQUIRED]  
Privacy enquiries: [MONITORED PRIVACY EMAIL REQUIRED]

## 11. Children

FYNX's intended minimum age and age-assurance approach must be formally determined and aligned with its target audience, content rating and applicable law before launch. Do not publish a guessed age threshold.

## 12. International processing

FYNX may use infrastructure and service providers located in countries other than the user's country. Actual data locations and any cross-border transfer safeguards must be confirmed from production provider settings and contracts before this section is finalised.

## 13. Changes and contact

FYNX may update this policy when its features, providers or legal obligations change. The effective date will be shown at the top of the published policy.

**Controller/operator:** [LEGAL ENTITY]  
**Registered/business address:** [VERIFIED ADDRESS]  
**Privacy contact:** [MONITORED EMAIL]  
**Public website:** [VERIFIED HTTPS DOMAIN]

---
## Publication blockers — must be cleared

1. Confirm legal operator/controller identity, registered/business address and monitored privacy email.
2. Confirm production database, hosting, storage, email, AI, Firebase and payment providers; regions, logs, contracts and retention.
3. Complete payment/wallet financial-data inventory and Data safety mapping.
4. Approve retention schedule and account-deletion implementation, including orders, wallets, disputes, media, backups and processors.
5. Verify contacts, location, microphone/call, media and SDK behavior on the release build.
6. Confirm minimum age, target audience and applicable jurisdiction.
7. Publish as an editable-controlled, globally accessible HTTPS HTML page (not a PDF) only after legal review; link inside the app and Play Console.
