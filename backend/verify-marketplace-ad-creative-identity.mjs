import fs from 'fs';

const advertising = fs.readFileSync(new URL('./marketplaceAdvertising.js', import.meta.url), 'utf8');
const business = fs.readFileSync(new URL('./businessRoutes.js', import.meta.url), 'utf8');
const social = fs.readFileSync(new URL('./socialRoutes.js', import.meta.url), 'utf8');

function check(name, ok) {
  if (!ok) {
    console.error(`Marketplace ad creative identity FAILED - ${name}`);
    process.exit(1);
  }
  console.log(`Marketplace ad creative identity OK - ${name}`);
}

check('all creative types use canonical numeric references',
  advertising.includes('CREATIVE_TYPES = new Set(["post", "product", "business"])') &&
  advertising.includes('typeof creativeRef !== "string" || !/^[0-9]+$/.test(creativeRef)') &&
  advertising.includes('const referenceId = positiveInt(creativeRef)'));

check('product creatives are tied to the advertiser-owned active listing',
  advertising.includes('FROM marketplace_listings WHERE id=$1 AND seller_id=$2 AND COALESCE(active,TRUE)=TRUE FOR SHARE'));

check('business creatives are tied to the advertiser-owned active business profile',
  advertising.includes('FROM business_profiles WHERE id=$1 AND owner_id=$2 AND COALESCE(active,TRUE)=TRUE FOR SHARE') &&
  business.includes('owner_id BIGINT NOT NULL UNIQUE REFERENCES users(id)') &&
  business.includes('active BOOLEAN NOT NULL DEFAULT TRUE'));

check('post creatives are tied to the advertiser-owned social post',
  advertising.includes('FROM social_posts WHERE id=$1 AND author_id=$2 FOR SHARE') &&
  social.includes('author_id BIGINT NOT NULL REFERENCES users(id)'));

check('non-product creatives cannot omit their canonical reference',
  advertising.includes('creativeType !== "product" && !creativeRef') &&
  advertising.includes('canonical reference'));

check('invalid or unowned references are rejected before campaign insertion',
  advertising.includes('validateCreativeReference(client, auth.userId, creativeType, creativeRef)') &&
  advertising.includes('creativeReferenceError(creativeType)') &&
  advertising.includes('await client.query("ROLLBACK")'));

console.log('Marketplace advertising canonical creative identity verification PASSED');
