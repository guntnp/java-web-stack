---
name: java-web-stack
description: ใช้เมื่อสร้าง แก้ไข หรือขึ้น server เว็บที่ใช้โครงสร้าง Frontend → Cloudflare → Nginx → Java Spring Boot → MariaDB/MySQL บน Docker Compose เช่น "เริ่มโปรเจกต์ใหม่", "เพิ่ม API", "เพิ่มตาราง", "ทำระบบล็อกอิน", "ตั้ง Nginx", "ตั้ง Cloudflare", "ขึ้น production", "rollback", "เช็กความปลอดภัย", new project, add endpoint, deploy, docker compose, spring boot, spring security, reverse proxy
---

# Java Web Stack

พิมพ์เขียวเว็บของทีม: Cloudflare → Nginx → Spring Boot → MariaDB รันด้วย Docker Compose
โฟลเดอร์ `templates/` เป็นโปรเจกต์ตัวอย่างที่ **รันได้จริงและผ่านการทดสอบแล้ว** (ล็อกอิน, สิทธิ์ admin, CRUD สินค้า, rate limit, security headers) ให้คัดลอกไปเริ่มงาน แล้วเพิ่ม feature ตามแบบที่มีอยู่

## ภาพรวม

```text
ผู้ใช้ (Browser)
      ↓ HTTPS
Cloudflare          DDoS · WAF · Bot Protection · Rate Limiting · CDN
      ↓ Cloudflare Tunnel (เซิร์ฟเวอร์ไม่เปิด port เว็บเลย)
Nginx               Static files · Reverse Proxy /api/ · Rate Limit · Security Headers · Real IP
      ↓ /api/*
Spring Boot         Spring Security (session + CSRF) · Validation · Business Logic
                    Controller → Service → Repository (JdbcClient)
      ↓ JDBC (network ภายในเท่านั้น)
MariaDB             Flyway Migration · Backup
```

| ชั้น | หน้าที่ | ห้ามทำ |
|---|---|---|
| Frontend | แสดงผล เรียก `/api/...` ผ่าน `public/js/api.js` | เก็บ secret, ใช้ `innerHTML` กับข้อมูลจาก server, เขียน `<script>` inline |
| Cloudflare | กันโจมตีก่อนถึงเซิร์ฟเวอร์ | cache `/api/`, ใช้ SSL แบบ Flexible |
| Nginx | ประตูเดียวเข้าระบบ และเป็นที่เดียวที่ตั้ง security headers | ใช้ `add_header` ใน `location` |
| Spring Boot | logic ทั้งหมด ตรวจสิทธิ์ ตรวจข้อมูล | เปิด port ออกนอก, ใส่ logic ใน Controller, ส่ง stack trace |
| Database | เก็บข้อมูล | เปิด port ออกนอก, ใช้ root ในแอป |

## กฎที่ห้ามพลาด

1. **ประตูเดียว:** มีแค่ `web` ที่มี `ports:` (local) และบน production ไม่มี service ไหนมี `ports:` เลย เพราะเข้าผ่าน Tunnel
2. **Secret อยู่ใน `.env` เท่านั้น:** `.env` อยู่ใน `.gitignore` และ commit แค่ `.env.example` ห้ามพิมพ์ค่า secret ลงแชต log หรือโค้ด
3. **แบ่งชั้นในโค้ด:**
   - Controller: รับ/ตอบ JSON + `@Valid`
   - Service: business rule + `@Transactional`
   - Repository: ที่เดียวที่มี SQL
4. **ตรวจสิทธิ์ที่ server:**
   - ใช้ `@PreAuthorize("hasRole('ADMIN')")` หรือเช็กเจ้าของข้อมูลใน Service
   - ผู้ใช้ปัจจุบันมาจาก `@AuthenticationPrincipal CurrentUser` เสมอ ห้ามรับ user id หรือ role จาก request body
