import assert from "node:assert/strict";
import { resolveFynxMarketplaceCallContext } from "./marketplaceCallContext.js";

function makePool({ listing = null, blocked = false } = {}) {
  const queries = [];
  return {
    queries,
    async query(sql, params) {
      queries.push({ sql, params });
      if (sql.includes("FROM marketplace_listings")) return { rows: listing ? [listing] : [] };
      if (sql.includes("FROM blocks")) return { rowCount: blocked ? 1 : 0 };
      throw new Error("Unexpected Marketplace proof query: " + sql);
    }
  };
}

const validListing = {
  listingId: "8801",
  sellerId: "202",
  sellerUsername: "fynx_seller",
  title: "Blue phone",
  active: true,
  quantity: 4
};

{
  const pool = makePool({ listing: validListing });
  assert.deepEqual(
    await resolveFynxMarketplaceCallContext(pool, "101", "202", "8801"),
    { listingId: "8801", productTitle: "Blue phone", sellerUsername: "fynx_seller" }
  );
  assert.deepEqual(pool.queries.map(x => x.params), [["8801"], ["101", "202"]]);
}

{
  const pool = makePool({ listing: validListing });
  await assert.rejects(() => resolveFynxMarketplaceCallContext(pool, "101", "303", "8801"), /does not belong to this seller/);
  assert.equal(pool.queries.length, 1, "wrong-seller requests must be rejected before block lookup");
}

{
  const pool = makePool({ listing: validListing, blocked: true });
  await assert.rejects(() => resolveFynxMarketplaceCallContext(pool, "101", "202", "8801"), /unavailable/);
}

{
  const pool = makePool();
  await assert.rejects(() => resolveFynxMarketplaceCallContext(pool, "101", "202", "8801"), /no longer available/);
}

{
  const pool = makePool();
  await assert.rejects(() => resolveFynxMarketplaceCallContext(pool, "101", "202", "0; DROP TABLE users"), /invalid/);
  assert.equal(pool.queries.length, 0, "invalid listing IDs must never reach SQL");
}

{
  const pool = makePool({ listing: validListing });
  await assert.rejects(() => resolveFynxMarketplaceCallContext(pool, "202", "202", "8801"), /own product/);
  assert.equal(pool.queries.length, 1);
}

console.log("Marketplace Live Proof authorization tests passed (valid listing, seller match, availability, bilateral block, self-call, invalid ID).");
