-- =====================================================================
-- GuruKulam - Supabase schema (Postgres) : tables + security (RLS) + functions + seed
-- Supabase Dashboard > SQL Editor mein poora file ek baar run karo.
-- Dobara run karna safe hai (create if not exists / or replace).
-- =====================================================================
create extension if not exists pgcrypto;

-- ---------- TABLES ----------
create table if not exists public.profiles (
  id uuid primary key references auth.users(id) on delete cascade,
  display_name text,
  plan_shown boolean not null default false,
  is_admin boolean not null default false,
  banned boolean not null default false,
  notify_hour int not null default 8 check (notify_hour between 0 and 23),
  lang text not null default 'hi',
  created_at timestamptz not null default now()
);

create table if not exists public.wallets (
  user_id uuid primary key references auth.users(id) on delete cascade,
  super_chats int not null default 0 check (super_chats >= 0),
  frames int not null default 0 check (frames >= 0)
);

create table if not exists public.entitlements (
  user_id uuid primary key references auth.users(id) on delete cascade,
  plan_id text,
  expires_at timestamptz,
  auto_renew boolean not null default false,
  purchase_token text,
  state text,
  updated_at timestamptz not null default now()
);
create index if not exists entitlements_token_idx on public.entitlements(purchase_token);

-- Ek order par credits sirf ek baar milte hain (double credit nahi)
create table if not exists public.credit_grants (
  order_id text primary key,
  user_id uuid not null references auth.users(id) on delete cascade,
  super_chats int not null,
  frames int not null,
  created_at timestamptz not null default now()
);

create table if not exists public.plans (
  id text primary key,
  title text not null,
  subtitle text,
  price_inr int not null,
  duration_days int not null,
  product_id text not null,          -- Play Console subscription ID
  base_plan_prepaid text not null,   -- manual (ek baar) base plan ID
  base_plan_auto text not null,      -- autopay base plan ID
  super_chats int not null default 0,
  frames int not null default 0,
  video_url text,                    -- plan card ke upar ki instruction video
  benefits jsonb not null default '[]'::jsonb,  -- [{"title":"..","text":"..","image_url":".."}]
  sort int not null default 0,
  active boolean not null default true
);

create table if not exists public.content (
  id uuid primary key default gen_random_uuid(),
  kind text not null,                -- music | story | movie | status | study | wallpaper | ringtone | calendar
  title text not null,
  subtitle text,
  thumb_url text,
  media_key text,                    -- R2 file ka path (ya poora https link)
  media_type text not null default 'video' check (media_type in ('video','audio','image')),
  is_premium boolean not null default false,
  is_ai boolean not null default false,
  section text,
  sort int not null default 0,
  published boolean not null default false,
  likes int not null default 0,
  created_at timestamptz not null default now()
);
create index if not exists content_kind_idx on public.content(kind, published, sort);

create table if not exists public.content_likes (
  user_id uuid not null references auth.users(id) on delete cascade,
  content_id uuid not null references public.content(id) on delete cascade,
  primary key (user_id, content_id)
);

create table if not exists public.banners (
  id uuid primary key default gen_random_uuid(),
  title text,
  image_url text,
  target_type text,                  -- content | list | plans
  target_id text,
  sort int not null default 0,
  active boolean not null default true
);

create table if not exists public.grid_tiles (
  key text primary key,
  label_en text not null,
  label_hi text not null,
  icon text not null default 'star',
  color_from text not null default '#FF4B55',
  color_to text not null default '#F9A03F',
  route text not null default '',
  sort int not null default 0,
  active boolean not null default true
);

-- DHYAN: app_config mein koi secret mat rakhna. Logged-in har user ise padh sakta hai.
create table if not exists public.app_config (
  key text primary key,
  value jsonb not null
);

create table if not exists public.chat_messages (
  id bigint generated always as identity primary key,
  user_id uuid references auth.users(id) on delete cascade,
  room text not null check (room in ('child','world')),
  body text not null,
  kind text not null default 'normal' check (kind in ('normal','super','admin')),
  visibility text not null default 'public' check (visibility in ('public','admin')),
  author_name text,
  deleted boolean not null default false,
  created_at timestamptz not null default now()
);
create index if not exists chat_room_idx on public.chat_messages(room, id desc);

