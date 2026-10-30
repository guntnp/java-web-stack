# java-web-stack

Claude Code skill + โปรเจกต์ตัวอย่างสำหรับเว็บโครงสร้าง **Cloudflare → Nginx → Spring Boot → MariaDB** บน Docker Compose

- `SKILL.md`: กฎ ขั้นตอน และเช็กลิสต์ที่ Claude ใช้ตอนช่วยงาน
- `templates/`: โปรเจกต์ที่รันได้จริง (ล็อกอินด้วย session + CSRF, สิทธิ์ admin, CRUD ตัวอย่าง, rate limit, security headers, Cloudflare Tunnel) พร้อม smoke test

## ติดตั้งเป็น skill

```sh
# ใช้ได้ทุกโปรเจกต์ในเครื่อง
git clone https://github.com/guntnp/java-web-stack ~/.claude/skills/java-web-stack

# หรือเฉพาะโปรเจกต์เดียว (commit ไปกับ repo ทีมจะได้ด้วย)
git clone https://github.com/guntnp/java-web-stack .claude/skills/java-web-stack
```

แล้วพิมพ์ `/java-web-stack` ใน Claude Code หรือขอเช่น "เริ่มโปรเจกต์ใหม่ตาม java-web-stack"

## ลองรัน templates

```sh
cp -r templates my-app && cd my-app
cp .env.example .env          # ใส่รหัสผ่าน: openssl rand -base64 32
docker compose up -d --build  # http://localhost:8080
bash scripts/smoke.sh         # ต้องได้ "failed 0"
```

ต้องมีแค่ Docker ไม่ต้องติดตั้ง Java หรือ Maven
