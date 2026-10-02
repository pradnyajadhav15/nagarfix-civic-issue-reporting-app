"use client";

import { useEffect, useMemo, useState } from "react";
import type { FeatureCollection } from "geojson";
import IssuesMapLoader from "@/components/IssuesMapLoader";
import { api } from "@/lib/api";
import { CATEGORIES, FIXED_STATUSES, OPEN_STATUSES, STATUS, STATUS_COLOR, type Issue } from "@/lib/issues";

type Show = "all" | "open" | "fixed";

const chip = (active: boolean) =>
  `rounded-full border px-3 py-1 text-sm ${
    active ? "border-emerald-700 bg-emerald-700 text-white" : "border-slate-300 bg-white hover:bg-slate-50"
  }`;

export default function MapPage() {
  const [issues, setIssues] = useState<Issue[] | null>(null);
  const [zones, setZones] = useState<FeatureCollection | null>(null);
  const [error, setError] = useState("");
  const [category, setCategory] = useState<string>("ALL");
  const [show, setShow] = useState<Show>("all");
  const [withDemo, setWithDemo] = useState(true);

  useEffect(() => {
    let active = true;
    api<Issue[]>("/api/issues?limit=1000")
      .then((list) => {
        if (active) setIssues(list);
      })
      .catch((e) => {
        if (active) setError(e instanceof Error ? e.message : "Could not load reports");
      });
    api<FeatureCollection>("/api/wards")
      .then((fc) => {
        if (active) setZones(fc);
      })
      .catch(() => {
        // zone outlines are optional
      });
    return () => {
      active = false;
    };
  }, []);

  const pool = useMemo(() => (issues ?? []).filter((i) => withDemo || !i.demo), [issues, withDemo]);
  const visible = useMemo(
    () =>
      pool.filter(
        (i) =>
          (category === "ALL" || i.category === category) &&
          (show === "all" ||
            (show === "open" ? OPEN_STATUSES.includes(i.status) : FIXED_STATUSES.includes(i.status))),
      ),
    [pool, category, show],
  );

  const total = pool.length;
  const open = pool.filter((i) => OPEN_STATUSES.includes(i.status)).length;
  const fixed = pool.filter((i) => FIXED_STATUSES.includes(i.status)).length;
  const demoCount = (issues ?? []).filter((i) => i.demo).length;

  return (
    <main className="mx-auto max-w-5xl space-y-4 px-6 py-8">
      <h1 className="text-3xl font-bold">All reports</h1>

      <div className="grid grid-cols-3 gap-3">
        {[
          { label: "Total", value: total },
          { label: "Open", value: open },
          { label: "Fixed", value: fixed },
        ].map((s) => (
          <div key={s.label} className="rounded-lg border border-slate-200 bg-white p-3 text-center">
            <div className="text-2xl font-bold">{issues ? s.value : "-"}</div>
            <div className="text-xs uppercase tracking-wide text-slate-500">{s.label}</div>
          </div>
        ))}
      </div>

      <div className="flex flex-wrap gap-2">
        <button type="button" className={chip(category === "ALL")} onClick={() => setCategory("ALL")}>
          All types
        </button>
        {CATEGORIES.map((c) => (
          <button key={c.value} type="button" className={chip(category === c.value)} onClick={() => setCategory(c.value)}>
            {c.en} <span className="opacity-70">{c.mr}</span>
          </button>
        ))}
      </div>
      <div className="flex flex-wrap gap-2">
        {(["all", "open", "fixed"] as const).map((s) => (
          <button key={s} type="button" className={chip(show === s)} onClick={() => setShow(s)}>
            {s === "all" ? "All statuses" : s === "open" ? "Open" : "Fixed"}
          </button>
        ))}
      </div>

      {error && <p className="text-sm text-red-600">{error}</p>}
      {!issues && !error && (
        <p className="text-sm text-slate-600">Loading reports (the free server can take up to a minute to wake)...</p>
      )}
      {demoCount > 0 && (
        <label className="flex items-center gap-2 text-sm text-slate-700">
          <input type="checkbox" checked={withDemo} onChange={(e) => setWithDemo(e.target.checked)} />
          Include {demoCount} demo reports (made-up data, marked DEMO)
        </label>
      )}
      {issues && (
        <p className="text-sm text-slate-600">
          Showing {visible.length} of {total} reports. Click a dot for details.
        </p>
      )}

      <IssuesMapLoader issues={visible} zones={zones} />

      <div className="flex flex-wrap gap-x-4 gap-y-2 text-xs text-slate-600">
        {Object.keys(STATUS).map((s) => (
          <span key={s} className="flex items-center gap-1.5">
            <span className="inline-block h-3 w-3 rounded-full" style={{ backgroundColor: STATUS_COLOR[s] }} />
            {STATUS[s].label}
          </span>
        ))}
      </div>
    </main>
  );
}
