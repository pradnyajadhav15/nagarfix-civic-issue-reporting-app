import { api } from "@/lib/api";

export type LatLng = { lat: number; lng: number };

export type Issue = {
  id: number;
  category: string;
  description: string | null;
  lat: number;
  lng: number;
  address: string | null;
  wardCode: string | null;
  wardName: string | null;
  wardNameMr: string | null;
  photoUrl: string;
  status: string;
  createdAt: string;
};

// Marathi labels are written as unicode escapes so this file stays plain ASCII.
export const CATEGORIES = [
  { value: "POTHOLE", en: "Pothole", mr: "\u0916\u0921\u094D\u0921\u093E" },
  { value: "GARBAGE", en: "Garbage", mr: "\u0915\u091A\u0930\u093E" },
  { value: "STREETLIGHT", en: "Streetlight", mr: "\u092A\u0925\u0926\u093F\u0935\u093E" },
  { value: "DRAINAGE", en: "Drainage", mr: "\u0917\u091F\u093E\u0930" },
  { value: "WATER_LEAK", en: "Water leak", mr: "\u092A\u093E\u0923\u0940 \u0917\u0933\u0924\u0940" },
  { value: "OTHER", en: "Other", mr: "\u0907\u0924\u0930" },
] as const;

export function categoryLabel(value: string): { en: string; mr: string } {
  const c = CATEGORIES.find((x) => x.value === value);
  return c ? { en: c.en, mr: c.mr } : { en: value, mr: "" };
}

export const STATUS: Record<string, { label: string; cls: string }> = {
  SUBMITTED: { label: "Submitted", cls: "bg-slate-100 text-slate-700" },
  ASSIGNED: { label: "Assigned", cls: "bg-blue-100 text-blue-800" },
  IN_PROGRESS: { label: "In progress", cls: "bg-amber-100 text-amber-800" },
  RESOLVED: { label: "Resolved - please confirm", cls: "bg-emerald-100 text-emerald-800" },
  CLOSED: { label: "Closed", cls: "bg-emerald-700 text-white" },
  REOPENED: { label: "Reopened", cls: "bg-orange-100 text-orange-800" },
  REJECTED: { label: "Rejected", cls: "bg-red-100 text-red-800" },
  DUPLICATE: { label: "Duplicate", cls: "bg-slate-200 text-slate-700" },
};

/** Marker colours on the map. */
export const STATUS_COLOR: Record<string, string> = {
  SUBMITTED: "#64748b",
  ASSIGNED: "#2563eb",
  IN_PROGRESS: "#d97706",
  RESOLVED: "#10b981",
  CLOSED: "#047857",
  REOPENED: "#ea580c",
  REJECTED: "#dc2626",
  DUPLICATE: "#94a3b8",
};

export const OPEN_STATUSES: readonly string[] = ["SUBMITTED", "ASSIGNED", "IN_PROGRESS", "REOPENED"];
export const FIXED_STATUSES: readonly string[] = ["RESOLVED", "CLOSED"];

/** Adds a Cloudinary transformation, e.g. "c_fill,w_160,h_120" for a thumbnail. */
export function cloudinaryUrl(url: string, transform: string): string {
  return url.includes("/image/upload/") ? url.replace("/image/upload/", `/image/upload/${transform}/`) : url;
}

export function formatDate(iso: string): string {
  return new Date(iso).toLocaleString("en-IN", { dateStyle: "medium", timeStyle: "short" });
}

/** Resizes a photo in the browser and re-encodes it as JPEG (this also drops hidden GPS/EXIF data). */
export async function compressImage(file: File, maxSize = 1280, quality = 0.8): Promise<Blob> {
  const bitmap = await createImageBitmap(file);
  const scale = Math.min(1, maxSize / Math.max(bitmap.width, bitmap.height));
  const width = Math.round(bitmap.width * scale);
  const height = Math.round(bitmap.height * scale);
  const canvas = document.createElement("canvas");
  canvas.width = width;
  canvas.height = height;
  const ctx = canvas.getContext("2d");
  if (!ctx) throw new Error("Could not process the photo");
  ctx.drawImage(bitmap, 0, 0, width, height);
  bitmap.close();
  return new Promise((resolve, reject) =>
    canvas.toBlob(
      (blob) => (blob ? resolve(blob) : reject(new Error("Could not process the photo"))),
      "image/jpeg",
      quality,
    ),
  );
}

type UploadSignature = {
  uploadUrl: string;
  apiKey: string;
  timestamp: number;
  folder: string;
  signature: string;
};

/** Gets a one-time signature from our API, then uploads the photo straight to Cloudinary. */
export async function uploadPhoto(photo: Blob): Promise<string> {
  const sig = await api<UploadSignature>("/api/uploads/signature", { method: "POST" });
  const form = new FormData();
  form.append("file", photo, "report.jpg");
  form.append("api_key", sig.apiKey);
  form.append("timestamp", String(sig.timestamp));
  form.append("folder", sig.folder);
  form.append("signature", sig.signature);
  const res = await fetch(sig.uploadUrl, { method: "POST", body: form });
  const data = await res.json().catch(() => null);
  if (!res.ok || !data || typeof data.secure_url !== "string") {
    throw new Error(data?.error?.message ?? "Photo upload failed - please try again");
  }
  return data.secure_url;
}
