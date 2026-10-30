# Daybook frontend

React + Vite frontend for the Spring Boot journal API.

## Run locally

1. Start the Spring Boot app on `http://localhost:8080`.
2. In this folder, run `npm install` and then `npm run dev`.
3. Open the URL printed by Vite (normally `http://localhost:5173`).

Vite proxies `/public`, `/journal`, `/user`, and `/auth` to the backend during development. Set `VITE_API_BASE_URL` when the API is hosted on a different origin. Google sign-in redirects through `/auth/google/login`; configure the Google OAuth redirect URI to match the backend's `GOOGLE_REDIRECT_URI`.

The UI uses the backend's current routes: `POST /public/signup`, `POST /public/login`, and authenticated CRUD under `/journal`. JWTs are kept in session storage and cleared when you sign out.
