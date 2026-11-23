export async function resolveFynxMarketplaceCallContext(pool, callerId, targetUserId, rawListingId) {
  if (rawListingId == null || String(rawListingId).trim() === "") return null;
  const listingId = String(rawListingId).trim();
  if (!/^\d{1,19}$/.test(listingId) || /^0+$/.test(listingId)) {
    throw new Error("The selected Marketplace product is invalid.");
  }
  const result = await pool.query(
    `SELECT l.id::text AS "listingId",l.seller_id::text AS "sellerId",u.username AS "sellerUsername",
            l.title,l.active,l.quantity
     FROM marketplace_listings l
     JOIN users u ON u.id=l.seller_id
     WHERE l.id=$1 AND l.active=TRUE AND l.quantity>0
       AND COALESCE(l.reserved_quantity,0)<l.quantity
     LIMIT 1`,
    [listingId]
  );
  const listing = result.rows[0];
  if (!listing) throw new Error("This Marketplace product is no longer available.");
  if (String(listing.sellerId) !== String(targetUserId)) {
    throw new Error("The selected product does not belong to this seller.");
  }
  if (String(listing.sellerId) === String(callerId)) {
    throw new Error("You cannot start a seller inspection call for your own product.");
  }
  const blocked = await pool.query(
    "SELECT 1 FROM blocks WHERE (blocker_id=$1 AND blocked_id=$2) OR (blocker_id=$2 AND blocked_id=$1) LIMIT 1",
    [String(callerId), String(listing.sellerId)]
  );
  if (blocked.rowCount) throw new Error("This Marketplace call is unavailable.");
  return {
    listingId: String(listing.listingId),
    productTitle: String(listing.title || "").trim().slice(0, 180),
    sellerUsername: String(listing.sellerUsername || "").trim()
  };
}
