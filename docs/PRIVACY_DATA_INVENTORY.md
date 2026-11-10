# FYNX privacy and personal-data inventory

**Review date:** 2026-11-10  
**Source baseline:** `integration/fynx-single-apk-20261013` at `c7718b35c80f955e70c9d45793461698e39b0f16`  
**Status:** Initial source-backed inventory; incomplete; not a privacy policy or a Data safety declaration.

This document records what the reviewed source shows, what remains uncertain, and what must be supplied before FYNX can publish accurate legal disclosures. A source code reference proves that a code path exists; it does not prove the current production configuration, provider retention, runtime behavior, or legal compliance.

## 1. Scope and limits

Reviewed Android manifest and dependencies, selected Android clients, backend route modules, AI provider integration, media storage adapter, and discovery/notification persistence code.

Not verified in this pass:
- Live production database contents, database/backup region, retention settings, or deletion jobs.
- Actual environment-variable values, provider account configuration, vendor contracts, subprocessors, or data-processing terms.
- Hosting edge/access logs and whether URL query strings are retained.
- Full runtime permission prompts and every feature's data path on a physical device.
- Final Play Console Data safety answers.
- A deployed public privacy policy or account-deletion website.

Do not treat an unverified item as absent. Mark it as needing provider/runtime confirmation.

## 2. Verified or source-indicated data flows

| Data category | Source and purpose | Recipient / storage indicated by source | Remaining questions |
|---|---|---|---|
| Account identity | Registration/login uses username, display name, phone, password and optional email. The backend user record includes password hash, phone, email-verification and session-version fields. | FYNX backend database. Android access tokens are encrypted with AES/GCM using Android Keystore before local preference storage. | Confirm all profile/account fields, identity vendors (if any), data region, retention and account-deletion behavior. |
| Contacts and SIM contacts | The Contacts screen reads contact display names and phone numbers from Android Contacts Provider, including SIM-backed rows. It normalises numbers before matching. | The Android client sends each normalised phone number as an individual HTTPS `/api/users/search?q=…&mode=phone` request. The backend compares the digits to the phone value stored on FYNX user records and returns matching account profile fields. Contact names are not included in that lookup request. | Hosting/access logs may record request URLs; their query-string logging and retention have not been checked. Confirm provider logs, retention and whether any additional device sync exists outside this path. |
| Location | The post-location client requests coarse/fine location only when resolving a place label. It uses the last known GPS/network location and Android `Geocoder` to build a locality/region/country label. | The social post creation path stores a text location label. This path does not add raw latitude/longitude to the post request. | Android Geocoder may use a system geocoding provider; its network transfer, processor and retention behavior have not been verified. Verify every other location entry point separately. |
| Camera, microphone and selected media | The manifest declares camera, microphone, photos/videos, and legacy read-storage permissions. CameraX handles capture; Home composer and Marketplace seller media use Android's Photo Picker. The Status Hub also has a custom Recent Media browser: after the user selects "Allow access to recent media", it requests READ_MEDIA_IMAGES/VIDEO on Android 13+ or READ_EXTERNAL_STORAGE on older supported versions and queries MediaStore for recent items. It has an OpenDocument fallback that does not require broad library access. The camera/gallery flow copies selected media into app cache for preview/editing; the reviewed camera upload callback runs after the user chooses Send. | Backend media records include owner, MIME type, byte size and storage-provider/object-key metadata. The storage adapter supports PostgreSQL and S3-compatible Neon/R2 modes; the actual selected provider depends on runtime configuration. | Assess whether the Status Hub custom Recent Media experience still needs broad library access or can preserve required multi-select/layout behavior through Photo Picker/scoped access. Audit every other upload route, media cache cleanup, provider region, object retention, signed URL/access policy and delete propagation. |
| Private messages and group content | Message, group and media routes process message text, sender/recipient or group membership, timestamps, delivery/read state and attached media. | FYNX backend database, authenticated media storage and realtime/push delivery infrastructure. | Verify complete message/group schema, retention, export/deletion behavior, encryption at rest, realtime processor configuration and notification-preview contents. Do not claim end-to-end encryption without evidence. |
| Social posts, comments, status and Marketplace content | User-generated content includes text, media references, visibility/audience settings, account identifiers and timestamps. Marketplace listings include seller, product, price/currency, stock, location and media references. | FYNX backend database and configured media storage. | Complete an object-by-object retention and deletion map, especially orders, disputes, reviews, delivery evidence and financial records. |
| AI assistant inputs | The AI conversation flow persists user/assistant text against the authenticated account. It can load prior messages for the same conversation and attach up to four user-owned images; the Gemini provider request contains the current message, bounded history/context and inline image data. | FYNX stores AI conversation text and attachment references. The backend sends AI inputs to Google's Gemini API using server-side credentials. | Confirm Google AI service terms, data processing settings, retention/training treatment, region, provider account configuration and user-facing disclosures. A prompt typed by the user can still contain personal information even though private FYNX tools are restricted. |
| Discovery/engagement events | The discovery event route records authenticated user ID, event type, optional post/listing/target-user IDs, session ID, JSON metadata and creation time. Event types include views and engagement actions. | FYNX database table `fynx_discovery_events`. | Define purpose-specific retention, analytics access, metadata limits and user controls. |
| Push notification device data | The notification-device route stores provider, device token, enabled state and created/updated/last-seen times against an account. | FYNX database; Firebase Cloud Messaging is included in the Android app and used for push delivery. | Verify notification payload contents, Firebase project configuration, Google/Firebase processing terms, token lifecycle and retention. |
| Email verification and account recovery | Email-verification and password-reset routes store hashed codes, expirations, attempt counters and send timestamps; the email delivery adapter sends the email to the configured address. | FYNX backend database plus the configured email delivery provider. | Identify the actual provider, region, logs, retention, subprocessor list and deletion handling. |
| Payments, wallet and seller payouts | Source includes marketplace payments, order/settlement data and wallet-related foundations. This requires a dedicated financial-data inventory because buyer/seller, provider reference, financial ledger, payout destination and dispute data can have separate retention obligations. | FYNX backend database and configured payment/banking providers. | Full provider-by-provider and table-by-table mapping is not complete in this Stage 1 pass. Do not publish payment Data safety answers from this partial inventory. |

