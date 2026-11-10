# FYNX account deletion — dependency and retention design

**Review date:** 2026-11-10  
**Reviewed branch:** `compliance/stage1-privacy-foundation-20261110`  
**Reviewed source commit:** `459aaf4bd16639aad9ba798f8571a9abf64962be`  
**Status:** Engineering design only. No deletion endpoint, worker, database mutation, or public deletion page is implemented by this document.

This design records the deletion blockers found in the source. It is not a legal retention schedule and does not assert that any current production database, storage provider, or processor has been configured in a particular way.

## 1. Why a direct user-row delete is unsafe

The schema mixes ordinary account-owned content with records that represent orders, money movement, disputes, payout destinations, driver custody and evidence. Some user relationships cascade; several financial/operational relationships explicitly use `ON DELETE RESTRICT`. Deleting a row from `users` therefore cannot be treated as the account-deletion operation.

A failed delete is not a deletion workflow. Changing foreign keys to `CASCADE` merely to make deletion succeed would risk destroying transaction and dispute history. Conversely, retaining every personal field indefinitely would also be inappropriate. The implementation needs a deliberate split between (a) data that can be erased, (b) data that can be anonymised or detached while preserving a transaction record, and (c) data that must be retained for a verified legal, security, fraud or payment purpose for a defined period.

## 2. Source-confirmed relationship map

The following constraints were read from the reviewed backend modules. This is a focused map of deletion-critical relationships, not an exhaustive inventory of every table or migration.

| Area | Source-confirmed relationships | Deletion implication |
|---|---|---|
| Marketplace orders | `backend/marketplaceProtection.js`: `marketplace_orders.buyer_id` and `seller_id` reference `users(id) ON DELETE RESTRICT`; `listing_id` references listings with RESTRICT. | A buyer or seller with an order cannot be removed by deleting the user row. A listing referenced by an order also cannot simply be removed. |
| Protection cases | `marketplace_protection_cases.opened_by` references users with RESTRICT; the case's order reference cascades from the order. | Opened-by identity needs a retention/anonymisation rule. Do not cascade-delete cases as a shortcut. |
| Wallet | `backend/buyerWallet.js`: `buyer_wallets.user_id` references users with RESTRICT. Funding intents, transactions and provider operations also reference wallet/user/transaction records with RESTRICT. Ledger entries reference transactions and wallets with RESTRICT. | Wallet rows, provider operations and ledger entries block a direct user delete. Any anonymisation must preserve amount, currency, status, references, auditability and reconciliation integrity. |
| Settlement and escrow | `backend/marketplaceSettlement.js`: payout accounts and escrow buyer/seller IDs reference users with RESTRICT. Escrow references its order; marketplace ledger and financial-operation rows reference escrow/order. | Payout/escrow identities require a documented retention or pseudonymisation decision. Do not delete or rewrite financial rows without a reviewed accounting and reconciliation design. |
| Delivery | `backend/marketplaceCompletion.js`: delivery assignments reference seller and driver users with RESTRICT; approved-driver records reference approver with RESTRICT; delivery evidence references submitter with RESTRICT. | Sellers, drivers, approvers and evidence submitters may block deletion. Preserve chain-of-custody facts while minimising direct identifiers where permitted. |
| Social content | `backend/socialRoutes.js`: seller listings, posts, comments, follows, likes, reposts and audience links include user relationships, many with CASCADE. Post media references use SET NULL. | Some ordinary social content may disappear through cascades, but this does not cover external media objects, retained copies, screenshots, repost copies or downstream processors. |
| Moderation audit | `backend/marketplaceProtectionResolution.js`: protection audit rows reference cases/orders with CASCADE; actor identity uses SET NULL. | The schema already demonstrates a pattern for retaining an audit event while detaching an actor. Any broader use must be deliberate and preserve only necessary data. |

### Important schema warning

Some financial ledger/operation rows currently reference orders or escrows with `ON DELETE CASCADE`. A CASCADE rule is a database behavior, not proof that deleting the parent is legally or operationally acceptable. Before enabling deletion, review the complete transaction lifecycle, provider reconciliation, refunds, chargebacks, disputes, reporting and any required retention with the responsible finance/legal owner.

## 3. Data groups and proposed treatment

These are design categories, not final legal decisions.

