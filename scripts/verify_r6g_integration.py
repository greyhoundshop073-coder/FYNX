from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
backend = (ROOT / "backend/r6gIntegrationRoutes.js").read_text()
scalability = (ROOT / "backend/scalability.js").read_text()
client = (ROOT / "app/src/main/java/com/fynx/app/ui/FynxR6GIntegrationClient.kt").read_text()
business_panel = (ROOT / "app/src/main/java/com/fynx/app/ui/FynxBusinessAccount.kt").read_text()
share = (ROOT / "app/src/main/java/com/fynx/app/ui/FynxShare.kt").read_text()
group = (ROOT / "backend/groupContentRoutes.js").read_text()
marketplace_remote = (ROOT / "app/src/main/java/com/fynx/app/ui/FynxMarketplaceRemotePanel.kt").read_text()
conversation = (ROOT / "app/src/main/java/com/fynx/app/ui/ConversationPanel.kt").read_text()
messaging = (ROOT / "app/src/main/java/com/fynx/app/ui/FynxProductionMessaging.kt").read_text()

checks = [
    ("R6-G route module exists", "export function registerR6GIntegrationRoutes" in backend),
    ("R6-G routes are production-wired", "registerR6GIntegrationRoutes({ app });" in scalability),
    ("canonical listing gets business ownership", "marketplace_listings ADD COLUMN IF NOT EXISTS business_id" in backend),
    ("Home posts can reference canonical listing", "social_posts ADD COLUMN IF NOT EXISTS listing_id" in backend),
    ("Home posts can reference business identity", "social_posts ADD COLUMN IF NOT EXISTS business_id" in backend),
    ("messages can carry listing context", "messages ADD COLUMN IF NOT EXISTS listing_id" in backend),
    ("groups can carry canonical listing context", "fynx_group_posts ADD COLUMN IF NOT EXISTS listing_id" in backend),
    ("business link enforces owner", "business_profiles WHERE id=$1 AND owner_id=$2" in backend),
    ("Home product share uses listing_id", "INSERT INTO social_posts(author_id,text,visibility,listing_id,business_id)" in backend),
    ("group product share uses listing_id", "INSERT INTO fynx_group_posts(id,group_id,author_id,text,listing_id,business_id)" in backend),
    ("message context checks participant", "message access denied" in backend),
    ("message context validates listing identity", "message is not connected to this seller" in backend),
    ("message context never owns marketplace authority", "UPDATE messages SET listing_id=$1,business_id=$2" in backend and "UPDATE marketplace_listings" not in backend.split("app.post('/api/r6g/messages/:id/context'", 1)[1].split("app.post('/api/r6g/groups/:groupId/marketplace-posts'", 1)[0]),
    ("Android listing context uses a block body", "suspend fun listingContext(context: Context, listingId: String): Result<ListingContext> {" in client),
    ("Android listing context has no illegal expression-body return", "Result.failure(IllegalArgumentException(\"invalid listing id\"))" in client and "suspend fun listingContext(context: Context, listingId: String): Result<ListingContext> =" not in client),
    ("Android client exposes business linking", "suspend fun linkListingToBusiness" in client),
    ("Business Account loads canonical listing context", "FynxR6GIntegrationClient.listingContext(context, product.id)" in business_panel),
    ("Business Account exposes link/unlink action", "FynxR6GIntegrationClient.linkListingToBusiness" in business_panel and "else \"Link\"" in business_panel),
    ("Android client exposes Home share", "shareListingToHome" in client),
    ("Android client exposes message context", "attachListingToMessage" in client),
    ("Android client exposes group share", "shareListingToGroup" in client),
    ("Marketplace share payload preserves canonical listing id", "marketplaceListingId = listingId" in share),
    ("Marketplace share exposes FYNX Home action", "Post to FYNX Home" in share and "postMarketplaceToHome" in share),
    ("Marketplace Home action calls canonical integration client", "FynxR6GIntegrationClient.shareListingToHome(context, listingId)" in share),
    ("Marketplace share exposes FYNX Group action", "Share to FYNX Group" in share and "chooseGroupForMarketplaceShare" in share),
    ("Marketplace Group action loads real groups", "FynxGroupsStore.load(context)" in share),
    ("Marketplace Group action calls canonical integration client", "FynxR6GIntegrationClient.shareListingToGroup(context, group.id, listingId" in share),
    ("legacy group marketplace field remains for compatibility", "marketplace_product_id" in group),
    ("Marketplace contact opens normal FYNX Chat", "onContact = { onOpenChat(listing.sellerUsername) }" in marketplace_remote),
    ("Marketplace detail contact opens normal FYNX Chat", "onOpenChat(listing.sellerUsername); selected = null" in marketplace_remote),
    ("ConversationPanel remains a general chat surface", "fun ConversationPanel(chat: ChatPreview" in conversation and "FynxProductionMessaging.history" in conversation and "FynxProductionMessaging.sendText" in conversation),
    ("Chat send API remains marketplace-independent", "suspend fun sendText(context: Context, recipientUsername: String, text: String" in messaging and "listingId" not in messaging.split("suspend fun sendText", 1)[1].split("suspend fun", 1)[0]),
]

failed = [name for name, ok in checks if not ok]
for name, ok in checks:
    print(("PASS" if ok else "FAIL") + " - " + name)
if failed:
    raise SystemExit("R6-G integration gate failed: " + ", ".join(failed))
print(f"R6-G integration gate GREEN ({len(checks)} checks)")
