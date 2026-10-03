import { createClient } from "npm:@supabase/supabase-js@2";

export const SB_URL = Deno.env.get("SUPABASE_URL")!;
export const SERVICE_KEY = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!;

// Service-role client: sirf server ke andar. App/admin page ko ye key kabhi nahi milti.
export const admin = createClient(SB_URL, SERVICE_KEY, { auth: { persistSession: false } });

export const cors = {
  "Access-Control-Allow-Origin": Deno.env.get("ADMIN_ORIGIN") ?? "*",
  "Access-Control-Allow-Headers": "authorization, x-client-info, apikey, content-type",
  "Access-Control-Allow-Methods": "POST, OPTIONS",
};

export function json(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { "Content-Type": "application/json", ...cors },
  });
}

export function preflight(req: Request): Response | null {
  return req.method === "OPTIONS" ? new Response("ok", { headers: cors }) : null;
}

/** Request ke token se asli user nikalta hai (Auth server verify karta hai). */
export async function userFrom(req: Request) {
  const token = (req.headers.get("Authorization") ?? "").replace("Bearer ", "").trim();
  if (!token) return null;
  const { data, error } = await admin.auth.getUser(token);
  if (error || !data.user) return null;
  return data.user;
}

export async function isAdminUser(uid: string): Promise<boolean> {
  const { data } = await admin.from("profiles").select("is_admin").eq("id", uid).maybeSingle();
  return !!data?.is_admin;
}