| Data group | Proposed handling after verified request | Must be decided before implementation |
|---|---|---|
| Login credentials and sessions | Revoke active sessions immediately after confirmed request; disable new sign-in while the request is processed. Remove password/reset/verification secrets when deletion completes. | Whether a short cancellation window is needed and how ownership is re-verified. |
| Profile and public identity | Remove public profile visibility promptly; erase or replace direct identifiers when no retention basis applies. | Whether a seller/driver role needs to remain associated with retained transaction evidence in pseudonymous form. |
| Posts, comments, follows and reactions | Delete or anonymise account-owned content according to the chosen policy; remove audience/follow/reaction links. | Treatment of content already copied, quoted, reposted or relied upon by another user; notice to affected users. |
| Private messages and group membership | Remove account access and membership; apply a documented policy to messages and attachments visible to other participants. | Whether message content is erased, retained for recipients, or pseudonymised; group ownership/continuity. Avoid promising that deleting an account deletes copies already delivered to recipients. |
| Marketplace listings and seller media | Deactivate listings and prevent new purchases immediately; remove media objects when no longer referenced or required. | Open orders, returns, refunds, disputes, reviews and delivery obligations must be resolved or transferred before destructive removal. |
| Wallet, funding, payouts and ledgers | Lock new financial activity and revoke payment/payout capabilities. Retain only the minimum records approved for reconciliation, fraud/security and applicable legal obligations; pseudonymise direct identity where permitted. | Jurisdiction-specific retention periods, provider requirements, open balances, pending withdrawals, chargebacks, tax/accounting duties and the approved anonymisation key strategy. |
| Disputes, protection and delivery evidence | Keep open cases actionable; restrict access to authorised support/admin roles. After closure, retain the minimum approved evidence for the approved period. | Evidence types, legal hold triggers, access audit, retention start date and deletion deadline. |
| AI conversations and attachments | Delete account-linked conversations and FYNX-held attachment objects when eligible; revoke access immediately. | Gemini request/log retention and deletion capabilities must be confirmed from the actual Google AI service terms/configuration. |
| Device tokens and notifications | Disable and delete FCM/device registrations; stop notifications. | Confirm any provider-side token retention and delivery logs. |
| Media and object storage | Delete database metadata and the underlying object from the configured provider; clean thumbnails/transcodes/cache copies and orphaned objects. | Runtime provider is not confirmed. PostgreSQL BYTEA and S3-compatible Neon/R2 paths need separate, tested deletion adapters. |
| Backups, logs and processors | Apply a documented expiry schedule and provider deletion requests where supported; keep only narrowly required security/audit evidence. | Backup rotation, log retention, hosting access logs, email provider, FCM, Gemini, storage and payment processor terms/regions. |

## 4. Required deletion request lifecycle

Implement this as an auditable state machine, not a single destructive HTTP handler.

1. **Request created:** authenticated in-app request or public web request; create a random request ID and timestamp. Do not store a raw password, OTP or identity document in the request record.
2. **Ownership verification:** re-authenticate the account owner using an approved method. Public web requests must work for people who have uninstalled the app, without allowing an unauthenticated person to delete another user's account.
3. **Cooling-off / cancellation:** define and disclose any cancellation period. During it, explain the account state and provide a secure cancellation path.
4. **Immediate protection:** after the request is confirmed, revoke sessions, disable account use, stop new listings/orders/payout changes and suppress non-essential notifications. Keep support access tightly scoped.
5. **Dependency assessment:** classify open orders, balances, pending funding/withdrawals, refunds, chargebacks, disputes, delivery assignments, legal holds and security investigations. Route blocked cases to authorised review; never silently mark them complete.
6. **Erase eligible data:** execute idempotent, retryable jobs for database records and external objects. Record counts/outcomes without copying the deleted personal data into the audit log.
7. **Processor and backup handling:** submit supported processor deletion requests and apply the published backup expiry schedule. Record provider request IDs/status, not credentials or unnecessary payloads.
8. **Completion and notice:** confirm completion only after all required jobs succeed or clearly disclose the specific retained categories, reason, and expected review/expiry date. Keep a minimal non-identifying completion receipt if approved.
9. **Failure recovery:** retries must be safe; partial completion must remain visible to authorised operators. Never report success while external object deletion or mandatory processor requests remain unresolved.

Suggested internal states: `REQUESTED`, `AWAITING_VERIFICATION`, `VERIFIED`, `COOLING_OFF`, `PROCESSING`, `BLOCKED_REVIEW`, `PROCESSOR_PENDING`, `COMPLETED`, `CANCELLED`, `FAILED_RETRYABLE`. Final state names and transitions must be tested and documented before API implementation.

## 5. Minimum engineering components

- A deletion-request table with opaque request ID, account reference, state, timestamps, verification outcome, cancellation deadline, reason codes and retry metadata. Avoid duplicating profile fields.
- A transactionally safe request endpoint with rate limiting, CSRF protection for web sessions, ownership verification and idempotency.
- A worker/queue that processes one account at a time and supports retries, dead-letter visibility and operator escalation.
- A dependency classifier for open financial/Marketplace obligations and legal holds.
- Per-provider media deletion adapters with object-key inventory, retries and orphan reconciliation.
- Session/token revocation and account lockout that do not depend on the account row being physically deleted immediately.
- A privacy-safe audit trail: actor, action, timestamp, request ID, outcome and reason code; do not copy message text, bank data, full contact details or identity documents into audit metadata.
- A public HTTPS deletion-request page, separate from the app, with clear scope, verification steps, contact and expected timing. It must be deployed and tested before Play submission.
- Monitoring and support runbook for blocked, failed and processor-pending requests.

## 6. Tests required before release