create table if not exists public.chat_reports (
  id bigint generated always as identity primary key,
  message_id bigint references public.chat_messages(id) on delete cascade,
  user_id uuid,
  reason text,
  created_at timestamptz not null default now()
);

create table if not exists public.polls (
  id bigint generated always as identity primary key,
  question text not null,
  active boolean not null default true,
  ends_at timestamptz,
  created_at timestamptz not null default now()
);
create table if not exists public.poll_options (
  id bigint generated always as identity primary key,
  poll_id bigint not null references public.polls(id) on delete cascade,
  label text not null,
  votes int not null default 0
);
create table if not exists public.poll_votes (
  poll_id bigint not null references public.polls(id) on delete cascade,
  user_id uuid not null references auth.users(id) on delete cascade,
  option_id bigint not null references public.poll_options(id) on delete cascade,
  primary key (poll_id, user_id)
);

create table if not exists public.fcm_tokens (
  token text primary key,
  user_id uuid not null references auth.users(id) on delete cascade,
  notify_hour int not null default 8,
  updated_at timestamptz not null default now()
);

-- ---------- NEW USER TRIGGER ----------
create or replace function public.handle_new_user() returns trigger
language plpgsql security definer set search_path = public as $$
begin
  insert into public.profiles(id, display_name)
  values (new.id, coalesce(new.raw_user_meta_data->>'full_name', split_part(new.email, '@', 1)))
  on conflict (id) do nothing;
  insert into public.wallets(user_id) values (new.id) on conflict (user_id) do nothing;
  return new;
end $$;

drop trigger if exists on_auth_user_created on auth.users;
create trigger on_auth_user_created after insert on auth.users
  for each row execute function public.handle_new_user();

-- ---------- HELPER FUNCTIONS ----------
create or replace function public.is_admin() returns boolean
language sql stable security definer set search_path = public as $$
  select coalesce((select is_admin from public.profiles where id = auth.uid()), false)
$$;

create or replace function public.has_premium(uid uuid) returns boolean
language sql stable security definer set search_path = public as $$
  select exists (select 1 from public.entitlements where user_id = uid and expires_at > now())
$$;

