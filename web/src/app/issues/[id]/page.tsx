"use client";

import Link from "next/link";
import { useParams } from "next/navigation";
import { useEffect, useState } from "react";
import ReportMapLoader from "@/components/ReportMapLoader";
import StatusBadge from "@/components/StatusBadge";
import { api } from "@/lib/api";
import { categoryLabel, cloudinaryUrl, formatDate, type Issue } from "@/lib/issues";

export default function IssuePage() {
  const params = useParams<{ id: string }>();
  const id = params?.id;
  const [issue, setIssue] = useState<Issue | null>(null);
  const [error, setError] = useState("");
  const [isNew, setIsNew] = useState(false);

  useEffect(() => {
    setIsNew(new URLSearchParams(window.location.search).has("new"));
  }, []);

  useEffect(() => {
    if (!id) return;
    let active = true;
    api<Issue>(`/api/issues/${id}`)
      .then((i) => {
        if (active) setIssue(i);
      })
      .catch((e) => {
        if (active) setError(e instanceof Error ? e.message : "Could not load this report");
      });
    return () => {
      active = false;
    };
  }, [id]);

  if (error) {
    return (
      <main className="mx-auto max-w-3xl space-y-3 px-6 py-10">
        <p className="text-red-600">{error}</p>
        <Link href="/my-reports" className="text-emerald-700 underline">
          Back to my reports
        </Link>
      </main>
    );
  }

  if (!issue) {
    return (
      <main className="mx-auto max-w-3xl px-6 py-10 text-slate-600">
        Loading report (the free server can take up to a minute to wake)...
      </main>
    );
  }

  const cat = categoryLabel(issue.category);

  return (
    <main className="mx-auto max-w-3xl space-y-5 px-6 py-8">
      {isNew && (
        <div className="rounded-lg border border-emerald-300 bg-emerald-50 p-4 text-sm text-emerald-900">
          Report submitted! It was routed to <strong>{issue.wardName ?? "your zone"}</strong>. Track it anytime on{" "}
          <Link href="/my-reports" className="underline">
            My reports
          </Link>
          .
        </div>
      )}
      <div className="flex flex-wrap items-center justify-between gap-3">
        <h1 className="text-2xl font-bold">
          {cat.en} <span className="text-lg font-normal text-slate-500">{cat.mr}</span>
        </h1>
        <StatusBadge status={issue.status} />
      </div>
      <p className="text-sm text-slate-600">
        Report #{issue.id} - {formatDate(issue.createdAt)} - {issue.wardName ?? "Unknown zone"}
        {issue.wardNameMr ? ` / ${issue.wardNameMr}` : ""}
      </p>
      <img
        src={cloudinaryUrl(issue.photoUrl, "c_limit,w_1000,q_auto,f_auto")}
        alt={`Photo of the reported ${cat.en.toLowerCase()}`}
        className="w-full rounded-lg border border-slate-200"
      />
      {issue.description && (
        <p className="rounded-lg border border-slate-200 bg-white p-4">{issue.description}</p>
      )}
      <ReportMapLoader position={{ lat: issue.lat, lng: issue.lng }} heightClass="h-56" />
      <p className="text-xs text-slate-500">
        Next: a ward officer reviews the report and updates its status (officer workflow arrives in Phase 2).
      </p>
    </main>
  );
}