5. **SQL ใช้ `:param` เสมอ:** ห้ามต่อ string จาก input
6. **Schema เปลี่ยนด้วย migration ใหม่:** เพิ่มไฟล์ `V{n}__ชื่อ.sql` ห้ามแก้ไฟล์ที่รันบน production แล้ว
7. **ตอบ error เป็น JSON แบบเดียว:** ใช้ `{"error": "code"}` ผ่าน `ApiExceptionHandler` ห้ามส่ง message หรือ stack trace ของ exception ให้ client
8. **ทุก feature มี unit test ของ Service:** เทสต์รันตอน build image ถ้าเทสต์ไม่ผ่าน image จะไม่ถูกสร้าง
9. **Backup ก่อน deploy ที่มี migration และ tag image ทุกครั้ง** จะได้ rollback ได้
10. **ทดสอบใน local ก่อนเสมอ:** ห้าม deploy หรือแก้ server จริงถ้าผู้ใช้ไม่ได้สั่งชัดเจน

## ไฟล์ใน templates/

```text
templates/
├── docker-compose.yml              # local: web publish 8080, app/db อยู่ใน network ภายใน
├── docker/
│   ├── compose.prod.yml            # production: Tunnel, ไม่มี ports, image tag ตาม APP_VERSION
│   ├── nginx/
│   │   ├── Dockerfile
│   │   ├── site.inc                # กฎร่วม: headers, CSP, /api/ proxy, rate limit, cache
│   │   ├── default.conf            # local
│   │   └── prod.conf               # production: real IP จาก Cloudflare, HSTS, บอก app ว่าเป็น https
│   └── backend/Dockerfile          # build + test ด้วย Maven, รันด้วย JRE เป็น user ที่ไม่ใช่ root
├── backend/
│   ├── pom.xml                     # Spring Boot 3.5, Java 21, Security, Validation, JDBC, Flyway, MariaDB
│   └── src/main/java/app/
│       ├── common/ApiExceptionHandler.java
│       ├── security/               # SecurityConfig (session + CSRF + BCrypt), CurrentUser
│       ├── auth/                   # register / login / logout / me, AdminBootstrap
│       └── product/                # ตัวอย่าง feature: Controller → Service → Repository + test
├── public/                         # index.html + js/api.js (ส่ง CSRF ให้อัตโนมัติ) + js/app.js
├── scripts/smoke.sh                # ทดสอบ end-to-end ผ่าน nginx (31 ข้อ)
├── .env.example
└── .gitignore
```

## ขั้นตอนงานที่พบบ่อย

**เริ่มโปรเจกต์ใหม่**
1. คัดลอกทุกอย่างใน `templates/` (รวม `.env.example` และ `.gitignore`) ไปเป็นโปรเจกต์ใหม่ แล้วเปลี่ยนชื่อ `myapp` ใน compose ทั้งสองไฟล์
2. `cp .env.example .env` แล้วใส่ค่า โดยสุ่มรหัสผ่านด้วย `openssl rand -base64 32`
3. `docker compose up -d --build` แล้วเปิด `http://localhost:8080` ถ้า port ชน ให้เปลี่ยน `"8080:80"`
4. `bash scripts/smoke.sh` ต้องได้ `failed 0`

**เพิ่ม feature / API ใหม่** (ทำตามแบบ `product/`)
1. ถ้ามีตารางใหม่ → เพิ่ม `backend/src/main/resources/db/migration/V{n+1}__ชื่อ.sql`
2. สร้าง package `app/<feature>/` → `XxxRepository` (SQL), `XxxService` (rule + `@Transactional`), `XxxController` (record request ที่มี validation)
3. กำหนดสิทธิ์: GET สาธารณะต้องเพิ่มใน `SecurityConfig` เอง (ค่าเริ่มต้นคือต้องล็อกอิน) ส่วนงานของ admin ใช้ `@PreAuthorize`
4. เขียน unit test ของ Service ใน `src/test/java/app/<feature>/`
5. เพิ่มกรณีทดสอบใน `scripts/smoke.sh` ทั้งกรณีที่ต้องผ่านและกรณีที่ต้องโดนปฏิเสธ (401/403/400)
6. `docker compose up -d --build app && bash scripts/smoke.sh`