## 3. Relevant implementation details

### Contacts matching

- `FynxContactsPanel.kt` reads the primary device contacts and SIM contacts after Contacts permission is granted.
- `FynxSocialClient.searchUsers(..., phoneSearch = true)` encodes a single phone query in the request URL.
- `backend/socialRoutes.js` handles `GET /api/users/search`, strips non-digits from the supplied phone query and performs a database match against account phone values.
- The reviewed endpoint does not insert a contact-book/address-book table record. This does not rule out infrastructure-level request logging.
- The Contacts UI change in this Stage 1 PR adds a versioned, account-scoped disclosure before it reads contacts or requests Android Contacts permission. The disclosure is only for this feature; it is not acceptance of Terms of Use or a Privacy Policy.

### Local authentication token

- `FynxSecureTokenStore.kt` encrypts the access token with AES/GCM and an Android Keystore key before saving it locally.
- `android:allowBackup="false"` is set in the application manifest.
- This is a positive local-security control, not a substitute for server-side token revocation, session expiry, or account deletion.

### Media storage

- `backend/mediaStorage.js` supports PostgreSQL, Neon object storage and Cloudflare R2 adapter modes.
- The default provider in the adapter is PostgreSQL when no provider override is present; deployment configuration has not been inspected, so the actual production provider must be confirmed.
- Signed download URL expiry is bounded in the adapter; this does not tell us the underlying object retention period.

### AI privacy boundary

- The AI tool privacy policy explicitly excludes wallet balances, reserved funds, ledgers, transaction histories, bank/payment credentials, passwords, PINs, tokens, private messages, private addresses, KYC documents, private orders and non-public social/account records from approved AI tools.
- This is a useful tool-access boundary, but it does not stop a user from typing sensitive information into an ordinary AI prompt. Disclosures and provider settings are still required.


### Sensitive permission paths verified in this pass

