# FYNX Engineering Research Register

Use this register to keep external technical research reusable across sessions. For each new research task, add the exact page/book/paper studied, not merely a company homepage. Record date checked, source quality, principle extracted and how it applies to FYNX. Prefer primary sources and current versions.

## Seed sources reviewed for the global builder standard — 2026-11-22

| Topic | Source | Quality / type | Principle relevant to FYNX | URL |
|---|---|---|---|---|
| Site reliability and large-scale operations | Google SRE Books, including *Site Reliability Engineering*, *The Site Reliability Workbook* and *Building Secure & Reliable Systems* | Primary publisher resource; engineering books by Google SRE practitioners | Reliability is designed and operated across the lifecycle; monitoring, release engineering, incident learning and testing matter | https://sre.google/books/ |
| Cloud architecture trade-offs | AWS Well-Architected Framework | Primary vendor architecture framework | Evaluate operational excellence, security, reliability, performance efficiency, cost optimization and sustainability together | https://docs.aws.amazon.com/wellarchitected/latest/framework/welcome.html |
| Android architecture and test strategy | Android Developers architecture recommendations | Primary platform documentation | Test ViewModels/data layers/navigation; use Macrobenchmark and Baseline Profiles where useful; optimize based on measured evidence | https://developer.android.com/topic/architecture/recommendations |
| Offline-first mobile design | Android Developers offline-first data layer | Primary platform documentation | Keep a local source of truth for app reads; separate local/network data sources; reconcile safely; account for unreliable networks | https://developer.android.com/topic/architecture/data-layer/offline-first |
| Secure software development | UK NCSC secure development principles | Primary national cyber-security guidance | Security belongs throughout delivery; protect source/build pipelines, test continuously and plan for vulnerabilities | https://www.ncsc.gov.uk/collection/developers-collection/principles |
| Secure-by-design implementation | UK NCSC Software Security Code of Practice implementation guidance | Primary national cyber-security guidance | Use an established secure development framework and threat modelling; security must not be bolted on after feature delivery | https://www.ncsc.gov.uk/collection/software-security-code-of-practice-implementation-guidance |
| Engineering practice and technology evaluation | Thoughtworks Technology Radar | Practitioner research / global engineering consultancy | Assess, trial, adopt or avoid technologies based on experience; do not adopt tools merely because they are fashionable | https://www.thoughtworks.com/radar |
| Indian engineering practice perspective | Thoughtworks India, “What are our core values and practices for building software?” | Practitioner engineering article | Fast feedback, repeatability, simplicity and clean code are useful delivery principles | https://www.thoughtworks.com/en-in/insights/blog/what-are-our-core-values-and-practices-building-software |
| Nigerian/African payment and policy context | CBN, NIBSS, NDPC, NCC, GSMA and AfricaNenda official publications | Primary regulator/industry sources; individual documents must be identified per task | Local payment rails, data protection, telecom/connectivity and interoperability must be validated against current official requirements | https://www.cbn.gov.ng/ ; https://nibss-plc.com.ng/ ; https://ndpc.gov.ng/ ; https://www.ncc.gov.ng/ ; https://www.gsma.com/ ; https://www.africanenda.org/ |

## Research items to deepen when relevant

These are research destinations to investigate for a concrete feature; they are not claims that a particular implementation has already been studied or adopted.

| Research lens | Primary destination | Investigate when |
|---|---|---|
| US platform engineering and consumer scale | Meta Engineering: https://engineering.fb.com/ ; Apple Developer: https://developer.apple.com/documentation/ ; Stripe Docs: https://docs.stripe.com/ | Feed ranking, social graph, media, mobile platform behavior, payments |
| China consumer super-app and commerce | Alibaba Cloud: https://www.alibabacloud.com/help ; Tencent Cloud: https://www.tencentcloud.com/document ; ByteDance open source: https://github.com/bytedance | Marketplace, messaging, feed, media delivery, distributed services |
| India payment interoperability and engineering delivery | NPCI: https://www.npci.org.in/ ; Thoughtworks India engineering: https://www.thoughtworks.com/en-in/engineering | Payment integrations, API contracts, cost-conscious delivery |
| South Korea mobile/product engineering | Samsung Developers: https://developer.samsung.com/ ; Naver D2: https://d2.naver.com/ ; Kakao Tech: https://tech.kakao.com/ | Android devices, media, mobile UX and performance |
| UK financial security and service design | FCA Handbook: https://www.handbook.fca.org.uk/ ; GOV.UK Service Manual: https://www.gov.uk/service-manual ; NCSC: https://www.ncsc.gov.uk/ | Wallet, financial controls, privacy, secure delivery and user journeys |
| Mobile security | OWASP MASVS: https://mas.owasp.org/MASVS/ | Authentication, storage, networking, platform integration and privacy |

## Entry template

### YYYY-MM-DD — Topic / question
- **FYNX component / user impact:**
- **Question investigated:**
- **Exact source title / author / organization:**
- **URL and publication/update date:**
- **Date accessed:**
- **Source quality:** Primary / standard / peer-reviewed / practitioner / secondary
- **Findings supported by source:**
- **FYNX-specific interpretation (label inference clearly):**
- **Options compared / calculations:**
- **Decision-log link:**
- **Implementation and verification link:**
- **Open questions / revisit date:**


## Research applied to the 2026-11-22 Home and Chat/Groups runtime repair

| Topic | Exact source | Quality / type | Principle applied to FYNX | URL |
|---|---|---|---|---|
| Compose accessibility semantics | Android Developers, “Semantics — Jetpack Compose” (accessed 2026-11-22) | Primary platform documentation | Use explicit semantic properties on the actual clickable control so UI automation and accessibility services identify the intended action, not adjacent descriptive text. | https://developer.android.com/develop/ui/compose/accessibility/semantics |
| Offline-first local/network reconciliation | Android Developers, “Build an offline-first app” (accessed 2026-11-22) | Primary platform architecture guidance | Keep local reads usable without connectivity, persist critical offline writes, and reconcile local state with authoritative network state without claiming delivery prematurely. | https://developer.android.com/topic/architecture/data-layer/offline-first |
