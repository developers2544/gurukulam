// Admin panel se R2 mein seedha upload ke liye 15 minute ka link. Sirf admin user.
import { AwsClient } from "npm:aws4fetch@1.0.20";
import { isAdminUser, json, preflight, userFrom } from "../_shared/common.ts";

const ACCOUNT = Deno.env.get("R2_ACCOUNT_ID")!;
const PUBLIC_BUCKET = Deno.env.get("R2_PUBLIC_BUCKET") ?? "gurukulam-public";
const PREMIUM_BUCKET = Deno.env.get("R2_PREMIUM_BUCKET") ?? "gurukulam-premium";
const CDN_BASE = (Deno.env.get("CDN_BASE") ?? "").replace(/\/$/, "");
const r2 = new AwsClient({
  accessKeyId: Deno.env.get("R2_ACCESS_KEY_ID")!,
  secretAccessKey: Deno.env.get("R2_SECRET_ACCESS_KEY")!,
  service: "s3",
  region: "auto",
});
const ALLOWED = /\.(mp4|webp|jpg|jpeg|png|mp3|m4a)$/i;

Deno.serve(async (req) => {
  const pf = preflight(req);
  if (pf) return pf;
  try {
    const user = await userFrom(req);
    if (!user || !(await isAdminUser(user.id))) return json({ error: "forbidden" }, 403);
    const { bucket, key } = await req.json();
    if (typeof key !== "string" || !/^[a-zA-Z0-9_\-\/\.]{3,200}$/.test(key) || key.includes("..") || !ALLOWED.test(key)) {
      return json({ error: "bad_key" }, 400);
    }
    const name = bucket === "premium" ? PREMIUM_BUCKET : PUBLIC_BUCKET;
    const u = new URL(`https://${ACCOUNT}.r2.cloudflarestorage.com/${name}/${key}`);
    u.searchParams.set("X-Amz-Expires", "900");
    const signed = await r2.sign(new Request(u, { method: "PUT" }), { aws: { signQuery: true } });
    return json({ uploadUrl: signed.url, key, publicUrl: bucket === "premium" ? null : `${CDN_BASE}/${key}` });
  } catch (e) {
    console.error("admin-sign-upload", e);
    return json({ error: (e as Error).message }, 400);
  }
});
