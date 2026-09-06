# Auto-login สำหรับ Piriyalai Hotspot (อ้างอิงสคริปต์ PC)

ถ้าคุณมีสคริปต์เดิมบน PC ให้ copy มาวางในโฟลเดอร์นี้ หรือส่งให้ผมดู

## วิธีหา URL ที่ใช้บน PC

1. เปิด **Command Prompt** หรือ **PowerShell** บน PC (ขณะต่อ WiFi โรงเรียน)
2. รัน:

```bat
ipconfig
```

ดู **Default Gateway** (เช่น `10.10.222.1`)

3. เปิด browser ไปหน้า login แล้ว copy URL จากแถบ address bar

## สคริปต์ทดสอบ (PowerShell) — FortiGate

แก้ `$USERNAME`, `$PASSWORD`, `$GATEWAY` แล้วรันบน PC:

```powershell
$GATEWAY = "10.10.222.1"    # เปลี่ยนตาม ipconfig
$USERNAME = "your_username"
$PASSWORD = "your_password"

# ลอง HTTP port 1000 ก่อน
$urls = @(
    "http://${GATEWAY}:1000/",
    "http://${GATEWAY}/",
    "https://${GATEWAY}:1003/"
)

foreach ($base in $urls) {
    Write-Host "Trying $base ..."
    try {
        $page = Invoke-WebRequest -Uri $base -UseBasicParsing -TimeoutSec 8
        if ($page.Content -match 'name="magic"\s+value="([^"]+)"') {
            $magic = $Matches[1]
            $body = "magic=$magic&username=$USERNAME&password=$PASSWORD"
            $postUrl = if ($base -match ":1000") { "$base/logincheck" } else { "$base/fgtauth" }
            $result = Invoke-WebRequest -Uri $postUrl -Method POST -Body $body -UseBasicParsing
            Write-Host "SUCCESS via $base (HTTP $($result.StatusCode))"
            exit 0
        }
    } catch {
        Write-Host "  Failed: $($_.Exception.Message)"
    }
}
Write-Host "All failed"
```

## สคริปต์ทดสอบ (PowerShell) — MikroTik

```powershell
$GATEWAY = "10.10.222.1"
$USERNAME = "your_username"
$PASSWORD = "your_password"

$page = Invoke-WebRequest -Uri "http://$GATEWAY/login" -UseBasicParsing
if ($page.Content -match "hexMD5\('([0-9a-fA-F]+)'") {
    $chapId = $Matches[1]
    if ($page.Content -match "\+\s*'([0-9a-fA-F]+)'\)") {
        $chapChallenge = $Matches[1]
        $md5 = [System.Security.Cryptography.MD5]::Create()
        $hash = -join ($md5.ComputeHash([Text.Encoding]::ASCII.GetBytes("$chapId$PASSWORD$chapChallenge")) | ForEach-Object { $_.ToString("x2") })
        $body = "username=$USERNAME&password=$hash&dst=&popup=true"
        Invoke-WebRequest -Uri "http://$GATEWAY/login" -Method POST -Body $body -UseBasicParsing
        Write-Host "MikroTik login sent"
    }
} else {
    $body = "username=$USERNAME&password=$PASSWORD&dst=&popup=true"
    Invoke-WebRequest -Uri "http://$GATEWAY/login" -Method POST -Body $body -UseBasicParsing
    Write-Host "Plain MikroTik login sent"
}
```

## ส่งข้อมูลให้ผม

ถ้ายังไม่ได้ ส่งมาให้:
- ผลลัพธ์ `ipconfig` (Default Gateway)
- URL ในหน้า login ของ browser
- สคริปต์เดิมบน PC (ถ้ามี)
