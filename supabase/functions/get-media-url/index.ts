import { AwsClient } from "npm:aws4fetch@1.0.20";
import { admin, isAdminUser, json, preflight, userFrom } from "../_shared/common.ts";

const ACCOUNT = Deno.env.get("R2_ACCOUNT_ID")!;
const PREMIUM_BUCKET = Deno.env.get("R2_PREMIUM_BUCKET") ?? "gurukulam-premium";
const CDN_BASE = (Deno.env.get("CDN_BASE") ?? "").replace(/\/$/, "");
const r2 = new AwsClient({
  accessKeyId: Deno.env.get("R2_ACCESS_KEY_ID")!,
  secretAccessKey: Deno.env.get("R2_SECRET_ACCESS_KEY")!,
  service: "s3",
  region: "auto",
});

Deno.serve(async (req) => {
  const pf = preflight(req);
  if (pf) return pf;
  try {
    const user = await userFrom(req);
    if (!user) return json({ error: "unauthorized" }, 401);
    const { content_id } = await req.json();

    const { data: c } = await admin.from("content")
      .select("media_key,is_premium,published").eq("id", content_id).maybeSingle();
    const admin_ = await isAdminUser(user.id);
    if (!c || !c.media_key || (!c.published && !admin_)) return json({ error: "not_found" }, 404);
    if (c.media_key.startsWith("http")) return json({ url: c.media_key });

    if (!c.is_premium) return json({ url: `${CDN_BASE}/${c.media_key}` });

    // Premium: plan chalu hai? (admin test ke liye allowed)
    const { data: ok } = await admin.rpc("has_premium", { uid: user.id });
    if (!ok && !admin_) return json({ error: "premium_required" }, 403);

    const path = c.media_key.split("/").map(encodeURIComponent).join("/");
    const u = new URL(`https://${ACCOUNT}.r2.cloudflarestorage.com/${PREMIUM_BUCKET}/${path}`);
    u.searchParams.set("X-Amz-Expires", "7200"); // 2 ghante
    const signed = await r2.sign(new Request(u, { method: "GET" }), { aws: { signQuery: true } });
    return json({ url: signed.url });
  } catch (e) {
    console.error("get-media-url", e);
    return json({ error: (e as Error).message }, 400);
  }
});
