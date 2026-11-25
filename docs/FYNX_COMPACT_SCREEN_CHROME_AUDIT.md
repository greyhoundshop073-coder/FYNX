# FYNX Compact Screen Chrome Audit

Status: active; findings are source-inspected, not yet all runtime-certified.

## Purpose

Reduce wasted vertical space and repeated UI across FYNX without removing useful controls or changing feature behaviour. This audit covers:
- duplicate or repeated screen titles;
- long generic introductory copy that merely explains the obvious;
- search fields that are unnecessarily tall or widely spaced;
- duplicated back buttons between the app shell and child screens;
- content starting too far below the app header;
- scrollable content obscured by pinned headers or controls.

This is separate from the More feature-directory redesign. It must not replace, revert, or delay existing feature work.

## Shared design rule

1. Keep one clear title: prefer the app-shell title when the destination already has a shell header.
2. Keep descriptions only when they provide information users need to make a decision or understand a limitation.
3. Remove generic promotional paragraphs and repeated explanations of the visible controls.
4. Keep search functional, accessible and comfortably tappable, but use a compact consistent height and spacing.
5. Keep one visible back action per navigation level. Preserve Android system-back handling and the correct route destination.
6. Do not remove section labels, meaningful warnings, consent, safety disclosures, financial limitations, or task-specific instructions.
7. Ensure the first useful content appears promptly and the final item remains reachable while scrolling.
8. Preserve existing colors, feature routes, role checks, data behaviour and accessibility labels unless a change is specifically required.

## Confirmed source findings and changes started

| Screen | Source finding | Action |
|---|---|---|
| More | The app shell already says More; the page repeated a large FYNX Features heading and generic paragraph, and the search field was taller than needed | PR #601 removed the repeated heading; this PR reduces the search field to 56dp and tightens its surrounding vertical gaps. More card glyphs distinguish Official Announcements from Advertising and Money Center from Wallet |
| Money Center | The shell supplies Money Center, while the panel repeated its own title, a long introductory sentence and a large explanatory card | Removed repeated heading/intro card; tightened list top padding; updated the authenticated runtime navigation expectation to use the shell title |
| Business Account | The shell supplies the title and back action; the panel repeated title/description and added a second Back button at the bottom | Removed duplicate title/description and redundant Back button; Save Business now uses the available row width |
| Privacy & Safety | The app shell supplies the page title and back action; the child panel drew another back arrow, title and subtitle | Removed the child header and extra status-bar inset; retained privacy content and support/safety controls |
| Money Insights | The nested panel repeated a large title and generic description; the shell provides the destination title | Replaced the oversized headline with a compact child title, removed the generic description and shortened the financial-data limitation note |
| Budget Planner | The nested Money Center tool used a large headline and a sentence that simply described the visible budget controls | Kept the child title at compact scale, removed the generic sentence and reclaimed top padding |
| Savings Goals | The nested Money Center tool used a large headline and generic intro | Kept a compact title and a short local-storage disclosure; reduced top padding |
| Bills & Payment Reminders | Large title plus a long line that mixed obvious tracking text with an important no-automatic-payment limitation | Compact title and kept only the essential no-automatic-payment disclosure in small text |
| Subscriptions | Large title plus a long sentence repeating the recurring-cost purpose | Compact title and retained the essential no-automatic-recurring-payment disclosure |
| Receipts & Expense Records | Large title plus a generic explanation of recording purchases | Compact title; removed redundant explanation and tightened top padding |
| Accounts & Wallets | Large title plus local-only/bank-connection explanation | Compact title and concise local-only/no-bank-connection disclosure |
| Transaction History | Large title plus generic description | Compact title and concise local-records-only disclosure |
| Spending & Expense Insights | Large title plus generic intro repeating the summary content | Compact title and removed generic intro |
| Money Planner | Large title plus generic description while nested under Money Center | Kept a compact child title and removed generic sentence; refresh and planner tabs remain |
| Chats | The main Chats shell title is repeated inside the panel along with a generic explanatory sentence | Confirmed candidate; PR #588 owns the panel, so a coordination request was posted instead of overwriting it |
| Groups | The Groups shell title is repeated inside the group panel with the generic sentence “Your communities in one place” | Confirmed candidate; PR #588/#599 share ownership of group UI changes, so a coordination request was posted instead of overwriting them |
| Notifications | The Notifications shell already supplies a back action; the panel adds another back arrow | Confirmed duplicate-back candidate; PR #586 owns NotificationPanel, so a coordination request was posted |

