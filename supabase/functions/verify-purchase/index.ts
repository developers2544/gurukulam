import { json, preflight, userFrom } from "../_shared/common.ts";
import { syncPurchase } from "../_shared/play.ts";

Deno.serve(async (req) => {
  const pf = preflight(req);
  if (pf) return pf;
  try {
    const user = await userFrom(req);
    if (!user) return json({ error: "unauthorized" }, 401);
    const { purchaseToken } = await req.json();
    if (!purchaseToken || typeof purchaseToken !== "string") return json({ error: "bad_request" }, 400);
    const r = await syncPurchase(purchaseToken, user.id);
    return json({ ok: true, ...r });
  } catch (e) {
    console.error("verify-purchase", e);
    return json({ error: (e as Error).message }, 400);
  }
});

