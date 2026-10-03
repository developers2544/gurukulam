// Google Play "Real-time developer notifications" (Pub/Sub push). Refund / cancel / renew par plan apne-aap sahi hota hai.
// Deploy: supabase functions deploy rtdn --no-verify-jwt   (Pub/Sub ke paas JWT nahi hota, isliye secret key se check)
import { admin } from "../_shared/common.ts";
import { syncPurchase } from "../_shared/play.ts";

Deno.serve(async (req) => {
  const url = new URL(req.url);
  if (url.searchParams.get("key") !== Deno.env.get("RTDN_SECRET")) return new Response("forbidden", { status: 403 });
  try {
    const body = await req.json();
    const raw = body?.message?.data;
    if (!raw) return new Response("ok");
    const msg = JSON.parse(atob(raw));
    const voided = msg.voidedPurchaseNotification?.purchaseToken as string | undefined;
    if (voided) {
      // Refund: premium turant band
      await admin.from("entitlements")
        .update({ expires_at: new Date().toISOString(), state: "VOIDED", updated_at: new Date().toISOString() })
        .eq("purchase_token", voided);
    }
    const token = msg.subscriptionNotification?.purchaseToken as string | undefined;
    if (token) await syncPurchase(token);
  } catch (e) {
    console.error("rtdn", e); // 200 hi lautate hain, warna Pub/Sub baar-baar retry karta hai
  }
  return new Response("ok");
});