**Frontend เรียก API:** ใช้ `api('/path', { method: 'POST', body })` จาก `public/js/api.js` ซึ่งขอ CSRF token และใส่ header `X-XSRF-TOKEN` ให้เอง แสดงข้อมูลด้วย `textContent` ห้ามใช้ `innerHTML` และ CSP ห้ามมี JS/CSS inline ทุกชนิด

**ขึ้น production** (คำสั่งรันจาก root ของโปรเจกต์บน server)
```sh
# 1. backup
docker compose --env-file .env -f docker/compose.prod.yml exec -T db \
  sh -c 'mariadb-dump -uroot -p"$MARIADB_ROOT_PASSWORD" --single-transaction app' | gzip > backup-$(date +%F-%H%M).sql.gz
# 2. deploy โดย tag image ตาม commit (ต้องมี --env-file เพราะ compose file อยู่ใน docker/)
APP_VERSION=$(git rev-parse --short HEAD) docker compose --env-file .env -f docker/compose.prod.yml up -d --build
# 3. ดู log
docker compose --env-file .env -f docker/compose.prod.yml logs -f --tail=100 app
```
**Rollback:** `APP_VERSION=<tag เดิม> docker compose --env-file .env -f docker/compose.prod.yml up -d` (ไม่ใส่ `--build`) ถ้า migration ใหม่ทำข้อมูลเสีย ให้กู้จาก backup ด้วย `gunzip -c backup-....sql.gz | docker compose ... exec -T db sh -c 'mariadb -uroot -p"$MARIADB_ROOT_PASSWORD" app'`

## Cloudflare (Tunnel)

1. ย้าย DNS ของโดเมนมาที่ Cloudflare
2. ไปที่ Zero Trust → Networks → Tunnels → Create tunnel → คัดลอก token ใส่ `CLOUDFLARE_TUNNEL_TOKEN` ใน `.env`
3. ตั้ง Public hostname: `example.com` → Service `HTTP` → `web:80`
4. SSL/TLS → Edge Certificates: เปิด Always Use HTTPS
5. Caching → Cache Rules: URI path starts with `/api/` → Bypass cache
6. Security: เปิด WAF managed rules และ Bot Fight Mode โดยตั้งข้อยกเว้นให้ path ที่ระบบภายนอกเรียกเข้ามา (webhook หรือ callback ของ payment/supplier) ไม่อย่างนั้นจะโดนบล็อก
7. บน server: firewall ปิดทุก port ขาเข้า ยกเว้น SSH ซึ่งใช้ key เท่านั้น

`prod.conf` ตั้งค่าเหล่านี้ไว้แล้ว:
- อ่าน IP จริงจาก `CF-Connecting-IP` เฉพาะ request ที่มาจาก Docker network (container cloudflared) ไม่อย่างนั้นทุกคนจะเป็น IP เดียวกันและโดน rate limit รวมกัน
- ส่ง `X-Forwarded-Proto: https` ให้ app เพื่อให้ cookie เป็น `Secure`

**ถ้าจำเป็นต้องใช้ Proxy ธรรมดา (เมฆสีส้ม) แทน Tunnel:**
- เปิด `ports: 80/443` และใช้ใบ cert ของ origin
- SSL mode ต้องเป็น **Full (Strict)**
- firewall รับเฉพาะ IP ของ Cloudflare โดยตั้งใน chain `DOCKER-USER` เพราะ Docker ข้ามกฎ ufw
- เปลี่ยน `set_real_ip_from` ให้เป็นรายการจาก https://www.cloudflare.com/ips/

## จุดที่มักพลาด

