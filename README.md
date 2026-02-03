# 🛡️ API Guardian

**API Guardian** is a production-grade **API rate limiting, abuse detection, and analytics system** built using **Spring Boot, Redis, and MySQL**.
It protects APIs from overuse and malicious traffic while providing detailed audit logs and analytics.

---

## 🚀 Features

### ⏱️ Rate Limiting (Token Bucket)

* Redis-based **token bucket algorithm**
* Supports burst traffic safely
* Atomic operations using **Redis Lua scripts**
* Per-identifier limits:

  * User ID (JWT)
  * API Key
  * IP Address

---

### 🚫 Abuse Detection & Auto-Ban

* Tracks repeated rate-limit violations
* Automatically bans abusive clients
* Temporary bans with TTL (auto-unban)
* Global enforcement across all APIs

---

### 🧾 Audit Logging (AOP)

* Logs every request decision:

  * `ALLOW`, `BLOCK`, `BAN`
* Captures:

  * Identifier
  * Endpoint
  * HTTP method
  * Timestamp
* Implemented using **Spring AOP** (no controller pollution)
* Stored in **MySQL**

---

### 📊 Analytics Dashboard APIs

Read-only analytics endpoints powered by JPQL projections:

* Top blocked users/IPs
* Requests per endpoint
* Decision breakdown
* Hourly traffic patterns

---

## 🏗️ Architecture

```
Client
  ↓
Spring Security Filter Chain
  ↓
RateLimitingFilter
  ├── Redis (rate limit + abuse tracking)
  └── AbuseDetectionService
  ↓
Controller
  ↓
AuditLoggingAspect (AOP)
  ↓
MySQL (Audit Logs)
```

---

## 🧠 How It Works

1. **Request Identification**

   * USER_ID (JWT)
   * API_KEY
   * IP address fallback

2. **Rate Limit Check**

   * Token bucket stored in Redis
   * Tokens refilled over time
   * Atomic Lua script ensures consistency

3. **Abuse Detection**

   * Violations counted in Redis
   * Temporary bans applied automatically

4. **Audit Logging**

   * Decision attached to request context
   * Logged after request completion via AOP

5. **Analytics**

   * Aggregated insights via MySQL queries

---

## 🛠️ Tech Stack

* **Java 17**
* **Spring Boot 3.x**
* **Spring Security**
* **Redis (Lettuce client)**
* **Redis Lua Scripts**
* **MySQL**
* **Spring Data JPA**
* **Spring AOP**
* **Docker**
* **Postman (Load testing)**

---

## ⚙️ Setup Instructions

### 1️⃣ Start Redis (Docker)

```bash
docker run -d --name redis -p 6379:6379 redis
```

### 2️⃣ Configure MySQL

```sql
CREATE DATABASE api_guardian;
```

Update `application.properties`:

```properties
spring.datasource.url=jdbc:mysql://localhost:3306/api_guardian
spring.datasource.username=root
spring.datasource.password=YOUR_PASSWORD

spring.jpa.hibernate.ddl-auto=update
spring.jpa.show-sql=true
```

### 3️⃣ Run the Application

```bash
mvn spring-boot:run
```

---

## 🔍 Sample Endpoints

### Test API

```http
GET /api/test/hello
```

### Analytics APIs

```http
GET /api/analytics/top-blocked
GET /api/analytics/endpoints
GET /api/analytics/decisions
GET /api/analytics/hourly
```

---

## 🧪 Load Testing

* Tested using **Postman Runner**
* Verified:

  * 200 → allowed requests
  * 429 → rate limit exceeded
  * 403 → temporary ban

---

## 🎯 Why This Project Matters

✔ Real-world API security patterns
✔ Distributed systems awareness
✔ Redis + DB coordination
✔ Clean separation of concerns
✔ Production-style observability

---
