// Backend addresses.
// - NEXT_PUBLIC_* variables override everything (optional).
// - Production builds (Vercel) use the Render URLs below.
// - Local dev (npm run dev) uses your own machine.
const isProd = process.env.NODE_ENV === "production";

export const API_URL =
  process.env.NEXT_PUBLIC_API_URL ??
  (isProd ? "https://nagarfix-api.onrender.com" : "http://localhost:8080");
export const ML_URL =
  process.env.NEXT_PUBLIC_ML_URL ??
  (isProd ? "https://nagarfix-ml.onrender.com" : "http://localhost:8000");
