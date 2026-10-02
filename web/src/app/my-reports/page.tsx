"use client";

import Link from "next/link";
import { useEffect, useState } from "react";
import { useAuth } from "@/components/AuthProvider";
import StatusBadge from "@/components/StatusBadge";
import { api } from "@/lib/api";
import { categoryLabel, cloudinaryUrl, formatDate, type Issue } from "@/lib/issues";

export default function MyReportsPage() {
  const { user, loading } = useAuth();
  const [issues, setIssues] = useState<Issue[] | null>(null);
  const [error, setError] = useState("");

  useEffect(() => {
    if (!user) return;
    let active = true;
    api<Issue[]>("/api/issues/mine")
      .then((list) => {
        if (active) setIssues(list);
      })
      .catch((e) => {
        if (active) setError(e instanceof Error ? e.message : "Could not load your reports");
      });
    return () => {
      active = false;
    };
  }, [user]);

  if (loading) {
    return <main className="mx-auto max-w-3xl px-6 py-10 text-slate-600">Loading...</main>;
  }

  if (!user) {
    return (
      <main className="mx-auto max-w-3xl space-y-3 px-6 py-10">
        <p>Log in to see your reports.</p>
        <Link href="/login?next=/my-reports" className="text-emerald-700 underline">
          Log in
        </Link>
      </main>
    );
  }

  return (
    <main className="mx-auto max-w-3xl space-y-4 px-6 py-8">
      <div className="flex items-center justify-between gap-3">
        <h1 className="text-2xl font-bold">My reports</h1>
        <Link href="/report" className="rounded bg-emerald-700 px-3 py-1.5 text-sm font-semibold text-white hover:bg-emerald-800">
          + New report
        </Link>
      </div>
      {error && <p className="text-sm text-red-600">{error}</p>}
      {!issues && !error && <p className="text-slate-600">Loading your reports...</p>}
      {issues && issues.length === 0 && (
        <p className="text-slate-600">
          No reports yet. Spotted a problem?{" "}
          <Link href="/report" className="text-emerald-700 underline">
            Report it
          </Link>
          .
        </p>
      )}
      <ul className="space-y-3">
        {issues?.map((i) => {
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
                      {cat.en} <span className="font-normal text-slate-500">{cat.mr}</span>
                    </span>
                    <StatusBadge status={i.status} />
                  </div>
                  <p className="text-sm text-slate-600">
                    {i.wardName ?? "Unknown zone"} - {formatDate(i.createdAt)}
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