| Calendar | The app shell already titles the destination Calendar; the panel repeated a headline and generic explanatory sentence | Removed the repeated heading and generic sentence; kept the calendar controls and event content |
| Advertising Dashboard | The app shell already titles the destination Advertising Dashboard; the panel repeated a headline and generic sentence | Removed the repeated heading and generic sentence; kept campaign metrics and actions |
| Share & Invite | The app shell labels the destination Share, while the panel repeated a large Share & Invite title and an obvious intro paragraph before the action cards | Removed the extra title and generic paragraph; Invite Friends, Share FYNX and Copy Invite cards remain |
| To-Do | The app shell labels the destination To-Do; the panel added a second Daily Planning headline and generic reminder paragraph | Removed the two introductory lines; task statistics, search, filters and task actions now begin at the top |
| Group Invite | The app shell already supplies Back for the Invite destination; the panel drew a second Back arrow and an obvious “Review before joining” subtitle | Removed the inner back arrow and redundant subtitle while retaining the specific Group invite title, invite validation and join actions |
| Financial Overview | The source file exists but currently has no call site in the app | Left unchanged; confirm its intended route before editing |

## Protected workstreams and coordination

Do not edit these files while their existing PRs own them. Re-audit after their work merges, then submit follow-up changes against the updated canonical branch:
- `ProfilePanel.kt`, `NotificationPanel.kt`, account email and data/network settings: PR #586 (Settings/email/privacy readiness).
- `ChatsPanel.kt`, `FynxGroupsPanel.kt`, group settings, conversation panels and chat stores: PR #588 and call-related PR #599.
- `FynxApp.kt` app-shell headers/back routing: PR #599 and More PR #601. Do not change shell navigation as part of this cleanup without coordinating those owners.
- Marketplace, Home, Admin, Wallet, camera and call feature files: preserve their active PRs and ownership; inspect their final merged source before proposing edits.

The screenshot-confirmed Profile > Settings chrome issue remains a tracked high-priority follow-up because `ProfilePanel.kt` is currently owned by PR #586. Source tracing shows the outer Profile Back returns to Home while SettingsPanel's inner Back returns to Profile; these are distinct navigation levels, not duplicate actions. The compact fix should hide/collapse the outer Profile shell header while Settings is open, preserve the Settings Back-to-Profile action, remove redundant FYNX branding and generic intro copy, and reduce search height/gaps. Do not simply delete either Back action or silently overwrite PR #586.

## Remaining audit checklist

- [ ] Profile > Settings: hide/collapse the outer Profile shell header while the Settings page is open (the two existing Back actions have different destinations: Home vs Profile); preserve Settings Back-to-Profile, remove redundant FYNX branding/generic intro, and compact search; coordinate with PR #586.
- [ ] Notifications: trace Home-bell and Profile-settings entry paths separately; retain back actions when they represent different destinations, and remove only a genuinely duplicated affordance in coordination with PR #586.
- [ ] Chats and Groups: remove confirmed repeated headings and boilerplate, then inspect search sizing and nested back controls in coordination with PR #588/#599.
- [ ] Home and Feed: review header chrome, search/discovery controls and pinned overlays without changing feed behaviour.
- [ ] Friends and Contacts: review titles, search and nested navigation; Contacts is owned by PR #503/#462 and has a coordination request pending.
- [ ] Status archive and story subpages: verify which headers belong to the shell versus the archive/story panel before changing local back controls.
- [ ] Marketplace, product details, My Shop and Seller Center: review headers and search while preserving buyer/seller workflows.
- [x] First compact pass across Budget, Savings, Bills, Subscriptions, Receipts, Accounts, Transaction History, Spending Insights and Money Planner; preserve financial/local-storage/payment disclosures.
- [ ] Review all remaining Money Center child tools, verify data-source caveats remain accurate, and verify the unused Financial Overview panel's intended route before editing it.
- [ ] Status, Story creation, camera and media editing: review editor chrome and back navigation.
- [ ] Admin and Owner Control Center: review role-specific headers without weakening authorization.
- [ ] Calls and Official Announcements: remove confirmed repeated headings/boilerplate in coordination with PR #589/#599 and PR #582/#536/#533.
- [ ] Confirm the compact Share, To-Do and Invite pages remain visually readable on a real emulator after the changes in this PR.
- [ ] Dialogs, sheets and secondary destinations: check for stacked titles and duplicate back affordances.
- [ ] Run focused Compose tests, static checks and full Android runtime/emulator capture; inspect screenshots for top and bottom clipping.

## Verification boundary

Source changes are not runtime certification. Report separately:
- source/diff review;
- compile and static checks;
- Compose/unit/instrumentation tests;
- real emulator screenshots and scroll checks;
- backend-dependent checks.

Do not claim app-wide completion until every checklist area has been inspected and relevant runtime evidence reviewed.
