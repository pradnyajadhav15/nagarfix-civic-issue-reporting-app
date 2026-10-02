"use client";

import { useEffect, useState } from "react";
import { API_URL, ML_URL } from "@/lib/config";

type State = "checking" | "up" | "down";

function useServiceStatus(url: string) {
  const [state, setState] = useState<State>("checking");
  const [ms, setMs] = useState<number | null>(null);
  const [seconds, setSeconds] = useState(0);

  useEffect(() => {
    let active = true;
    const controller = new AbortController();
    const started = performance.now();
    const tick = setInterval(
      () => setSeconds(Math.floor((performance.now() - started) / 1000)),
      1000,
    );
    const timeout = setTimeout(() => controller.abort(), 120_000);

    fetch(url, { signal: controller.signal, cache: "no-store" })
      .then((res) => {
        if (!active) return;
        setState(res.ok ? "up" : "down");
        setMs(Math.round(performance.now() - started));
      })
      .catch(() => {
        if (active) setState("down");
      })
      .finally(() => {
        clearInterval(tick);
        clearTimeout(timeout);
      });

    return () => {
      active = false;
      clearInterval(tick);
      clearTimeout(timeout);
      controller.abort();
    };
  }, [url]);

  return { state, ms, seconds };
}

function StatusRow({ label, url }: { label: string; url: string }) {
  const { state, ms, seconds } = useServiceStatus(url);
  const text =
    state === "up"
      ? `Online (${ms} ms)`
      : state === "down"
        ? "Not reachable"
        : seconds < 3
          ? "Checking..."
          : `Waking up... ${seconds}s`;
  const dot =
    state === "up"
      ? "bg-emerald-500"
      : state === "down"
        ? "bg-red-500"
        : "bg-amber-400 animate-pulse";

  return (
    <li className="flex items-center justify-between gap-4">
      <span className="flex items-center gap-2">
        <span className={`inline-block h-2.5 w-2.5 rounded-full ${dot}`} />
        {label}
      </span>
      <span className="text-slate-600">{text}</span>
    </li>
  );
}

export default function ServiceStatus() {
  return (
    <section className="rounded-lg border border-slate-200 bg-white p-4">
      <h2 className="mb-2 text-sm font-semibold text-slate-700">System status</h2>
      <ul className="space-y-1 text-sm">
        <StatusRow label="API (Spring Boot)" url={`${API_URL}/api/hello`} />
        <StatusRow label="Database (Neon + PostGIS)" url={`${API_URL}/api/db`} />
        <StatusRow label="AI service (FastAPI)" url={`${ML_URL}/health`} />
      </ul>
      <p className="mt-2 text-xs text-slate-500">
        Free servers sleep when idle, so the first check can take about a minute.
      </p>
    </section>
  );
}
