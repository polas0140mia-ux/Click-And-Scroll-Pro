# Click & Scroll Pro — নেটিভ অ্যান্ড্রয়েড অটো ক্লিকার ও অটো-স্ক্রল প্রজেক্ট (AdMob সহ)

এই প্রজেক্টটি আপনার ভিডিওতে দেখানো **"Click With Safin"** অ্যাপের সবগুলো ফিচার এবং আপনার অনুরোধ করা **Google AdMob** ও **অটো-স্ক্রল (Auto-Scroll)** ফিচারসহ তৈরি করা হয়েছে।

## যুক্ত করা ফিচারসমূহ
1. **Single Target Mode:** এক জায়গায় দ্রুত ট্যাপ করার জন্য ভাসমান টার্গেট ও কন্ট্রোল প্যানেল।
2. **Multi Target Mode:** স্ক্রিনের একাধিক পয়েন্টে (`+` ও `-` বাটন দিয়ে) ক্রমানুসারে অটো-ক্লিক করার সুবিধা।
3. **Auto-Scroll Mode (নতুন ফিচার):** রিলস, শর্টস, টিকটক, ফেসবুক বা ওয়েবপেজ অটোমেটিক স্ক্রল করার জন্য চার দিকে (`⬆`, `⬇`, `⬅`, `➡`) সোয়াইপ অটোমেশন।
4. **Stop Logic:** মিলিসেকেন্ড ইন্টারভাল (৪০ms সেফটি চেকসহ), `Run Infinity`, `Run Timer` এবং `Number of cycle` সেটিংস।
5. **Save Configs (Manage Configuration):** পছন্দের ক্লিক ও স্ক্রল কনফিগারেশন সেভ ও লোড করার সুবিধা।
6. **Google AdMob Integration (নতুন ফিচার):** নিচে **Banner Ad** এবং সেটিংস/কনফিগ সেভ করার সময় **Interstitial Ad** যুক্ত করা হয়েছে।

## আপনার নিজের AdMob ID কোথায় বসাবেন?
1. `app/src/main/AndroidManifest.xml` ফাইলে গিয়ে `com.google.android.gms.ads.APPLICATION_ID`-এর `android:value`-তে আপনার AdMob App ID বসান।
2. `app/src/main/java/com/polas/autoclicker/MainActivity.kt` ফাইলের শুরুতে `ADMOB_BANNER_UNIT_ID` এবং `ADMOB_INTERSTITIAL_UNIT_ID`-তে আপনার Ad Unit ID বসান।

## মোবাইল থেকেই APK ফাইল তৈরির উপায় (পিসি ছাড়া)
1. আপনার GitHub অ্যাকাউন্টে একটি নতুন Repository খুলে এই জিপ ফাইলের ভেতরের সব ফাইল আপলোড করুন।
2. Repository-এর **Actions** ট্যাবে গেলে **Build Android APK** অটোমেটিক চালু হবে (তিনিট মিনিট সময় নেবে)।
3. বিল্ড শেষ হলে নিচে **Click-And-Scroll-Pro-APK** থেকে সরাসরি ইন্সটলযোগ্য `app-debug.apk` ডাউনলোড করে ফোনে ইন্সটল করতে পারবেন।
