// Backend addresses. In production these come from NEXT_PUBLIC_* variables
// set at build time; locally they fall back to the dev servers.
export const API_URL = process.env.NEXT_PUBLIC_API_URL ?? "http://localhost:8080";
export const ML_URL = process.env.NEXT_PUBLIC_ML_URL ?? "http://localhost:8000";