- User A cannot create, inspect, cancel or complete User B's deletion request.
- Repeated submissions create one effective request; repeated worker execution is idempotent.
- Sessions are revoked and the account is locked at the correct lifecycle point.
- Accounts with no Marketplace/financial records can complete the ordinary deletion path.
- Accounts with open orders, pending wallet operations, payout records, disputes, driver assignments or evidence are blocked/routed safely, not force-deleted.
- Resolved/closed financial cases follow the approved retention and anonymisation policy.
- Deleting an account removes eligible PostgreSQL media and external objects, thumbnails and derived files; retries recover from provider outages.
- AI conversation rows/attachments, notification tokens, social records and contact-derived data follow the documented policy.
- Backup expiry and processor deletion outcomes are observable without exposing deleted personal data.
- Public web requests work after uninstall and cannot be abused for account takeover.
- User-facing status accurately distinguishes completed, retained-for-reason and still-processing data.

## 7. Inputs still required — do not guess these

1. The real legal/controller identity, jurisdiction and monitored privacy contact.
2. The countries in which FYNX will initially operate and the approved retention schedule for account, financial, fraud, dispute, delivery and security records.
3. Actual production database, backup, hosting, media-storage, email, Gemini, FCM and payment-provider configuration, regions, logs and contracts.
4. The final policy for private messages and content already delivered to other users.
5. The verified public HTTPS domain and owner of the deletion request page.
6. The approved ownership-verification and cancellation method.

Until these are supplied and reviewed, do not publish a retention period, promise immediate erasure of every copy, or implement destructive user deletion.


## 3A. Additional user-linked records found in the broader source scan

The follow-up scan of backend modules on this branch found additional categories that must be included in the deletion job and policy. The scan is source-based; runtime-created tables and live database constraints still need a final database-level export.

| Feature | Source-backed relationships | Deletion concern |
|---|---|---|
| AI history | `backend/aiConversationRoutes.js`: conversations reference users with CASCADE; AI messages reference conversations and users with CASCADE; attachment links reference AI messages and `message_media` with CASCADE. | FYNX-held conversation rows and attachment links are cascade-linked, but the underlying media object and Gemini-side processing/log retention require separate handling. |
| Notifications | `backend/notificationDevices.js` and `backend/notificationPush.js`: device tokens, preferences, notifications and delivery records are user-linked with CASCADE. | Remove/disable tokens promptly; verify provider delivery logs and any token copies outside the database. |
| Groups | `backend/groupMembershipRoutes.js`: group invites/reports reference group and creator/reporter users with CASCADE; report target user uses SET NULL. `backend/groupContentRoutes.js`: group posts, poll votes, events and RSVPs are user/group-linked with CASCADE. | A cascade may remove reports or group activity that FYNX needs for abuse investigations. Decide how to retain a minimal moderation record while detaching reporter/target identity. Transfer group ownership/administration before removing the owner. |
| Status | `backend/statusInteractionRoutes.js`: views, likes, reactions and replies reference status/user rows with CASCADE. | Expired status media and its object-store copies need separate lifecycle handling; interaction data may be removed with the account. |
| Discovery and saved content | `backend/discoveryRoutes.js`: discovery events, saved posts, audience links and repost links are user-linked; target-user and quoted-post references use SET NULL in selected paths. | Define retention for report events and safety signals before allowing cascades to erase them. |
| Marketplace reputation | `backend/marketplaceReputation.js`: seller reviews reference order, seller and buyer with CASCADE. `backend/marketplaceSchema.js`: weekly seller-award seller reference uses SET NULL. | Account deletion can erase review history through CASCADE. Decide whether reviews should be deleted, retained with a pseudonymous author, or otherwise handled under a documented policy. |
| Business and shop profiles | `backend/businessRoutes.js` and `backend/marketplaceShopProfiles.js`: business owner/shop seller references use CASCADE. | Deactivate public business/shop surfaces immediately; check outstanding ads, listings, orders and obligations before removing the profile. |
| Advertising and admin | `backend/marketplaceAdvertising.js`: campaign owners, operations and click viewers use CASCADE; seasonal promotion `created_by` uses RESTRICT; admin-role rows use CASCADE. | A user who created a seasonal promotion may block physical deletion. Preserve campaign billing/metrics and ownership history under an approved policy; do not let an account delete silently remove advertising audit evidence. |
| Money planner | `backend/moneyPlanner.js`: personal planner/budget rows are user-linked with CASCADE. | Treat as account-owned personal data for erasure, including any derived exports or logs. |

This scan adds important deletion risks not captured by the first focused FK list: group safety reports and seller reviews are currently cascade-linked, while seasonal-promotion creator identity is another RESTRICT dependency. A future implementation must not equate “database cascade succeeds” with “all retention and user-rights duties are satisfied.”

## 8. Next implementation gate

Before writing the deletion endpoint, complete a full schema/foreign-key scan across every backend module and migration, including runtime-created tables; inspect media storage adapters and object-key lifecycle; and obtain the retention/provider decisions above. Then implement the request state machine and worker in a separate tested change. The account row must not be physically deleted until the dependency classifier proves that all retained obligations have been detached or lawfully preserved.
