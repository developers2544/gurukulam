// Har ghante chalao (pg_cron). Din mein max 2 notification: user ka chuna samay + shaam 7 baje (IST).
// Deploy: supabase functions deploy send-push --no-verify-jwt   (x-cron-secret se secure)
import { admin } from "../_shared/common.ts";
import { googleToken } from "../_shared/google.ts";

Deno.serve(async (req) => {
  if (req.headers.get("x-cron-secret") !== Deno.env.get("CRON_SECRET")) return new Response("forbidden", { status: 403 });
  try {
    const hour = new Date(Date.now() + 330 * 60_000).getUTCHours(); // IST ghanta
    const evening = hour === 19;

    // Jinka chuna hua samay abhi hai, +(shaam ko) sab. Ek token ko ek hi baar.
    const tokens = new Set<string>();
    const mine = await admin.from("fcm_tokens").select("token").eq("notify_hour", hour);
    mine.data?.forEach((r) => tokens.add(r.token));
    if (evening) {
      const all = await admin.from("fcm_tokens").select("token");
      all.data?.forEach((r) => tokens.add(r.token));
    }
    if (tokens.size === 0) return new Response(JSON.stringify({ sent: 0 }));

    const key = evening ? "push_evening" : "push_user";
    const { data: cfg } = await admin.from("app_config").select("value").eq("key", key).maybeSingle();
    const msg = (cfg?.value ?? { title: "GuruKulam", body: "Nayi kahani aur bhajan aapka intezaar kar rahe hain." }) as { title: string; body: string };

    const project = JSON.parse(Deno.env.get("FIREBASE_SERVICE_ACCOUNT")!).project_id;
    const at = await googleToken("FIREBASE_SERVICE_ACCOUNT", "https://www.googleapis.com/auth/firebase.messaging");
    const list = [...tokens];
    let sent = 0;
    for (let i = 0; i < list.length; i += 50) {
      await Promise.all(list.slice(i, i + 50).map(async (t) => {
        const r = await fetch(`https://fcm.googleapis.com/v1/projects/${project}/messages:send`, {
          method: "POST",
          headers: { Authorization: `Bearer ${at}`, "Content-Type": "application/json" },
          body: JSON.stringify({ message: { token: t, notification: { title: msg.title, body: msg.body } } }),
        });
        if (r.ok) sent++;
        else if (r.status === 404 || r.status === 400) await admin.from("fcm_tokens").delete().eq("token", t); // purana token
      }));
    }
    return new Response(JSON.stringify({ sent }));
  } catch (e) {
    console.error("send-push", e);
    return new Response("error", { status: 500 });
  }
});
