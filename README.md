# RefCard 05 – Backend: Spring Boot + Amazon RDS (MariaDB) + Amazon S3

> **RefCard05 (M324 DevOps)** – Fork von `bbwlc/RefCard-03-be-SpringBootBusinessTrips` (Stand `v1-start`).
> Projektteam: Panat Ruangsri, Timo Eichenberger, Johan Stefanes  
> Hauptdokumentation (PDF): [docs/RefCard05_Dokumentation.pdf](docs/RefCard05_Dokumentation.pdf) (Kopie, Original im Repo refcard-05-fullstack-aws)  
> Backup-Dokumentation, AWS-Setup und lokales Compose: **[refcard-05-fullstack-aws](https://github.com/Panat-ZHH/refcard-05-fullstack-aws)**
> Frontend: **[refcard-05-fe-react-vite](https://github.com/Panat-ZHH/refcard-05-fe-react-vite)**

[![Backend CI/CD](https://github.com/Panat-ZHH/refcard-05-be-springboot-businesstrips/actions/workflows/ci-cd.yml/badge.svg)](https://github.com/Panat-ZHH/refcard-05-be-springboot-businesstrips/actions/workflows/ci-cd.yml)

## Was ist neu in RefCard05?

| Thema | Umsetzung | Dateien |
|---|---|---|
| CI/CD | build → test → Image → **Amazon ECR** → Rolling Deployment **ECS Fargate** → Smoke-Test | [`.github/workflows/ci-cd.yml`](.github/workflows/ci-cd.yml) |
| Image | Multi-Stage: Maven/JDK 21 → JRE 21 (alpine, non-root, Healthcheck) | [`Dockerfile`](Dockerfile) |
| Versionierung | Git-Tag `vX.Y.Z` → Image-Tags `X.Y.Z`, `X.Y`, `latest` + `sha-<commit>`; Version in `/actuator/info` | Pipeline, `pom.xml` (`${revision}`) |
| Datenbank | **RDS MariaDB**; Host, Port, DB-Name, User, Passwort ausschliesslich aus Umgebungsvariablen (auf AWS aus Secrets Manager) | [`application-mariadb.properties`](src/main/resources/application-mariadb.properties), [`.aws/task-definition.json`](.aws/task-definition.json) |
| Bilder | Storage-Abstraktion: lokal (Volume, RefCard04) **oder Amazon S3** (Profil `s3`) | [`ch.bbw.trips.images`](src/main/java/ch/bbw/trips/images) |
| Least Privilege | Session Policy nur für den Bilder-Bucket (Learner Lab: keine eigenen Rollen) | [`iam/s3-least-privilege-policy.json`](src/main/resources/iam/s3-least-privilege-policy.json), [`S3ImageStorage`](src/main/java/ch/bbw/trips/images/S3ImageStorage.java) |

### Spring-Profile

| Profil | Datenbank | Bilder | Verwendung |
|---|---|---|---|
| *(default)* | H2 in-memory | lokal `./data/images` | Tests, schneller Start ohne Docker |
| `mariadb` | MariaDB über `DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USER`, `DB_PASSWORD` | – | lokal (Compose) und AWS (RDS) |
| `s3` | – | S3-Bucket `S3_BUCKET` (optional `S3_ENDPOINT` für S3Mock) | lokal (S3Mock) und AWS |

Auf ECS läuft das Backend mit `SPRING_PROFILES_ACTIVE=mariadb,s3`.

### Bilder-API

| Endpoint | Beschreibung |
|---|---|
| `GET /v1/images` | Liste der Bilder im Storage (`storage: s3://…`) |
| `GET /v1/trips/{id}/image` | Bild eines Trips (aus S3) |
| `PUT /v1/trips/{id}/image` | Bild hochladen (multipart, Feld `file`, JPEG/PNG/GIF/WebP, max. 5 MB) |

Fehlende S3-Rechte führen zu einer verständlichen `503`-Antwort (Problem Details) und `imageStorage: DOWN` in `/actuator/health`,
die Trips-API bleibt verfügbar (der ALB-Health-Check nutzt `/actuator/health/readiness` = App + DB).

### Pipeline-Konfiguration (GitHub)

| Typ | Name | Inhalt |
|---|---|---|
| Secret | `AWS_ACCESS_KEY_ID`, `AWS_SECRET_ACCESS_KEY`, `AWS_SESSION_TOKEN` | temporäre Learner-Lab-Zugangsdaten (laufen mit der Session ab) |
| Variable | `DB_SECRET_ARN` | ARN des Secrets `biztrips/db` (keine Zugangsdaten, nur die Referenz) |
| Variable | `S3_BUCKET` | Name des Bilder-Buckets |
| Variable | `APP_URL` | `http://<ALB-DNS>` für Smoke-Test und CORS |

### Release erstellen

```bash
git tag -a v1.1.0 -m "Release 1.1.0"
git push origin v1.1.0
```

---

## Ursprüngliche Beschreibung (RefCard 03)

REST-API-Backend für die BusinessTrips-Applikation (Mitarbeitende, Business
Trips, Flüge, Meetings). Teil der CI/CD-Übungssequenz **RefCard 02–05**
(siehe [`docs/future/RefCard-03-RefCard-05-own-repos.md`](docs/future/RefCard-03-RefCard-05-own-repos.md))
— dieses Repository ist **RefCard 03**: Spring Boot Backend mit MariaDB.

## Tech-Stack

- Java 21, Spring Boot 4.1 (Web, Data JPA)
- MariaDB (lokal via Docker Compose) / H2 (Default, für Tests und schnellen lokalen Start)
- Maven (Maven Wrapper, kein lokales Maven nötig)

## Schnellstart

### Ohne Datenbank-Setup (H2, Default)

```bash
./mvnw spring-boot:run
```

Startet auf Port 8080 mit einer flüchtigen In-Memory-H2-Datenbank (Daten sind nach jedem Neustart weg, werden aber automatisch neu geseedet).

### Mit MariaDB (persistent, wie in Produktion)

```bash
export DB_USER=root DB_PASSWORD=<lokales-passwort-frei-waehlen>
docker compose -f docker/docker-compose.yml up -d
./mvnw spring-boot:run -Dspring-boot.run.profiles=mariadb
```

- MariaDB läuft dann auf `localhost:3306` (Datenbank `db_biztrips`, User `root`, Passwort aus `DB_PASSWORD`)
- phpMyAdmin ist unter [http://localhost:8082](http://localhost:8082) erreichbar (gleiche Zugangsdaten)
- Die Demo-Daten werden nur beim allerersten Start eingefügt — ein Neustart der Applikation dupliziert sie nicht

### Tests ausführen

```bash
./mvnw test
```

## API

Basis-Pfad: `/v1`

| Endpoint | Beschreibung |
| --- | --- |
| `GET /v1/trips`, `POST /v1/trips`, `GET /v1/trips/{id}`, `DELETE /v1/trips/{id}` | Business Trips |
| `GET /v1/employees`, `POST /v1/employees`, `GET /v1/employees/{id}`, `PUT /v1/employees/{id}`, `DELETE /v1/employees/{id}`, `DELETE /v1/employees` | Mitarbeitende |
| `GET /v1/flights`, `POST /v1/flights`, `GET /v1/flights/{id}`, `PUT /v1/flights/{id}`, `DELETE /v1/flights/{id}`, `DELETE /v1/flights` | Flüge |
| `GET /v1/meetings`, `POST /v1/meetings`, `GET /v1/meetings/{id}`, `PUT /v1/meetings/{id}`, `DELETE /v1/meetings/{id}`, `DELETE /v1/meetings` | Meetings |
| `POST /v1/signIn` | Login (Demo-User: `joe` / `1234`) |
| `GET /actuator/health` | Health-Check (Spring Boot Actuator) — prüft u. a. die Datenbankverbindung |

## Projektstruktur

```
src/main/java/ch/bbw/trips/   Applikationscode (Controller, Entities, Repositories, DTOs)
docker/docker-compose.yml     MariaDB + phpMyAdmin für die lokale Entwicklung
docs/exercises/               CI/CD-Übungen (EX-01 EC2, EX-02 Docker Hub, EX-03 ECS/RDS)
docs/reviews/                 Code-Reviews
docs/future/                  Planungsentscheidungen für RefCard 03–05
```

## Für Studierende

Der Tag [`v1-start`](../../releases/tag/v1-start) markiert den Ausgangspunkt für die Übungen in `docs/exercises/` — bewusst **ohne** `Dockerfile`, `.dockerignore`, `.github/workflows/deploy.yml` und `task-definition.json`, da ihr genau diese Dateien in EX-01/EX-02/EX-03 selbst erstellt.