- **`add_header` หาย:** nginx ทิ้ง `add_header` ระดับ server ทั้งหมดใน location ที่มี `add_header` ของตัวเอง ดังนั้นใน location ให้ใช้ `expires` สำหรับ cache
- **Security headers ซ้ำ:** ให้ nginx เป็นที่เดียวที่ส่ง ซึ่ง `SecurityConfig` ปิดของ Spring ไว้แล้ว ถ้าเปิดคืน จะได้ `X-Frame-Options` ซ้ำกันสองค่าที่ขัดกัน
- **POST ได้ 403:** frontend ไม่ได้ส่ง CSRF token ต้องเรียกผ่าน `api()` หรือ GET `/api/auth/csrf` ก่อน
- **แก้ nginx config แล้ว:** `docker compose exec web nginx -t && docker compose exec web nginx -s reload`
- **ใช้ MySQL แทน MariaDB:**
  - image เปลี่ยนเป็น `mysql:8.4`
  - env เปลี่ยนเป็น `MYSQL_DATABASE`, `MYSQL_USER`, `MYSQL_PASSWORD`, `MYSQL_ROOT_PASSWORD`
  - healthcheck เปลี่ยนเป็น `["CMD", "mysqladmin", "ping", "-h", "localhost"]`
  - JDBC เปลี่ยนเป็น `jdbc:mysql://db:3306/app` และ dependency เป็น `com.mysql:mysql-connector-j`
  - คำสั่ง dump เปลี่ยนเป็น `mysqldump`
- **Container โดน kill (OOM):** heap ของ JVM คือ 75% ของ `mem_limit` ตั้งไว้ใน Dockerfile แล้ว ถ้าต้องการเพิ่ม ให้เพิ่ม `mem_limit`
- **ล็อกอินได้ 429:** nginx จำกัด login/register ไว้ที่ 10 ครั้งต่อนาทีต่อ IP (burst 5) ซึ่งตั้งใจไว้แบบนั้น

## เช็กลิสต์ก่อนขึ้น Production

- [ ] `bash scripts/smoke.sh` ผ่านครบใน local
- [ ] `docker compose -f docker/compose.prod.yml config` ไม่มี service ไหนมี `ports:`
- [ ] `.env` ไม่อยู่ใน git (`git ls-files | grep .env` เห็นแค่ `.env.example`) และสิทธิ์ไฟล์บน server เป็น `chmod 600 .env`
- [ ] เปลี่ยน `ADMIN_PASSWORD` เป็นค่าสุ่มยาว และไม่ใช้ซ้ำกับที่อื่น
- [ ] Cloudflare: Always Use HTTPS, ไม่ cache `/api/`, เปิด WAF และตั้งข้อยกเว้นให้ webhook แล้ว
- [ ] Firewall server ปิดทุก port ยกเว้น SSH และ SSH ปิด password login
- [ ] Backup อัตโนมัติ (เช่น cron ทุกวัน) และเคยลองกู้คืนจริงแล้ว
- [ ] Pin version ของ image: `cloudflared` ให้เปลี่ยนจาก `latest` เป็น tag ที่ใช้อยู่ และสแกน image ด้วย Trivy เมื่ออัปเดต dependency

## วิธีรายงานผลให้ผู้ใช้

บอกเป็นภาษาง่ายๆ ว่า: ทำอะไรไปบ้าง, แก้ไฟล์ไหน, ทดสอบอย่างไรและผลเป็นอย่างไร (เช่น smoke test ผ่านกี่ข้อ), อะไรที่ยังไม่ได้ทำหรือต้องให้ผู้ใช้ทำเอง (เช่น ตั้งค่าในหน้าเว็บ Cloudflare)
ถ้าเจอจุดที่ขัดกับ "กฎที่ห้ามพลาด" ในโปรเจกต์เดิม ให้แจ้งและเสนอวิธีแก้ ไม่แก้เองเงียบๆ
