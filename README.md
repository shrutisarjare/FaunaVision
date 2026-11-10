# Endangered Species Identification & Population Analytics

A web application for identifying endangered species from **images, video, audio, or a
written description**, and for exploring their **population history, forecast, and
conservation status** across India, pan-Asia, and the world.

---

## Architecture

```
Browser (React 19 + Vite 7 + Tailwind, :5173)
   │
   └── every request ──────────────► Spring Boot  (backend/, :8080)
                                     ├── Spring MVC controllers  (all legacy routes)
                                     ├── JPA + Hibernate → H2 file database
                                     ├── ONNX Runtime  → CLIP ViT-B/32 (+ MobileNetV2, YAMNet)
                                     └── JavaCV FFmpeg → video/audio decoding
```

A single Spring Boot service now replaces the former FastAPI + Express split. The
React frontend is unchanged apart from one detail: every network call goes through
`src/api.js`, which reads `VITE_API_URL` — unset, the dev server talks to
`http://localhost:8080` and a production build calls its own origin.

In production one container serves both halves: the `npm run build` output is copied into
`src/main/resources/static`, and Spring Boot answers `/` plus every client-side route with
`index.html`, so the app and the API share an origin (no CORS, no API URL to configure).

Identification is open-vocabulary: CLIP's text tower encodes every species name in the
catalogue once, and an upload is scored against those vectors. MobileNetV2 and YAMNet
remain as fallbacks, so the API still starts and identifies when the CLIP files are
absent.

---

## Project structure

```
endangered-species/
├── backend/                             Spring Boot 3.5 · Java 21
│   ├── pom.xml
│   ├── scripts/fetch-models.ps1         downloads the ONNX models
│   ├── scripts/bench-identify.ps1       measures top-1/top-5 accuracy
│   ├── src/main/java/com/endangered/species/
│   │   ├── SpeciesApplication.java      entry point
│   │   ├── config/                      CORS + first-run data seeding
│   │   ├── domain/                      JPA entities and enums
│   │   ├── repo/                        Spring Data repositories
│   │   ├── service/                     catalogue, forecast, auth, admin, wiki images
│   │   ├── ml/                          CLIP + ImageNet/YAMNet classifiers, species index
│   │   └── web/                        REST controllers, DTOs, error handling
│   ├── src/main/resources/
│   │   ├── application.properties
│   │   ├── ml/audioset_labels.txt       AudioSet class names for YAMNet
│   │   └── seed/animals.xlsx            imported into H2 on the first start
│   ├── data/                            H2 database + CLIP embedding cache
│   └── src/test/java/                   unit + MVC slice tests
│
└── frontend/                            React 19 · Vite · Tailwind
    ├── src/api.js                       the only place that knows the API host
    ├── src/component/                   27 components
    └── .env.example                     VITE_API_URL
```

`backend-python-legacy/` holds the previous Python/Node implementation for reference.
It is git-ignored and not part of the running system.

---

## Prerequisites