-- Chat Saturday (IST) ko khulti hai. app_config.chat_mode = "open" / "closed" / "auto" se admin override kar sakta hai.
create or replace function public.chat_is_open() returns boolean
language sql stable security definer set search_path = public as $$
  select case coalesce((select value #>> '{}' from public.app_config where key = 'chat_mode'), 'auto')
    when 'open' then true
    when 'closed' then false
    else extract(dow from (now() at time zone 'Asia/Kolkata')) = 6
  end
$$;

create or replace function public._chat_guard(p_room text, p_body text) returns text
language plpgsql security definer set search_path = public as $$
declare v_name text; v_banned boolean; v_last timestamptz;
begin
  if auth.uid() is null then raise exception 'not_authenticated'; end if;
  if not public.chat_is_open() then raise exception 'chat_closed'; end if;
  if p_room not in ('child','world') then raise exception 'bad_room'; end if;
  if char_length(trim(coalesce(p_body, ''))) not between 1 and 400 then raise exception 'bad_body'; end if;
  select display_name, banned into v_name, v_banned from public.profiles where id = auth.uid();
  if coalesce(v_banned, false) then raise exception 'banned'; end if;
  select max(created_at) into v_last from public.chat_messages where user_id = auth.uid();
  if v_last is not null and v_last > now() - interval '3 seconds' then raise exception 'slow_down'; end if;
  return coalesce(v_name, 'Guest');
end $$;

create or replace function public.send_chat(p_room text, p_body text, p_visibility text default 'public') returns bigint
language plpgsql security definer set search_path = public as $$
declare v_name text; v_id bigint;
        v_vis text := case when p_room = 'child' and p_visibility = 'admin' then 'admin' else 'public' end;
begin
  v_name := public._chat_guard(p_room, p_body);
  insert into public.chat_messages(user_id, room, body, kind, visibility, author_name)
  values (auth.uid(), p_room, trim(p_body), 'normal', v_vis, v_name) returning id into v_id;
  return v_id;
end $$;

-- Super chat: credit kaatna aur message likhna EK transaction mein (double-tap par bhi credit minus nahi hoga)
create or replace function public.send_super_chat(p_room text, p_body text) returns bigint
language plpgsql security definer set search_path = public as $$
declare v_name text; v_id bigint;
begin
  v_name := public._chat_guard(p_room, p_body);
  update public.wallets set super_chats = super_chats - 1 where user_id = auth.uid() and super_chats > 0;
  if not found then raise exception 'no_credits'; end if;
  insert into public.chat_messages(user_id, room, body, kind, visibility, author_name)
  values (auth.uid(), p_room, trim(p_body), 'super', 'public', v_name) returning id into v_id;
  return v_id;
end $$;

create or replace function public.report_message(p_id bigint, p_reason text default 'user_report') returns void
language plpgsql security definer set search_path = public as $$
begin
  if auth.uid() is null then raise exception 'not_authenticated'; end if;
  insert into public.chat_reports(message_id, user_id, reason) values (p_id, auth.uid(), left(p_reason, 100));
end $$;

create or replace function public.toggle_like(p_content uuid) returns jsonb
language plpgsql security definer set search_path = public as $$
declare v_liked boolean; v_count int;
begin
  if auth.uid() is null then raise exception 'not_authenticated'; end if;
  if exists (select 1 from public.content_likes where user_id = auth.uid() and content_id = p_content) then
    delete from public.content_likes where user_id = auth.uid() and content_id = p_content;
    update public.content set likes = greatest(likes - 1, 0) where id = p_content;
    v_liked := false;
  else
    insert into public.content_likes(user_id, content_id) values (auth.uid(), p_content);
    update public.content set likes = likes + 1 where id = p_content;
    v_liked := true;
  end if;
  select likes into v_count from public.content where id = p_content;
  return jsonb_build_object('count', coalesce(v_count, 0), 'liked', v_liked);
end $$;

create or replace function public.vote_poll(p_option bigint) returns void
language plpgsql security definer set search_path = public as $$
declare v_poll bigint;
begin
  if auth.uid() is null then raise exception 'not_authenticated'; end if;
  select o.poll_id into v_poll from public.poll_options o join public.polls p on p.id = o.poll_id
   where o.id = p_option and p.active and (p.ends_at is null or p.ends_at > now());
  if v_poll is null then raise exception 'poll_closed'; end if;
  insert into public.poll_votes(poll_id, user_id, option_id) values (v_poll, auth.uid(), p_option);
  update public.poll_options set votes = votes + 1 where id = p_option;
exception when unique_violation then
  raise exception 'already_voted';
end $$;

create or replace function public.mark_plan_shown() returns void
language sql security definer set search_path = public as $$
  update public.profiles set plan_shown = true where id = auth.uid();
$$;

create or replace function public.update_profile(p_lang text default null, p_hour int default null) returns void
language plpgsql security definer set search_path = public as $$
begin
  update public.profiles set lang = coalesce(p_lang, lang), notify_hour = coalesce(p_hour, notify_hour) where id = auth.uid();
  if p_hour is not null then
    update public.fcm_tokens set notify_hour = p_hour where user_id = auth.uid();
  end if;
end $$;

create or replace function public.save_fcm_token(p_token text, p_hour int default 8) returns void
language plpgsql security definer set search_path = public as $$
begin
  if auth.uid() is null then raise exception 'not_authenticated'; end if;
  insert into public.fcm_tokens(token, user_id, notify_hour) values (p_token, auth.uid(), p_hour)
  on conflict (token) do update set user_id = auth.uid(), notify_hour = excluded.notify_hour, updated_at = now();
end $$;

-- Play Store ki zaroorat: app ke andar se account delete
create or replace function public.delete_my_account() returns void
language plpgsql security definer set search_path = public as $$
begin
  if auth.uid() is null then raise exception 'not_authenticated'; end if;
  delete from auth.users where id = auth.uid();
end $$;

-- Sirf server (Edge Function) chala sakta hai
create or replace function public.grant_credits(p_order text, p_user uuid, p_sc int, p_fr int) returns boolean
language plpgsql security definer set search_path = public as $$
declare v_rows int;
begin
  insert into public.credit_grants(order_id, user_id, super_chats, frames) values (p_order, p_user, p_sc, p_fr)
  on conflict (order_id) do nothing;
  get diagnostics v_rows = row_count;
  if v_rows = 0 then return false; end if;
  insert into public.wallets(user_id, super_chats, frames) values (p_user, p_sc, p_fr)
  on conflict (user_id) do update set super_chats = public.wallets.super_chats + p_sc, frames = public.wallets.frames + p_fr;
  return true;
end $$;

-- ---------- FUNCTION PERMISSIONS ----------
revoke execute on all functions in schema public from public, anon, authenticated;
grant execute on function
  public.chat_is_open(), public.send_chat(text, text, text), public.send_super_chat(text, text),
  public.report_message(bigint, text), public.toggle_like(uuid), public.vote_poll(bigint),
  public.mark_plan_shown(), public.update_profile(text, int), public.save_fcm_token(text, int),
  public.delete_my_account(), public.is_admin()
to authenticated;
grant execute on function public.has_premium(uuid), public.grant_credits(text, uuid, int, int) to service_role;

-- ---------- ROW LEVEL SECURITY ----------
alter table public.profiles       enable row level security;
alter table public.wallets        enable row level security;
alter table public.entitlements   enable row level security;
alter table public.credit_grants  enable row level security;
alter table public.plans          enable row level security;
alter table public.content        enable row level security;
alter table public.content_likes  enable row level security;
alter table public.banners        enable row level security;
alter table public.grid_tiles     enable row level security;
alter table public.app_config     enable row level security;
alter table public.chat_messages  enable row level security;
alter table public.chat_reports   enable row level security;
alter table public.polls          enable row level security;
alter table public.poll_options   enable row level security;
alter table public.poll_votes     enable row level security;
alter table public.fcm_tokens     enable row level security;

-- User sirf apna data padh sakta hai. Plan/wallet/entitlement mein app kuch likh nahi sakta (sirf server).
drop policy if exists p_profiles_read on public.profiles;
create policy p_profiles_read on public.profiles for select to authenticated using (id = auth.uid() or public.is_admin());
drop policy if exists p_profiles_admin on public.profiles;
create policy p_profiles_admin on public.profiles for all to authenticated using (public.is_admin()) with check (public.is_admin());

drop policy if exists p_wallets_read on public.wallets;
create policy p_wallets_read on public.wallets for select to authenticated using (user_id = auth.uid() or public.is_admin());

drop policy if exists p_ent_read on public.entitlements;
create policy p_ent_read on public.entitlements for select to authenticated using (user_id = auth.uid() or public.is_admin());

drop policy if exists p_grants_admin on public.credit_grants;
create policy p_grants_admin on public.credit_grants for select to authenticated using (public.is_admin());

drop policy if exists p_plans_read on public.plans;
create policy p_plans_read on public.plans for select to authenticated using (active or public.is_admin());
drop policy if exists p_plans_admin on public.plans;
create policy p_plans_admin on public.plans for all to authenticated using (public.is_admin()) with check (public.is_admin());

drop policy if exists p_content_read on public.content;
create policy p_content_read on public.content for select to authenticated using (published or public.is_admin());
drop policy if exists p_content_admin on public.content;
create policy p_content_admin on public.content for all to authenticated using (public.is_admin()) with check (public.is_admin());

drop policy if exists p_likes_read on public.content_likes;
create policy p_likes_read on public.content_likes for select to authenticated using (user_id = auth.uid());

drop policy if exists p_banners_read on public.banners;
create policy p_banners_read on public.banners for select to authenticated using (active or public.is_admin());
drop policy if exists p_banners_admin on public.banners;
create policy p_banners_admin on public.banners for all to authenticated using (public.is_admin()) with check (public.is_admin());

drop policy if exists p_tiles_read on public.grid_tiles;
create policy p_tiles_read on public.grid_tiles for select to authenticated using (active or public.is_admin());
drop policy if exists p_tiles_admin on public.grid_tiles;
create policy p_tiles_admin on public.grid_tiles for all to authenticated using (public.is_admin()) with check (public.is_admin());

drop policy if exists p_config_read on public.app_config;
create policy p_config_read on public.app_config for select to authenticated using (true);
drop policy if exists p_config_admin on public.app_config;
create policy p_config_admin on public.app_config for all to authenticated using (public.is_admin()) with check (public.is_admin());

drop policy if exists p_chat_read on public.chat_messages;
create policy p_chat_read on public.chat_messages for select to authenticated
  using (public.is_admin() or (not deleted and (visibility = 'public' or user_id = auth.uid())));
drop policy if exists p_chat_admin on public.chat_messages;
create policy p_chat_admin on public.chat_messages for all to authenticated using (public.is_admin()) with check (public.is_admin());

drop policy if exists p_reports_admin on public.chat_reports;
create policy p_reports_admin on public.chat_reports for select to authenticated using (public.is_admin());

drop policy if exists p_polls_read on public.polls;
create policy p_polls_read on public.polls for select to authenticated using (active or public.is_admin());
drop policy if exists p_polls_admin on public.polls;
create policy p_polls_admin on public.polls for all to authenticated using (public.is_admin()) with check (public.is_admin());
drop policy if exists p_opts_read on public.poll_options;
create policy p_opts_read on public.poll_options for select to authenticated using (true);
drop policy if exists p_opts_admin on public.poll_options;
create policy p_opts_admin on public.poll_options for all to authenticated using (public.is_admin()) with check (public.is_admin());

drop policy if exists p_fcm_admin on public.fcm_tokens;
create policy p_fcm_admin on public.fcm_tokens for select to authenticated using (public.is_admin());

-- ---------- SEED DATA ----------
insert into public.grid_tiles(key, label_en, label_hi, icon, color_from, color_to, route, sort) values
 ('movies','Movies','मूवीज़','film','#7A1E2A','#C23A3A','list/movie',1),
 ('stories','Kahaniyan','कहानियाँ','book','#8A4B10','#D98A2B','list/story',2),
 ('music','Music','म्यूज़िक','music','#4A2A7A','#8A4BC2','music',3),
 ('status','Status','स्टेटस','status','#14683A','#2BB673','feed/0',4),
 ('others','Others','अन्य','wall','#1F4E8A','#3F8AD9','others',5),
 ('study','Study','स्टडी','study','#7A3A10','#D9672B','list/study',6)
on conflict (key) do nothing;

insert into public.plans(id, title, subtitle, price_inr, duration_days, product_id, base_plan_prepaid, base_plan_auto, super_chats, frames, sort) values
 ('ram','Shree Ram Plan','1 mahina • poora app unlock',301,30,'shree_ram','ram-prepaid','ram-auto',5,5,1),
 ('krishna','Shree Krishna Plan','1 saal • poora app unlock',3001,365,'shree_krishna','krishna-prepaid','krishna-auto',60,5,2)
on conflict (id) do nothing;

insert into public.app_config(key, value) values
 ('chat_mode', '"auto"'),
 ('min_version_code', '1'),
 ('push_user', '{"title":"GuruKulam","body":"Aaj ki nayi kahani aur bhajan aapka intezaar kar rahe hain."}'),
 ('push_evening', '{"title":"GuruKulam","body":"Shaam ki aarti aur nayi kahani sunein."}')
on conflict (key) do nothing;

-- ---------- ADMIN BANANA (apna Gmail daalo, pehle ek baar app mein login kar lo) ----------
-- update public.profiles set is_admin = true where id = (select id from auth.users where email = 'YOU@gmail.com');

-- ---------- DAILY PUSH (hourly) : Dashboard > Database > Extensions mein pg_cron aur pg_net ON karo ----------
-- select cron.schedule('gk-push-hourly', '0 * * * *', $$
--   select net.http_post(
--     url := 'https://YOUR-PROJECT.supabase.co/functions/v1/send-push',
--     headers := '{"Content-Type":"application/json","x-cron-secret":"YOUR_CRON_SECRET"}'::jsonb,
--     body := '{}'::jsonb);
-- $$);
