# Full Runtime Certification Trigger

This marker exists only to trigger the existing authenticated full-runtime GitHub Actions path after source and CI investigation.

It does not change application code, navigation, UI behavior, backend behavior, test data, credentials, or production configuration.

The workflow already supports the [full-runtime] commit marker. This file lets the certification run exercise the real emulator, authenticated navigation, screenshots, UI hierarchy, accessibility checks, responsive captures, and Maestro journey against the exact current main commit.


This trigger follows the authenticated runtime harness fix and is used to re-run the real-account certification path.