| Tool | Version |
|---|---|
| JDK | 21 or newer (the build targets 21) |
| Maven | 3.9+ (IntelliJ's bundled Maven works) |
| Node.js | 20+ |

No Python, no MongoDB, and no separate FFmpeg install are required — the FFmpeg
natives come from Maven.

---

## Running locally

**1. Download the ML models (once)**

```powershell
cd backend
powershell -ExecutionPolicy Bypass -File scripts/fetch-models.ps1
```

This writes the CLIP export (`vision_model.onnx`, `text_model.onnx`, `vocab.json`,
`merges.txt` into `backend/models/clip/`) plus the MobileNetV2/YAMNet fallbacks into
`backend/models/` — about 160 MB in total.

On the first start the catalogue species are encoded by CLIP's text tower and cached in
`backend/data/species-embeddings.json`, so later starts load the index instead of
re-encoding it. Delete that file, or change the catalogue or a model, to force a
rebuild. With no models at all the API still starts: catalogue, search, analytics and
admin work, and only the `/identify-*` endpoints answer `503` with a message telling
you to run the script.

**2. Start the API — port 8080**

```powershell
cd backend
mvn spring-boot:run
```

The first start imports `src/main/resources/seed/animals.xlsx` into `backend/data/speciesdb`
(about 12 000 records, roughly 15 s). Later starts reuse the database.

If port 8080 is already taken, set another one:

```powershell
$env:PORT = "8090"; mvn spring-boot:run
```

**3. Start the frontend — port 5173**

```powershell
cd frontend
npm install
npm run dev
```

Open <http://localhost:5173>. If the API runs on a different port, copy
`frontend/.env.example` to `frontend/.env` and change `VITE_API_URL`.

---

## Deploying (Render, one container)

`Dockerfile` builds both halves into a single image: the React bundle becomes the jar's
static resources, and the ONNX models are downloaded during the build, so nothing large
is committed.

1. Push the repository to GitHub.
2. In Render choose **New → Blueprint** and select the repo. `render.yaml` describes the
   service: Docker, free plan, health check on `/api/health`, a generated `JWT_SECRET`.
   Without a blueprint, use **New → Web Service**, runtime **Docker**, Dockerfile
   `./Dockerfile`.
3. The first build takes a few minutes (Maven dependencies plus ~176 MB of models).

Any other Docker host works the same way:

```powershell
docker build -t faunavision .
docker run -p 8080:8080 -e JWT_SECRET=change-me faunavision
```

To reproduce the production shape locally:

```powershell
cd frontend; npm run build
Copy-Item dist ..\backend\src\main\resources\static -Recurse -Force
cd ..\backend; mvn package
java -jar target\species-api.jar
```

Things that behave differently once deployed:

- **The free plan has no disk.** `app_user` and `contact_message` live in H2 inside the
  container and are recreated on every deploy or restart, so the admin account has to be
  registered again after one. Add a disk and set `APP_DB_PATH=/var/data/speciesdb` (see
  the comments in `render.yaml`) to keep them, and to keep the model warm-up from
  repeating.
- **A cold start takes ~30 s**, because the free instance sleeps after 15 minutes of
  inactivity and the catalogue re-seeds itself on the way up.
- **512 MB is enough, but not by much.** With CLIP loaded the process settles around
  340 MB, which is why the container caps the Java heap at 65% of the limit. Drop the
  CLIP files from the image to fall back to MobileNetV2 if you ever need it smaller.
- **`CLIENT_ORIGIN` is only for split deployments.** With the app and the API on one
  origin, CORS never comes into play.

---

## Configuration

Everything is read from `backend/src/main/resources/application.properties`, which
in turn honours environment variables (see `backend/.env.example`):

| Variable | Default | Purpose |
|---|---|---|
| `PORT` | `8080` | HTTP port |
| `CLIENT_ORIGIN` | `http://localhost:5173` | comma-separated CORS origins |
| `JWT_SECRET` | dev placeholder | HMAC key for issued tokens |
| `ML_MODEL_DIR` | `models` | directory holding the `.onnx` files |
| `APP_DB_PATH` | `./data/speciesdb` | H2 file without the extension; point it at a mounted volume to survive restarts |

The H2 database lives at `backend/data/speciesdb.mv.db`. Delete that file to force a
re-import on the next start. The CLIP embedding cache sits next to it in
`backend/data/species-embeddings.json`; both `data/` and `models/` are git-ignored.

---

## API reference

`region ∈ {india, panasia, global}` · `category ∈ {terrestrial, aquatic, aerial}`

### Identification

| Method | Path | Purpose |
|---|---|---|
| POST | `/identify-image` | CLIP ranks the photo against every catalogue species |
| POST | `/identify-video` | 8 sampled frames averaged; a matching soundtrack re-ranks near ties |
| POST | `/identify-audio` | YAMNet names the group; the catalogue supplies the candidate species |
| POST | `/identify-text` | keyword shortcuts, then a catalogue search (see the note below) |

Every identification returns the same envelope:

```json
{
  "species": "Bengal Tiger",
  "image": "https://upload.wikimedia.org/...",
  "results": [{ "name": "Bengal Tiger", "scientific_name": "Panthera tigris", "location": "India", "status": "Endangered", "image": "..." }],
  "matches": [
    { "name": "Bengal Tiger", "scientific_name": "Panthera tigris tigris", "confidence": 0.366 },
    { "name": "Indochinese Tiger", "scientific_name": "Panthera tigris corbetti", "confidence": 0.157 }
  ]
}
```

`matches` is the ranked candidate list (`confidence` is a softmax over the CLIP
similarities), and `results` are the matching catalogue rows. An audio upload adds the
reasoning, because there the model deliberately stops at a group:

```json
{
  "species": "Great Indian Bustard",
  "matches": [{ "name": "Great Indian Bustard", "scientific_name": "Ardeotis nigriceps", "confidence": 0.2 }],
  "evidence": "Heard Bird (30%). A sound cannot tell the species of a group apart, so these candidates are ordered by conservation status."
}
```

Optional keys are omitted rather than sent as `null`, so the legacy consumers see the
same payload they always did.

### Catalogue and analytics

| Method | Path | Purpose |
|---|---|---|
| GET | `/search/{animal}` | global species search |
| GET | `/api/animal/{region}/{category}/{state}/{animal}` | region-scoped search |
| GET | `/analysis/{region}/{category}/{state}/{animal}` | explore-flow analytics |
| GET | `/detection/{animal}` | population history, forecast, trend, risk |
| GET | `/detection/{animal}/{regionType}/{location}` | same, filtered by state/country/continent |
| GET | `/detection-locations/{animal}/{regionType}` | dropdown options for the filter |
| GET | `/api/health` | liveness probe; what the host polls |

### User data and quiz

These four paths sit under `/api` because `/history`, `/favourites`, `/quiz` and
`/animal/...` are also pages of the React app; without the prefix a browser refresh would
return JSON instead of the page.

| Method | Path | Purpose |
|---|---|---|
| GET/POST | `/api/history` | recently viewed species |
| GET/POST/DELETE | `/api/favourites` | bookmarked species |
| GET | `/api/quiz` | questions built from history + favourites |

### Accounts and contact

| Method | Path | Purpose |
|---|---|---|
| POST | `/api/auth/register` | create an account; answers `201`, and the app then sends the user to the login form |
| POST | `/api/auth/login` | authenticate, returns token + user; the chosen role must match the account in both directions |
| GET | `/api/auth/me` | the account behind `Authorization: Bearer <token>`, `401` when it is stale |
| POST | `/login` | older login shape kept for compatibility |
| PUT | `/update-profile` | rename / change e-mail |
| POST | `/api/contact` | store a contact message |

### Administration

| Method | Path | Purpose |
|---|---|---|
| GET | `/admin/species` | every record as a flat row |
| GET | `/admin/sheet/{region}/{category}` | one region/category sheet |
| POST | `/admin/upload-excel` | replace the whole catalogue |
| POST | `/admin/upload/{region}/{category}` | append rows to one sheet |
| PUT | `/admin/update/{region}/{category}/{animal}` | update a record |
| DELETE | `/admin/delete/{region}/{category}/{animal}` | delete matching records |
| POST | `/admin/add/{region}/{category}` | add a record |

---

## Identification accuracy

Measured with `backend/scripts/bench-identify.ps1`, which downloads 22 labelled photos
from Wikipedia and scores the API's answer against the expected catalogue name:

| Input | Result |
|---|---|
| Photo, top-1 | **15 / 22 (68%)** |
| Photo, top-5 | **22 / 22 (100%)** |
| Photo latency | ~230 ms warm, ~1 s cold |
| Video (8 frames, 6 MB webm) | Bengal Tiger, ~1.1 s warm |

Every miss was a congener rather than a different animal: African Forest vs Asian
elephant (28.7% vs 28.5%), the four leopards (22.7 / 22.5 / 19.8%), Asiatic vs African
lion, plains vs mountain zebra. The right answer was still second or third, which is why
the ranked list matters more than the single headline name. Pushing past that needs a
larger vision tower or fine-tuning; it is not a prompt-engineering problem. Multi-crop
test-time augmentation was tried and **made accuracy worse**, so it was removed.

Audio is measured separately because it cannot reach species level — see the notes below.

---

## Data model

`animals.xlsx` — historically the system of record — is still the import format. On the
first start it is read once and loaded into relational tables:

```
species_record   id, region, category, animal_name, animal_type,
                 scientific_name, state, habitat_location, status
population_entry id, record_id, year_value, population
user_account     id, name, email, password_hash, role
contact_message  id, name, email, message, created_at
history_entry    id, animal_name, source, viewed_at
favourite        id, animal_name, added_at
```

The workbook's nine `{region}_{category}` sheets become `region` + `category` on each
row. The `state` column doubles as state, country or continent depending on the sheet:
`GeoHierarchy` resolves the three levels, so pan-Asia and global filtering actually
return results (the Python version only understood Indian states).

---

## Notes and limitations

- **Identification is zero-shot, not a trained endangered-species classifier.** CLIP
  (ViT-B/32) compares an upload with the catalogue's own names, so it can answer for any
  species in the workbook, but it was not fine-tuned on wildlife. Without the CLIP files
  the image path falls back to MobileNetV2's 1000 ImageNet labels.
- **A description is not ranked by CLIP.** CLIP's text vectors are anisotropic enough
  (unrelated species sit at cosine ≈ 0.95) that text-to-text scoring is noise, so
  `/identify-text` keeps the keyword chain (`roar`, `stripe`, `trunk`, `horn`) and then
  looks the result up in the catalogue.
- **Audio cannot name a species.** YAMNet works on AudioSet classes such as "Roaring
  cats" or "Bird vocalization", so the API returns that group's candidate species
  ordered by conservation status and says so in `evidence`. Only groups the catalogue
  actually models are used: `Owl` maps to the four owls, not to all 54 birds, and a
  trumpet sound is reported as inconclusive because AudioSet has no elephant class.
- **A video's soundtrack only breaks near ties.** A matching group adds 0.015 to a
  species' cosine, so it reorders candidates that were already close and never overrules
  a confident visual match.
- **Population forecasting** is an ordinary least squares fit over six biennial points
  (2014–2024), extrapolated to 2026 and 2028 — the same maths as the previous
  scikit-learn `LinearRegression`.
- **History and favourites are global**, not per-user, exactly as before.
- **Only `/api/auth/me` requires a token.** The token is issued at login/registration and
  stored by the frontend, which uses it to detect a stale session; the catalogue, admin
  and history endpoints are still open, matching the original behaviour.
- **Wikipedia images** are fetched per species through the REST summary API and cached
  in memory for the lifetime of the process; the JSON cache file is gone.
