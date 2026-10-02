"use client";

import Link from "next/link";
import { useEffect, useMemo, useState } from "react";
import { useAuth } from "@/components/AuthProvider";
import { DeadlineBadge, DemoBadge } from "@/components/Badges";
import StatusBadge from "@/components/StatusBadge";
import { api } from "@/lib/api";
import { categoryLabel, cloudinaryUrl, timeAgo, type Issue } from "@/lib/issues";

type Tab = "todo" | "working" | "waiting" | "done";
type WardOption = { code: string; name: string };
type WardCollection = { features: { properties: WardOption }[] };

const TABS: { key: Tab; label: string; statuses: string[] }[] = [
  { key: "todo", label: "New and reopened", statuses: ["SUBMITTED", "REOPENED"] },
  { key: "working", label: "Being fixed", statuses: ["ASSIGNED", "IN_PROGRESS"] },
  { key: "waiting", label: "Waiting for citizen", statuses: ["RESOLVED"] },
  { key: "done", label: "Done", statuses: ["CLOSED", "REJECTED", "DUPLICATE"] },
];

const time = (iso: string | null) => (iso ? new Date(iso).getTime() : 0);

/** Open work first: overdue reports, then the oldest. Finished ones: most recent first. */
function sortFor(tab: Tab, list: Issue[]): Issue[] {
  const copy = [...list];
  if (tab === "done") return copy.sort((a, b) => time(b.updatedAt) - time(a.updatedAt));
  if (tab === "waiting") return copy.sort((a, b) => time(a.resolvedAt) - time(b.resolvedAt));
  return copy.sort((a, b) => Number(b.overdue) - Number(a.overdue) || time(a.createdAt) - time(b.createdAt));
}

