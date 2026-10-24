# QuickDeliver – Intelligent Optimized Delivery System

Frontend: **HTML + CSS + JavaScript** (`frontend/`) · Backend: **Java 17 + Spring Boot** (`backend/`) · Database: **MongoDB**

```
frontend/public/    pages, css, js, images, _redirects   → deploy to Netlify (or served by the Java app locally)
backend/            Spring Boot REST API + seeder
Dockerfile          builds the Java backend for Render / Railway / Fly.io
netlify.toml        tells Netlify to publish frontend/public
```

## 1. Run everything locally
Needs JDK 17+, Maven 3.9+ and MongoDB.
```bash
cd backend
cp ../.env.example .env                                # edit JWT_SECRET, ADMIN_EMAIL, ADMIN_PASSWORD
mvn spring-boot:run -Dspring-boot.run.profiles=seed    # once: admin, sample agents, 26 products
mvn spring-boot:run                                    # open http://localhost:5000
```
The Java app serves the pages from `frontend/public` too, so locally you only run this one command.

## 2. Deploy: frontend on Netlify + backend on a Java host
**Netlify can only host the static frontend; it cannot run a Java server.** So the Java backend goes on a Java-capable host
(Render, Railway, Fly.io …) and Netlify forwards `/api/*` to it.

### Step A – database (MongoDB Atlas, free)
Create a free M0 cluster, add a database user, allow network access (0.0.0.0/0 for a student project), and copy the
connection string, e.g. `mongodb+srv://USER:PASSWORD@cluster0.xxxxx.mongodb.net/quickdeliver`.
Seed it once from your PC: put that string in `backend/.env` as `MONGO_URI`, then run the `seed` command from section 1.

### Step B – backend on Render (free)
1. Push this whole folder to GitHub.
2. Render → New → Web Service → pick the repo → Runtime **Docker** (it finds the `Dockerfile`). Or use *Blueprint* with `render.yaml`.
3. Environment variables: `MONGO_URI` (Atlas string), `JWT_SECRET` (long random text), `JWT_EXPIRE=7d`.
4. Deploy, then copy the URL, e.g. `https://quickdeliver-api.onrender.com`. Check `<url>/api/products` returns JSON.
Free Render services sleep when idle; the first request after a pause can take about a minute.

### Step C – frontend on Netlify
1. Open `frontend/public/_redirects` and replace `https://YOUR-BACKEND-URL` with your Render URL (keep `/api/:splat`).
2. Netlify → Add new site → Import from Git → choose the repo (`netlify.toml` already sets the publish folder `frontend/public`; no build command).
   *Or* drag-and-drop the `frontend/public` folder onto Netlify.
3. Open the Netlify URL – pages, login, products, orders and the admin/agent panels all work through the proxy.

**Alternative (no proxy):** in `frontend/public/js/config.js` set `window.API_BASE = 'https://your-backend.onrender.com'`
and on the backend set `CORS_ALLOWED_ORIGINS=https://your-site.netlify.app`.

## Notes
* Product images: each product's image link is used if it loads; otherwise `js/product-images.js` looks up a photo by product name on
  Wikipedia (free, cached in the browser) and finally falls back to the bundled illustrations in `frontend/public/images/products/`.
* Backend code map: `controller/` (REST endpoints), `model/` (MongoDB documents), `security/` (JWT + `@Auth`),
  `service/`, `repository/`, `config/` (web config, error handling, seeder).
* Login tokens are kept in the browser (localStorage); data lives in MongoDB (`users`, `products`, `orders`).
* Kept from the original app: order totals are trusted from the client, agents can open any order by id.
