# GuruKulam – Android app (Kotlin + Jetpack Compose)

Ye zip mein poora Android Studio project, Supabase backend (SQL + Edge Functions) aur Admin panel hai.

> **Sach baat:** ye code maine yahan compile/run nahi kiya (yahan Android SDK nahi hota). Syntax check kiya hai, par pehli baar Gradle sync par 1-2 chhoti galti aa sakti hai. Error ka text mujhe paste karo, turant theek kar dunga.

## Folder
```
app/                 Android app (Kotlin/Compose)
supabase/schema.sql  Database + security (RLS) + functions + seed data
supabase/functions/  Edge Functions: verify-purchase, get-media-url, rtdn, admin-sign-upload, send-push
admin/index.html     Admin panel (web page)
docs/r2-cors.json    R2 bucket CORS
```

## Setup (is order mein)

### 1. Supabase
1. supabase.com par naya project banao. Launch se pehle **Pro plan** lo (free plan 1 hafte idle rehne par pause ho jata hai).
2. SQL Editor mein `supabase/schema.sql` poora paste karke Run karo.
3. Authentication > Providers > **Google** ON karo. Client ID = neeche wala *Web client ID*.
4. Authentication > Users > "Add user": apna email + password (admin login ke liye). Phir schema.sql ke neeche wali `update ... is_admin = true` line apne email ke saath chalao.
   (Android app mein pehle ek baar Google se login kar lo, tab bhi chalega.)
5. Settings > API se **Project URL** aur **anon key** copy karo.

### 2. Google Cloud (login)
1. console.cloud.google.com > naya project > OAuth consent screen set karo.
2. Credentials > **Web application** client banao. Iska Client ID Supabase mein aur app ki `local.properties` mein jata hai.
3. Credentials > **Android** client banao: package `com.tech2544.gurukulam` + **SHA-1**. SHA-1 teen jagah ke daalo (sab ke alag Android client):
   debug key, release key, aur **Play Console > App signing > App signing key certificate** (Play se aaya hua). Yahi sabse common login bug hai.

### 3. Cloudflare R2 (video/audio/photo)
1. R2 mein 2 bucket: `gurukulam-public` aur `gurukulam-premium`.
2. `gurukulam-public` par custom domain jodo (jaise `cdn.tumhara-domain.com`). `gurukulam-premium` ko **private** hi rehne do.
3. R2 > Manage API tokens: Object Read & Write token banao. Account ID, Access Key, Secret Key note karo.
4. Dono bucket par `docs/r2-cors.json` laga do (admin domain apna daalo).

### 4. Edge Functions
```bash
npm i -g supabase
supabase login
supabase link --project-ref YOUR_REF
supabase secrets set \
  R2_ACCOUNT_ID=... R2_ACCESS_KEY_ID=... R2_SECRET_ACCESS_KEY=... \
  R2_PUBLIC_BUCKET=gurukulam-public R2_PREMIUM_BUCKET=gurukulam-premium \
  CDN_BASE=https://cdn.tumhara-domain.com \
  PLAY_PACKAGE_NAME=com.tech2544.gurukulam \
  RTDN_SECRET=koi-lamba-random-text CRON_SECRET=koi-aur-lamba-random-text \
  ADMIN_ORIGIN=https://tumhara-admin.pages.dev
supabase secrets set GOOGLE_SERVICE_ACCOUNT="$(cat play-service-account.json)"
supabase secrets set FIREBASE_SERVICE_ACCOUNT="$(cat firebase-service-account.json)"   # push ke liye (optional)

supabase functions deploy verify-purchase
supabase functions deploy get-media-url
supabase functions deploy admin-sign-upload
supabase functions deploy rtdn --no-verify-jwt
supabase functions deploy send-push --no-verify-jwt
```
Roz ke 2 notification ke liye schema.sql ke neeche wala `cron.schedule` (hourly) ek baar chalao.

