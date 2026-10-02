"use client";

import { useRouter } from "next/navigation";
import { useEffect, useState } from "react";
import ReportMapLoader from "@/components/ReportMapLoader";
import { api, ApiError } from "@/lib/api";
import { CATEGORIES, compressImage, uploadPhoto, type Issue, type LatLng } from "@/lib/issues";

const SOLAPUR_CENTER: LatLng = { lat: 17.6599, lng: 75.9064 };

type ZoneHit = { code: string; name: string; nameMr: string | null };
type Zone = ZoneHit | "outside" | null;

export default function ReportForm() {
  const router = useRouter();
  const [category, setCategory] = useState<string>("POTHOLE");
  const [description, setDescription] = useState("");
  const [photo, setPhoto] = useState<Blob | null>(null);
  const [preview, setPreview] = useState<string | null>(null);
  const [position, setPosition] = useState<LatLng>(SOLAPUR_CENTER);
  const [located, setLocated] = useState(false);
  const [focusKey, setFocusKey] = useState(0);
  const [zone, setZone] = useState<Zone>(null);
  const [locMsg, setLocMsg] = useState("");
  const [error, setError] = useState("");
  const [step, setStep] = useState("");

  // Look up the zone whenever the pin moves (short delay so dragging does not flood the API)
  useEffect(() => {
    let active = true;
    const timer = setTimeout(() => {
      api<ZoneHit>(`/api/wards/locate?lat=${position.lat}&lng=${position.lng}`)
        .then((z) => {
          if (active) setZone(z);
        })
        .catch((e) => {
          if (active) setZone(e instanceof ApiError && e.status === 404 ? "outside" : null);
        });
    }, 400);
    return () => {
      active = false;
      clearTimeout(timer);
    };
  }, [position]);

  // Free the old preview image from memory when it changes
  useEffect(() => {
    return () => {
      if (preview) URL.revokeObjectURL(preview);
    };
  }, [preview]);

  async function onPhoto(e: React.ChangeEvent<HTMLInputElement>) {
    const file = e.target.files?.[0];
    if (!file) return;
    setError("");
    try {
      const blob = await compressImage(file);
      setPhoto(blob);
      setPreview(URL.createObjectURL(blob));
    } catch {
      setError("Could not read this photo. Please try a different one.");
    }
  }

  function locateMe() {
    if (!("geolocation" in navigator)) {
      setLocMsg("This browser cannot share location - drag the pin instead.");
      return;
    }
    setLocMsg("Finding your location...");
    navigator.geolocation.getCurrentPosition(
      (p) => {
        setPosition({ lat: p.coords.latitude, lng: p.coords.longitude });
        setLocated(true);
        setFocusKey((k) => k + 1);
        setLocMsg(`Location found (accurate to about ${Math.round(p.coords.accuracy)} m). Drag the pin if needed.`);
      },
      () => setLocMsg("Could not get your location. Allow location access, or click/drag the pin to the spot."),
      { enableHighAccuracy: true, timeout: 15000, maximumAge: 60000 },
    );
  }

  async function onSubmit(e: React.FormEvent<HTMLFormElement>) {
    e.preventDefault();
    setError("");
    if (!photo) {
      setError("Please add a photo of the problem.");
      return;
    }
    if (zone === "outside") {
      setError("The pin is outside Solapur. Move it to where the problem is.");
      return;
    }
    try {
      setStep("Uploading photo...");
      const photoUrl = await uploadPhoto(photo);
      setStep("Saving your report...");
      const issue = await api<Issue>("/api/issues", {
        method: "POST",
        body: JSON.stringify({ category, description, lat: position.lat, lng: position.lng, photoUrl }),
      });
      router.push(`/issues/${issue.id}?new=1`);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Could not submit the report");
      setStep("");
    }
  }

  const busy = step !== "";

  return (
    <form onSubmit={onSubmit} className="space-y-5">
      <section className="space-y-3 rounded-lg border border-slate-200 bg-white p-4">
        <h2 className="font-semibold">1. Photo</h2>
        <input
          type="file"
          accept="image/*"
          capture="environment"
          onChange={onPhoto}
          className="block w-full text-sm file:mr-3 file:rounded file:border-0 file:bg-emerald-700 file:px-3 file:py-2 file:font-semibold file:text-white"
        />
        {preview && <img src={preview} alt="Selected photo" className="max-h-64 rounded border border-slate-200" />}
        <p className="text-xs text-slate-500">
          The photo is resized and its hidden location data is removed before upload.
        </p>
      </section>

      <section className="space-y-3 rounded-lg border border-slate-200 bg-white p-4">
        <div className="flex flex-wrap items-center justify-between gap-2">
          <h2 className="font-semibold">2. Location</h2>
          <button
            type="button"
            onClick={locateMe}
            className="rounded border border-emerald-700 px-3 py-1.5 text-sm font-semibold text-emerald-800 hover:bg-emerald-50"
          >
            Use my location
          </button>
        </div>
        {locMsg && <p className="text-sm text-slate-600">{locMsg}</p>}
        <ReportMapLoader position={position} onChange={setPosition} focusKey={focusKey} />
        <p className="text-sm">
          {zone === "outside" ? (
            <span className="font-semibold text-red-600">Outside Solapur - move the pin.</span>
          ) : zone ? (
            <span>
              Zone: <strong>{zone.name}</strong>
              {zone.nameMr ? ` / ${zone.nameMr}` : ""}
            </span>
          ) : (
            <span className="text-slate-500">Checking zone...</span>
          )}
        </p>
        {!located && (
          <p className="text-xs text-slate-500">Tip: tap &quot;Use my location&quot;, or click / drag the pin on the map.</p>
        )}
      </section>

      <section className="space-y-3 rounded-lg border border-slate-200 bg-white p-4">
        <h2 className="font-semibold">3. What is the problem?</h2>
        <div className="grid grid-cols-2 gap-2 sm:grid-cols-3">
          {CATEGORIES.map((c) => (
            <label
              key={c.value}
              className={`cursor-pointer rounded border px-3 py-2 text-sm ${
                category === c.value ? "border-emerald-700 bg-emerald-50 font-semibold" : "border-slate-300"
              }`}
            >
              <input
                type="radio"
                name="category"
                value={c.value}
                checked={category === c.value}
                onChange={() => setCategory(c.value)}
                className="sr-only"
              />
              {c.en}
              <span className="block text-xs font-normal text-slate-500">{c.mr}</span>
            </label>
          ))}
        </div>
        <textarea
          maxLength={1000}
          rows={3}
          placeholder="Describe it (optional) - e.g. deep pothole near the bus stop"
          value={description}
          onChange={(e) => setDescription(e.target.value)}
          className="w-full rounded border border-slate-300 px-3 py-2 text-sm focus:border-emerald-600 focus:outline-none"
        />
      </section>

      {error && <p className="text-sm font-semibold text-red-600">{error}</p>}
      <button
        type="submit"
        disabled={busy || zone === "outside"}
        className="w-full rounded-lg bg-emerald-700 py-3 font-semibold text-white hover:bg-emerald-800 disabled:opacity-60"
      >
        {busy ? step : "Submit report"}
      </button>
      <p className="text-center text-xs text-slate-500">
        Independent student project - reports are not sent to Solapur Municipal Corporation.
      </p>
    </form>
  );
}
