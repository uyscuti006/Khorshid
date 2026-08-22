پ
# خورشید (Khorshid)

<p align="center">
  <img src="https://opengraph.githubassets.com/9d4900a4feeffbe3084feced37503d9570357840f698fb6eaa22ba4a91d955ff/uyscuti006/Khorshid" alt="Khorshid banner" width="600"/>
</p>

<p align="center">
  یک کلاینت V2ray برای اندروید، بر پایه‌ی <a href="https://github.com/2dust/v2rayNG">V2rayNG</a> — با قابلیت‌های اضافه‌شده برای اتصال پایدارتر و امن‌تر.
</p>

<p align="center">
  <a href="https://t.me/VPN_Khorshid"><img src="https://img.shields.io/badge/Telegram-کانال%20خورشید-26A5E4?logo=telegram&logoColor=white" alt="Telegram Channel"></a>
  <a href="https://github.com/uyscuti006/Khorshid/releases"><img src="https://img.shields.io/github/v/release/uyscuti006/Khorshid?label=آخرین%20نسخه" alt="Latest Release"></a>
  <a href="https://github.com/uyscuti006/Khorshid/blob/main/LICENSE"><img src="https://img.shields.io/badge/license-GPL--3.0-blue" alt="License"></a>
  <a href="https://github.com/uyscuti006/Khorshid/stargazers"><img src="https://img.shields.io/github/stars/uyscuti006/Khorshid?style=flat" alt="Stars"></a>
</p>

---

## درباره‌ی پروژه

**خورشید** یک فورک از V2rayNG است که با هدف رفع چند مشکل رایج در استفاده‌ی روزمره از کلاینت‌های V2ray ساخته شده: قطع‌نشدن ناخواسته‌ی ترافیک هنگام افت تانل، تداخل با سایر VPNها، و پیدا کردن IP تمیز کلادفلر برای بهبود کیفیت اتصال. تمرکز پروژه روی سادگی برای کاربر فارسی‌زبان است، نه صرفاً افزودن قابلیت فنی.

## امکانات کلیدی

- **قطع خودکار VPNهای رقیب** — با اتصال در حالت VPN، هر VPN فعال دیگری روی دستگاه به‌طور خودکار قطع می‌شود تا تداخل پیش نیاید.
- **Kill Switch واقعی** — مسدودسازی کامل اینترنت در سطح tun-interface وقتی تانل قطع می‌شود؛ یعنی هیچ ترافیکی بدون محافظت خارج نمی‌شود (نه فقط یک پرچم رابط کاربری). قطع دستی هم وقتی Kill Switch فعال است، یک تأییدیه نمایش می‌دهد تا کاربر ناخواسته محافظت را دور نزند.
- **اسکنر IP تمیز کلادفلر** — پیدا کردن بهترین IPهای کلادفلر برای هر کانفیگ به‌صورت جداگانه (نه یک IP سراسری)، رتبه‌بندی بر اساس تأخیر/جیتر/سرعت، و اعمال خودکار روی کانفیگ‌ها بدون قطع تونل فعال.
- **صفحه‌ی ساده (Simple Main)** — دسته‌بندی کانفیگ‌ها (ALL / BPB / NAHAN / OTHER / Clean IP) با آمار سرعت آپلود/دانلود و پینگ، و دکمه‌ی اتصال/قطع با انیمیشن روان.
- **راهنمای درون‌برنامه‌ای** — آموزش تصویری قدم‌به‌قدم که در اولین اجرا نمایش داده می‌شود و هر زمان با آیکون «؟» قابل مشاهده‌ی مجدد است.
- سازگار با ساختار اشتراک (Subscription) V2rayNG برای دریافت و به‌روزرسانی خودکار لیست کانفیگ‌ها.

## نصب

آخرین نسخه‌ی امضاشده (Signed Release) را می‌توانید از یکی از این دو مسیر دریافت کنید:

- 📦 [GitHub Releases](https://github.com/uyscuti006/Khorshid/releases)
- 📢 [کانال تلگرام خورشید](https://t.me/VPN_Khorshid)

## ساخت از سورس (Build from Source)

```bash
git clone --recursive https://github.com/uyscuti006/Khorshid.git
cd Khorshid
```

> پروژه شامل چند زیرماژول (submodule) است، از جمله `AndroidLibXrayLite` و `hev-socks5-tunnel`؛ حتماً با فلگ `--recursive` کلون کنید یا بعد از کلون دستور زیر را اجرا کنید:

```bash
git submodule update --init --recursive
```

سپس پروژه را در **Android Studio** باز کرده و ماژول اصلی (`V2rayNG`) را Build/Run کنید. اسکریپت `compile-hevtun.sh` برای کامپایل کتابخانه‌ی تونل مورد نیاز است.

## این پروژه بر پایه‌ی چه چیزی ساخته شده؟

خورشید یک فورک از [2dust/V2rayNG](https://github.com/2dust/v2rayNG) است و از کتابخانه‌های زیر به‌عنوان زیرماژول استفاده می‌کند:

- [AndroidLibXrayLite](https://github.com/2dust/AndroidLibXrayLite)
- [hev-socks5-tunnel](https://github.com/heiher/hev-socks5-tunnel)

## مشارکت

اگر باگی پیدا کردید یا پیشنهادی داشتید، از بخش [Issues](https://github.com/uyscuti006/Khorshid/issues) پروژه استفاده کنید یا Pull Request بفرستید.

## لایسنس

این پروژه تحت لایسنس [GPL-3.0](https://github.com/uyscuti006/Khorshid/blob/main/LICENSE) منتشر شده است.

---

<p align="center">ساخته‌شده با ❤️ برای کاربران فارسی‌زبان</p>
