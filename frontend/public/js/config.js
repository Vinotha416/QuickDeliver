/*
 * Where the Java backend lives.
 *
 *  - '' (default): API calls go to the same site. Locally that is the Spring Boot app itself; on Netlify the
 *    rules in /_redirects forward /api/* to your backend, so nothing else is needed.
 *  - 'https://your-backend.onrender.com': call the backend directly (no Netlify proxy). In that case also set
 *    CORS_ALLOWED_ORIGINS=https://your-site.netlify.app on the backend.
 */
window.API_BASE = '';