export default function OfficerPage() {
  const { user, loading } = useAuth();
  const [zones, setZones] = useState<WardOption[]>([]);
  const [zoneChoice, setZoneChoice] = useState("");
  const [data, setData] = useState<{ key: string; list: Issue[] } | null>(null);
  const [error, setError] = useState("");
  const [tab, setTab] = useState<Tab>("todo");

  const isStaff = user?.role === "OFFICER" || user?.role === "ADMIN";
  const ward = user?.role === "OFFICER" ? (user.wardCode ?? "") : zoneChoice;
  const demo = Boolean(user?.demo);
  const key = `${ward}|${demo}`;

  useEffect(() => {
    if (!isStaff) return;
    let active = true;
    api<WardCollection>("/api/wards")
      .then((fc) => {
        if (active) setZones(fc.features.map((f) => f.properties));
      })
      .catch(() => {
        // the zone names are only a nicety
      });
    return () => {
      active = false;
    };
  }, [isStaff]);

  useEffect(() => {
    if (!isStaff) return;
    let active = true;
    const params = new URLSearchParams({ limit: "1000", demo: String(demo) });
    if (ward) params.set("ward", ward);
    api<Issue[]>(`/api/issues?${params}`)
      .then((list) => {
        if (active) setData({ key, list });
      })
      .catch((e) => {
        if (active) setError(e instanceof Error ? e.message : "Could not load the reports");
      });
    return () => {
      active = false;
    };
  }, [isStaff, ward, demo, key]);

  const issues = data?.key === key ? data.list : null;
  const byTab = useMemo(() => {
    const groups = new Map<Tab, Issue[]>();
    for (const t of TABS) {
      groups.set(t.key, sortFor(t.key, (issues ?? []).filter((i) => t.statuses.includes(i.status))));
    }
    return groups;
  }, [issues]);

  if (loading) {
    return <main className="mx-auto max-w-4xl px-6 py-10 text-slate-600">Loading...</main>;
  }
  if (!user) {
    return (
      <main className="mx-auto max-w-4xl space-y-3 px-6 py-10">
        <p>Log in as a ward officer or admin to see your zone&apos;s reports.</p>
        <Link href="/login?next=/officer" className="text-emerald-700 underline">
          Log in
        </Link>
      </main>
    );
  }
  if (!isStaff) {
    return (
      <main className="mx-auto max-w-4xl px-6 py-10 text-slate-600">
        This page is for ward officers and admins. Your own reports are on{" "}
        <Link href="/my-reports" className="text-emerald-700 underline">
          My reports
        </Link>
        .
      </main>
    );
  }

  const zoneName = zones.find((z) => z.code === ward)?.name;
  const shown = byTab.get(tab) ?? [];
  const overdueCount = (issues ?? []).filter((i) => i.overdue).length;

  return (
    <main className="mx-auto max-w-4xl space-y-5 px-6 py-8">
      <div className="flex flex-wrap items-end justify-between gap-3">
        <div>
          <h1 className="text-3xl font-bold">My zone</h1>
          <p className="text-sm text-slate-600">
            {user.role === "OFFICER"
              ? `${ward}${zoneName ? ` - ${zoneName}` : ""}`
              : "As an admin you can work on any zone."}
          </p>
        </div>
        {user.role === "ADMIN" && (
          <select
            value={zoneChoice}
            onChange={(e) => setZoneChoice(e.target.value)}
            className="rounded border border-slate-300 px-3 py-2 text-sm"
          >
            <option value="">All zones</option>
            {zones.map((z) => (
              <option key={z.code} value={z.code}>
                {z.code} - {z.name}
              </option>
            ))}
          </select>
        )}
      </div>

      {demo && (
        <p className="rounded-lg border border-violet-200 bg-violet-50 p-3 text-sm text-violet-900">
          You are using a <strong>demo account</strong>. Open any report below and try the buttons: take it, start
          work, mark it fixed with an after-photo. Demo data resets every day.
        </p>
      )}

      <div className="grid grid-cols-2 gap-2 sm:grid-cols-4">
        {TABS.map((t) => (
          <button
            key={t.key}
            type="button"
            onClick={() => setTab(t.key)}
            className={`rounded-lg border p-3 text-left ${
              tab === t.key ? "border-emerald-700 bg-emerald-50" : "border-slate-200 bg-white hover:bg-slate-50"
            }`}
          >
            <div className="text-2xl font-bold">{issues ? (byTab.get(t.key)?.length ?? 0) : "-"}</div>
            <div className="text-xs font-semibold uppercase tracking-wide text-slate-500">{t.label}</div>
          </button>
        ))}
      </div>
      {overdueCount > 0 && (
        <p className="text-sm font-semibold text-red-700">
          {overdueCount} {overdueCount === 1 ? "report is" : "reports are"} past the deadline.
        </p>
      )}

      {error && <p className="text-sm text-red-600">{error}</p>}
      {!issues && !error && <p className="text-slate-600">Loading reports...</p>}
      {issues && shown.length === 0 && <p className="text-slate-600">Nothing here right now.</p>}

      <ul className="space-y-3">
        {shown.map((i) => {
          const cat = categoryLabel(i.category);
          return (
            <li key={i.id}>
              <Link
                href={`/issues/${i.id}`}
                className="flex gap-4 rounded-lg border border-slate-200 bg-white p-3 hover:border-emerald-600"
              >
                <img
                  src={cloudinaryUrl(i.photoUrl, "c_fill,w_160,h_120,q_auto,f_auto")}
                  alt=""
                  className="h-20 w-28 flex-none rounded object-cover"
                />
                <div className="min-w-0 flex-1 space-y-1">
                  <div className="flex flex-wrap items-center justify-between gap-2">
                    <span className="font-semibold">
                      #{i.id} {cat.en} <span className="font-normal text-slate-500">{cat.mr}</span>
                    </span>
                    <span className="flex flex-wrap items-center gap-1.5">
                      {i.demo && <DemoBadge />}
                      <DeadlineBadge issue={i} />
                      <StatusBadge status={i.status} />
                    </span>
                  </div>
                  <p className="text-sm text-slate-600">
                    Reported {timeAgo(i.createdAt)}
                    {!ward && i.wardName ? ` - ${i.wardName}` : ""}
                    {i.address ? ` - ${i.address}` : ""}
                  </p>
                  {i.description && <p className="truncate text-sm text-slate-500">{i.description}</p>}
                </div>
              </Link>
            </li>
          );
        })}
      </ul>
    </main>
  );
}
