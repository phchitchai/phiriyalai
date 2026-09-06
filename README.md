# Piriyalai Hotspot Auto-Login

แอป Android สำหรับ **login WiFi Hotspot โรงเรียนพิริยาลัยอัตโนมัติ** ที่ `login.piriyalaihotspot.com:1003` ด้วย username/password

## ทำงานอย่างไร

1. เชื่อมต่อ WiFi โรงเรียน
2. แอปเปิดหน้า captive portal แล้วดึง `magic` token (FortiGate)
3. ส่ง username + password ไปที่ `/fgtauth` อัตโนมัติ
4. ไม่ต้องเปิด browser หรือพิมพ์รหัสเองทุกครั้ง

## ติดตั้ง

1. เปิดโฟลเดอร์ `android/` ด้วย **Android Studio**
2. Build APK: `Build > Build Bundle(s) / APK(s) > Build APK(s)`
3. ติดตั้ง APK บนมือถือ Android 8.0+

## การใช้งาน

1. เปิดแอป **Piriyalai Hotspot**
2. กรอก:
   - **Portal URL**: `https://login.piriyalaihotspot.com:1003/` (ค่าเริ่มต้น)
   - **Username** / **Password** ของ hotspot
   - **WiFi SSID** (ถ้ารู้ชื่อ WiFi โรงเรียน — ถ้าเว้นว่างจะ login ทุก WiFi)
3. เปิด **Auto-login** แล้วกด **บันทึก**
4. กด **ทดสอบ Login** ขณะเชื่อมต่อ WiFi โรงเรียนเพื่อตรวจสอบ

## ข้อควรรู้

- Portal นี้ใช้ **port 1003** (FortiGate HTTPS captive portal) — **ไม่ใช่ Google Authenticator**
- ต้องอยู่ใน WiFi โรงเรียนเท่านั้น (server ไม่ตอบจากอินเทอร์เน็ตภายนอก)
- เปิด **ยอมรับ certificate ของ portal** ถ้า login ไม่ผ่านเพราะ SSL
- รหัสผ่านเก็บใน **EncryptedSharedPreferences** บนเครื่องเท่านั้น

## สิทธิ์ที่ต้องการ

- Internet / WiFi state — ตรวจจับการเชื่อมต่อ
- Location หรือ Nearby WiFi (Android 13+) — อ่านชื่อ WiFi
- Notifications — แสดงสถานะ foreground service

## พัฒนา / ทดสอบ

```bash
cd android
./gradlew test
```

Unit test ครอบคลุมการ parse `magic` token จาก HTML ของ FortiGate portal

## โครงสร้าง

```
android/app/src/main/java/com/piriyalai/hotspot/
├── MainActivity.kt              # หน้าตั้งค่า
├── auth/FortiGateAuthClient.kt  # logic login portal
├── service/HotspotLoginService.kt # auto-login เมื่อ WiFi เชื่อมต่อ
└── data/CredentialStore.kt      # เก็บ credentials แบบเข้ารหัส
```