- **Contacts:** The Phone Contacts feature now displays a versioned, account-scoped disclosure before it reads device/SIM contacts or requests `READ_CONTACTS`, including when Android permission was granted previously. It explains per-number HTTPS lookup and that the contact name is not included in that lookup. Query-string/access-log retention at the hosting layer remains unknown.
- **Location:** The Home composer Location chip and Marketplace Nearby feature request coarse/fine location when the user activates those functions. They call `FynxPostLocationClient.currentPlace()`, which resolves a human-readable locality label; the Home post and Marketplace nearby request use that label rather than raw coordinates. Chat location sharing now shows an in-app explanation before the Android permission prompt, then a separate in-chat confirmation before sending latitude/longitude to that conversation. The explanation states that coordinates are not sent until the user confirms Share. Home/Marketplace location entry points still need a focused pre-permission/context review, and the geocoding provider/network processing remains unverified.
- **Camera and microphone:** The camera capture panel no longer auto-launches Android permission when it opens. It first explains that camera access is needed to capture photos/videos and offers an explicit Allow camera action; that action requests CAMERA only. RECORD_AUDIO is requested separately when the user initiates a video recording that needs sound. Other voice paths request microphone permission from their feature actions; audit each path for contextual explanation, denial/revocation and confirmation before any recording is uploaded.
- **Photos and videos:** Home composer and seller media use Android Photo Picker. The Status Hub also offers a custom Recent Media browser that queries MediaStore after a broad photos/videos permission grant; its "Allow access to recent media" action precedes the OS prompt. The separate OpenDocument fallback can select media without broad library access. This is a specific permission-minimisation candidate; do not remove the current capability without preserving Status multi-select/layout behavior.
- **Notifications:** The startup path no longer launches `POST_NOTIFICATIONS`. `MainActivity` registers an FCM device token only when the signed-in account's cached push-alert preference is enabled and Android permission is already granted (or the OS version does not require the runtime permission). The Push alerts switch in Notification Preferences now shows an off state when Android 13+ permission is missing; enabling it first presents an in-app explanation, then launches the OS prompt. On grant it enables the FYNX push preference and registers the device token; on denial it leaves the preference off and explains how to enable it later in Android Settings. Permission state is refreshed when the screen resumes. Verify all of this on Android 13+ and earlier versions in runtime tests.
- **Exact alarms:** `CalendarReminderScheduler` uses `setExactAndAllowWhileIdle` only when Android reports exact-alarm scheduling is allowed; otherwise it falls back to `setAndAllowWhileIdle`. This permission is tied to Calendar reminders and should not be removed without changing that reminder behavior.

## 4. Account deletion readiness

**Not implemented/verified in the reviewed source baseline.**

The review did not find a complete account-level deletion journey with all of the following:
1. Discoverable authenticated in-app request path.
2. Public web request path that works after the app is uninstalled.
3. Identity/ownership checks and a confirmation step that prevents accidental deletion.
4. A complete relationship map for data owned by the account, including media/object storage, notifications, AI conversations, social content, Marketplace records, financial ledgers, disputes and delivery evidence.
5. A deletion job that handles database rows and external object storage.
6. Requests to processors to delete data they process for FYNX.
7. A documented schedule and explanation for legally required security, fraud, accounting or payment retention.
8. User confirmation and support/escalation for requests that cannot be completed automatically.

Foreign-key cascades exist in parts of the schema, but they are not sufficient evidence of complete deletion: object storage, payment/financial evidence, backups and processors require separate handling. Do not add a destructive account-delete endpoint until the relationship/retention map and processor behavior are complete.

Google's current account-deletion instructions require deletion of associated account data, including relevant third-party processor data, while allowing justified legal/security/fraud retention that is disclosed. They also require a public web request resource even when the app provides an in-app deletion flow.

## 5. Items that cannot safely be finalized yet

- Legal controller identity: registered entity or individual developer name, country, business address and jurisdiction.
- A monitored privacy/support email and accountable contact.
- Countries/markets where FYNX will first operate and the legal bases applicable to each processing purpose.
- Retention periods for each data category and the backup deletion schedule.
- Actual deployment vendors, regions, backup policy and subprocessor list.
- Gemini, Firebase/FCM, email, hosting/database, media/object-storage, WebRTC and payment-provider contractual/data-processing settings.
- A live public HTTPS privacy-policy URL and account-deletion web URL.
- Final Terms of Use and Community Guidelines, including seller/payment, user-content licence, moderation, dispute and age-related provisions.
- Exact Play Data safety and account-deletion questionnaire answers.

These details must be supplied or verified before a public legal policy is published. Do not use placeholder URLs or an invented developer identity.

## 6. Next engineering sequence

1. Complete the code-backed inventory for all permissions, SDKs, processors and data paths.
2. Add prominent in-app disclosures before each sensitive data access that users may not reasonably expect; preserve permission denial and account isolation.
3. Identify all account-owned tables, objects, backups and processors; design an auditable request/verification/deletion lifecycle.
4. Implement and test the in-app deletion request path and the public deletion web resource when a real host and verified legal contact are available.
5. Prepare privacy policy, Data safety, retention and user-rights disclosures only from verified facts.
6. Add versioned Terms of Use / Community Guidelines acceptance once the developer identity and reviewed legal text are supplied.

## 7. Official references checked 2026-11-10

- Google Play User Data policy: https://support.google.com/googleplay/android-developer/answer/10144311
- Google Play account-deletion requirements: https://support.google.com/googleplay/android-developer/answer/13327111
- Google Play prominent disclosure and consent best practices: https://support.google.com/googleplay/android-developer/answer/11150561
- Google Play UGC moderation and Terms of Use requirements: https://support.google.com/googleplay/android-developer/answer/12923286
- Google Play Data safety: https://support.google.com/googleplay/android-developer/answer/10787469

---

**Completion rule:** This inventory is a working engineering record. It is not a published privacy policy, legal opinion, Data safety submission, or claim of Google Play approval.
