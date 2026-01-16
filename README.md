# 🔐 Access Control Platform — Showcase

This repository is a **public showcase** of a production-grade access control system, designed to demonstrate **backend architecture, domain modeling, and event-driven patterns** using **Java and Quarkus**.

> ⚠️ **Disclaimer**  
> This repository does **not** contain sensitive configurations, secrets, or business-specific rules.  
> It is intended as a **reference implementation** to showcase architecture, patterns, and engineering practices.

---

## 🚀 Overview

The Access Control Platform is designed to manage **access decisions** in multi-tenant environments, supporting scenarios such as:
- Residential buildings
- Corporate facilities
- Controlled areas with dynamic access policies

The system focuses on **correctness, scalability, and maintainability**, prioritizing clear domain boundaries and production-ready practices.

---

## 🧠 Architectural Principles

- **Clean Architecture**
- **Domain-Driven Design (DDD)**
- **Modular Monolith**
- **Event-Driven Architecture**
- **Production-oriented design**

This project intentionally avoids over-engineering while maintaining a structure that can **evolve naturally into microservices** if needed.

---

## 🏗️ Architecture Overview

**High-level structure:**

- **API Layer**  
  REST endpoints and request validation

- **Application Layer**  
  Use cases and orchestration logic

- **Domain Layer**  
  Core business logic, entities, value objects, domain events

- **Infrastructure Layer**  
  Persistence, messaging, and external integrations

The system follows **clear separation of concerns** and avoids leaking infrastructure details into the domain.

---

## ⚙️ Core Features (Showcased)

- Rule-based access decision engine
- Modular access policies
- Multi-tenant support
- Domain events with **Outbox pattern**
- Soft-deletion and audit-friendly entities
- Observability-ready design
- Security-first mindset

---

## 🛠️ Tech Stack

### Backend
- **Java**
- **Quarkus**

### Architecture
- Modular monolith and a single microservice
- Event-driven design
- Domain-driven design

### Persistence
- PostgreSQL

### Security
- Keycloak (OIDC / OAuth2) — conceptual integration

### Observability
- Prometheus
- Grafana
- Alertmanager

### DevOps
- Docker
- GitHub Actions (basic CI)

---

## 🔐 Security & Configuration

All sensitive configuration has been intentionally excluded.
---

## 🧪 Testing Strategy

The project includes:
- Unit tests for domain logic
- Focus on deterministic business rules
- Testable services with minimal infrastructure coupling

---

## 📌 What This Repository Is (and Is Not)

### ✅ This repository **is**:
- A reference architecture
- A backend engineering showcase
- A demonstration of real-world patterns
- Suitable for technical evaluation and learning

### ❌ This repository **is not**:
- A complete commercial product
- A plug-and-play solution
- A system containing production secrets

---

## 📈 Why a Modular Monolith?

The system is intentionally built as a **modular monolith** to:
- Reduce operational complexity
- Maintain strong consistency
- Enable faster development cycles
- Allow future extraction into microservices if required

This approach balances **engineering rigor** with **pragmatic decision-making**.

---

### Decision Engine

The decision engine included in this repository is a **simplified dummy implementation**.

The real production engine includes:
- Rule prioritization
- Time window evaluation
- Multi-criteria matching
- Observability metrics

Those details are intentionally excluded from this public showcase.

---

## 🧑‍💻 Author

**Nicolás Guevara**  
Backend Engineer (Java)

- 💼 LinkedIn: https://www.linkedin.com/in/nicol%C3%A1s-guevara-herr%C3%A1n-a959a82ab/
- 🌐 Portfolio: https://nicog-portfolio.vercel.app

---

## 📄 License

This project is shared for **educational and showcase purposes**.

Commercial usage, redistribution, or reuse of ideas should be discussed with the author.
