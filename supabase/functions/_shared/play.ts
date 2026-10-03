import { admin } from "./common.ts";
import { googleToken } from "./google.ts";

const PKG = Deno.env.get("PLAY_PACKAGE_NAME") ?? "com.tech2544.gurukulam";
// CANCELED = user ne autopay band kiya, par expiry tak premium chalu rehta hai
const ACTIVE = new Set([
  "SUBSCRIPTION_STATE_ACTIVE",
  "SUBSCRIPTION_STATE_IN_GRACE_PERIOD",
  "SUBSCRIPTION_STATE_CANCELED",
]);

/**
 * Google se purchase verify -> acknowledge -> entitlement likhna -> credits dena.
 * Ye function idempotent hai: same token dobara aaye to kuch double nahi hota.
 * expectedUser dene par check hota hai ki token isi user ka hai (doosre ka token chura kar use nahi ho sakta).
 */
export async function syncPurchase(purchaseToken: string, expectedUser?: string) {
  const at = await googleToken("GOOGLE_SERVICE_ACCOUNT", "https://www.googleapis.com/auth/androidpublisher");
  const base = `https://androidpublisher.googleapis.com/androidpublisher/v3/applications/${PKG}`;
  const tok = encodeURIComponent(purchaseToken);

  const r = await fetch(`${base}/purchases/subscriptionsv2/tokens/${tok}`, { headers: { Authorization: `Bearer ${at}` } });
  if (!r.ok) throw new Error(`play_verify_${r.status}`);
  const p = await r.json();

  const uid = p.externalAccountIdentifiers?.obfuscatedExternalAccountId as string | undefined;
  if (!uid) throw new Error("no_account_id");
  if (expectedUser && uid !== expectedUser) throw new Error("token_owner_mismatch");

  const line = p.lineItems?.[0];
  if (!line) throw new Error("no_line_item");
  const productId = line.productId as string;
  const expiry = new Date(line.expiryTime);
  const active = ACTIVE.has(p.subscriptionState) && expiry.getTime() > Date.now();

  const { data: plan } = await admin.from("plans").select("*").eq("product_id", productId).maybeSingle();
  if (!plan) throw new Error("unknown_plan");

  // 3 din ke andar acknowledge zaroori hai, warna Google paisa wapas kar deta hai
  if (active && p.acknowledgementState === "ACKNOWLEDGEMENT_STATE_PENDING") {
    const a = await fetch(`${base}/purchases/subscriptions/${productId}/tokens/${tok}:acknowledge`, {
      method: "POST",
      headers: { Authorization: `Bearer ${at}`, "Content-Type": "application/json" },
      body: "{}",
    });
    if (!a.ok) console.error("acknowledge_failed", a.status, await a.text());
  }

  // Purana token agar zyada der tak valid hai to use mat mita do
  const { data: cur } = await admin.from("entitlements").select("*").eq("user_id", uid).maybeSingle();
  const keepCurrent = cur && cur.purchase_token !== purchaseToken && cur.expires_at && new Date(cur.expires_at) > expiry;
  if (!keepCurrent) {
    await admin.from("entitlements").upsert({
      user_id: uid,
      plan_id: plan.id,
      expires_at: expiry.toISOString(),
      auto_renew: !!line.autoRenewingPlan?.autoRenewEnabled,
      purchase_token: purchaseToken,
      state: p.subscriptionState,
      updated_at: new Date().toISOString(),
    });
  }

  // Credits (super chat / frames) har naye order par ek hi baar
  if (active && p.latestOrderId) {
    await admin.rpc("grant_credits", {
      p_order: p.latestOrderId, p_user: uid, p_sc: plan.super_chats, p_fr: plan.frames,
    });
  }
  return { active, expires_at: expiry.toISOString(), plan_id: plan.id };
}
