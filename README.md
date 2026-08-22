# خورشید (Khorshid)

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

**خورشید** یک فورک از V2rayNG است که با هدف بهبود تجربه‌ی روزمره‌ی کاربران فارسی‌زبان از کلاینت‌های V2ray ساخته شده؛ با تمرکز بر سادگی استفاده و افزایش کیفیت اتصال.

## امکانات کلیدی

- **اسکنر IP تمیز کلادفلر** — پیدا کردن بهترین IPهای کلادفلر برای هر کانفیگ به‌صورت جداگانه (نه یک IP سراسری)، رتبه‌بندی بر اساس تأخیر/جیتر/سرعت، و اعمال خودکار روی کانفیگ‌ها بدون قطع تونل فعال.
- **صفحه‌ی ساده (Simple Main)** — یک رابط کاربری ساده و کاربرپسند برای مدیریت و اتصال سریع‌تر به کانفیگ‌ها.

## نصب

آخرین نسخه‌ی امضاشده (Signed Release) را می‌توانید از یکی از این دو مسیر دریافت کنید:

- 📦 [GitHub Releases](https://github.com/uyscuti006/Khorshid/releases)
- 📢 [کانال تلگرام خورشید](https://t.me/VPN_Khorshid)

### کدام نسخه را نصب کنم؟

در بخش Releases معمولاً چند فایل APK با نام‌های متفاوت (بر اساس معماری پردازنده‌ی گوشی) منتشر می‌شود:

| نسخه | مناسب برای | توضیح |
|---|---|---|
| `universal` | همه‌ی گوشی‌ها | اگر از معماری گوشی‌تان مطمئن نیستید، این گزینه را نصب کنید؛ روی هر گوشی کار می‌کند ولی حجمش بیشتر است |
| `arm64-v8a` | اکثر گوشی‌های امروزی | مناسب گوشی‌های چند سال اخیر؛ حجم کمتر نسبت به universal |
| `armeabi-v7a` | گوشی‌های قدیمی‌تر | برای گوشی‌های اندرویدی نسل‌های قبل |
| `x86` / `x86_64` | شبیه‌سازها و برخی تبلت‌ها | مخصوص Emulatorها یا دستگاه‌های با پردازنده‌ی اینتل |

اگر از معماری گوشی خود مطمئن نیستید، از اپلیکیشن‌هایی مثل CPU-Z روی گوشی استفاده کنید یا همان نسخه‌ی universal را نصب کنید.

> پیش از نصب، فعال بودن گزینه‌ی «نصب از منابع ناشناس» (Install unknown apps) برای مرورگر یا فایل‌منیجری که با آن APK را باز می‌کنید لازم است.

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