### 5. Google Play Console (payment)
1. Naya app banao, ek build upload karo (Internal testing). Subscriptions testing ke liye ye zaroori hai.
2. Monetize > Subscriptions: do products banao, aur har ek mein **do base plan**:
   | Product ID | Base plan ID | Type | Price |
   |---|---|---|---|
   | `shree_ram` | `ram-prepaid` | Prepaid, 1 month | ₹301 |
   | `shree_ram` | `ram-auto` | Auto-renewing, monthly | ₹301 |
   | `shree_krishna` | `krishna-prepaid` | Prepaid, 1 year | ₹3001 |
   | `shree_krishna` | `krishna-auto` | Auto-renewing, yearly | ₹3001 |
   (IDs `schema.sql` ke `plans` table se match karni chahiye. Badalni ho to admin panel mein Plans edit karo.)
3. Setup > API access: ek **service account** banao/jodo, Play Console mein usse "View financial data" aur "Manage orders and subscriptions" permission do. Uski JSON key upar `GOOGLE_SERVICE_ACCOUNT` mein gayi.
4. Monetization setup > **Real-time developer notifications**: Google Cloud Pub/Sub topic banao, push subscription ka endpoint:
   `https://YOUR_REF.supabase.co/functions/v1/rtdn?key=RTDN_SECRET`
5. Release se pehle: Privacy Policy link, Data Safety form, content rating, **AI-generated content** ka sahi jawab.

### 6. Firebase (sirf push notification ke liye, optional)
Firebase project > Android app (`com.tech2544.gurukulam`) > `google-services.json` ko `app/` folder mein rakho. Na ho to app bina push ke chalega.

### 7. Android Studio
1. Android Studio ko latest update karo. **File > Open** > is folder ko kholo.
2. `local.properties.example` ko `local.properties` naam se copy karo aur values bharo (SUPABASE_URL, SUPABASE_ANON_KEY, GOOGLE_WEB_CLIENT_ID, CDN_BASE, PRIVACY_URL, sdk.dir).
3. Gradle Sync. Studio "Gradle wrapper" ya JDK ke baare mein pooche to usi ki suggestion maan lo.
4. Phone par Run. Release ke liye Build > Generate Signed App Bundle.

### 8. Admin panel
`admin/index.html` ke upar wali 3 line (SUPABASE_URL, ANON_KEY, CDN_BASE) badlo. Cloudflare Pages par deploy karo, aur **Cloudflare Access** se us page ko apne email tak lock kar do (double security).

## Content kaise daalein (Admin panel)
- **Compress pehle:** HandBrake preset "Gurukulam Vertical" (720x1280, RF 24, Web Optimized).
- Content > Naya: kind chuno (music/story/movie/status/study/wallpaper/ringtone/calendar), thumbnail aur media upload karo, **Premium** tick karo to private bucket mein jayega.
- Pehle `published` OFF (draft) rakho, check karke ON karo.
- Status image ho to thumbnail aur media dono wahi image.
- Wallpaper: image ya (live ke liye) chhoti loop video; thumbnail 9:16.
- Calendar: har mahine ki image (thumbnail mein).
- Banners 2:1 (1200x600). Home ke 6 tiles aur Plans (price, fayde, photo, video) bhi yahin se badalte hain.

## Jo is version mein NAHI hai (agla phase)
- VIP profile **frames** ka design/apply UI (abhi sirf count milta hai).
- Status/shorts par super comments.
- Quiz system, movies ka part-wise (series) grouping aur movie ke end ka poll.
- Offline download, HLS (adaptive quality), feed ka agla video pehle se load karna.
- Crashlytics, aur Room wala bada offline cache (abhi simple cache hai).

## Safety yaad rakhna
- `local.properties`, service-account JSON, R2 keys, `service_role` key kisi ko mat bhejna, git par mat daalna. Leak ho jaye to turant rotate karo.
- App mein sirf anon key hai. Premium, credits aur plan sirf server (Edge Function) likhta hai.
