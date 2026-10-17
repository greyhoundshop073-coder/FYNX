# FYNX Marketplace Driver Custody

## Purpose

The driver workflow adds a controlled hand-off between a Marketplace seller and an approved delivery driver. It is intended to make clear who is responsible for an order at each stage and to leave an event/evidence trail for the buyer, seller, and FYNX administrators.

## How it works

1. **FYNX approves a driver.** The app's admin/owner area can list driver approvals and activate or revoke approval. An account without active approval cannot be assigned through the normal seller assignment route.
2. **The seller assigns the driver.** The seller opens the order and assigns an approved FYNX user by username or account ID. The buyer and seller cannot be selected as that order's driver.
3. **The driver accepts the job.** The assignment appears in the driver's own assignment queue. The queue is scoped to the signed-in driver. Buyer address/contact details are withheld until the driver accepts.
4. **The seller hands over the parcel.** The order must be shipped as a delivery order; the assigned driver must have accepted; approval must still be active; and an active protection case/dispute blocks the handover. The seller records a handover note.
5. **The driver records delivery.** After the handover, only the assigned, actively approved driver can report delivery. The driver must enter the recipient's name and a delivery note or a photo reference. These details are recorded as delivery evidence and in the order event history.
6. **The buyer confirms receipt.** The buyer confirms that the parcel arrived. The order then enters the existing 48-hour inspection period. To complete the order, the buyer must confirm the item and quantity match; active protection/dispute cases block completion.
7. **FYNX can intervene if approval changes.** Approval cannot be revoked while a driver still has an order in active custody. If approval is revoked before handover, the seller can reassign the order to another active approved driver. Reassignment is locked after physical handover.

## What each person does in the app

- **Buyer:** chooses delivery/pickup where offered, provides delivery details, follows the order timeline/evidence, confirms receipt, checks the item and quantity, and raises a protection case when needed.
- **Seller:** assigns an approved driver, ships the order, records the physical handover, and monitors the driver's acceptance/handover/delivery status.
- **Driver:** checks their assignment queue, accepts the delivery before seeing the address, collects the parcel from the seller, and records recipient/evidence details after delivery.
- **FYNX admin/owner:** approves or revokes driver access and can see the driver approval list.

## Important limits to understand

- The current implementation records a **photo reference string**; that is not proof that the app uploads, stores, or displays an actual photo file. Do not describe photo upload as complete until real media storage and APK testing confirm it.
- The current code does not by itself establish GPS tracking, live maps, courier-company integration, automatic driver payment, or delivery-fee settlement to drivers.
- Green CI and a built APK prove code/build checks, not a successful live order journey. End-to-end runtime validation still requires an available backend and test accounts for buyer, seller, approved driver, and admin.
- Render was reported unavailable/expired in the handover. Do not treat the live driver flow as operational until backend availability and the complete flow have been tested.
- These changes remain on the driver-custody feature branch and must not be merged into `main` without explicit approval.

## Verification gate before Batch 4

The critical scenarios to verify behaviorally are:

- Unapproved or revoked drivers cannot accept jobs or obtain buyer address/contact details.
- A driver cannot record delivery before seller handover.
- Approval revocation and physical handover cannot race into an unsafe custody state.
- Reassignment is allowed after pre-handover revocation but blocked after handover.
- The buyer cannot complete the order before driver evidence, receipt confirmation, and item/quantity confirmation.


## Seller-local phone driver mode

A second delivery mode is supported alongside approved FYNX-account drivers. The seller can record a local driver's name and phone number, an agreed bus-stop/roadside pickup point, optional instructions, and optional vehicle type/colour/plate/note. The local driver does not need a FYNX account or app. The seller records physical handover; after the driver calls the buyer at the agreed point, the seller may report arrival. That report is not buyer receipt: the buyer must still confirm collection, and the existing inspection/completion protections remain in force.

Vehicle photo upload is not included in this change because a real photo requires media upload/storage and display integration; the optional vehicle note is supported instead. The pickup point is stored separately from the buyer's delivery address. Assignment details are exposed by the delivery-details endpoint only to the order buyer or seller. Reassignment remains locked after physical handover.
