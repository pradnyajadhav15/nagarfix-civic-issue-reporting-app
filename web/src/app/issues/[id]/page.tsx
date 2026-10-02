"use client";

import Link from "next/link";
import { useParams, useSearchParams } from "next/navigation";
import { useEffect, useState } from "react";
import { DeadlineBadge, DemoBadge } from "@/components/Badges";
import IssueActions from "@/components/IssueActions";
import IssueTimeline from "@/components/IssueTimeline";
import ReportMapLoader from "@/components/ReportMapLoader";
import StatusBadge from "@/components/StatusBadge";
import { api } from "@/lib/api";
import { categoryLabel, cloudinaryUrl, formatDate, type Issue } from "@/lib/issues";

export default function IssuePage() {
  const params = useParams<{ id: string }>();
  const id = params?.id;
  const [issue, setIssue] = useState<Issue | null>(null);
  const [error, setError] = useState("");
  const [refreshKey, setRefreshKey] = useState(0);
  const isNew = useSearchParams().has("new");

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

  function onChanged(updated: Issue) {
    setIssue(updated);
    setRefreshKey((k) => k + 1);
  }

  if (error) {
    return (
      <main className="mx-auto max-w-3xl space-y-3 px-6 py-10">
        <p className="text-red-600">{error}</p>
        <Link href="/map" className="text-emerald-700 underline">
          Back to all reports
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
  const fixed = issue.resolutionPhotoUrl && (issue.status === "RESOLVED" || issue.status === "CLOSED");

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
      {issue.demo && (
        <p className="rounded-lg border border-violet-200 bg-violet-50 p-3 text-sm text-violet-900">
          This is a <strong>demo report</strong> with made-up data, so you can try NagarFix. Demo data resets every
          day.
        </p>
      )}

      <div className="space-y-2">
        <div className="flex flex-wrap items-center justify-between gap-3">
          <h1 className="text-2xl font-bold">
            {cat.en} <span className="text-lg font-normal text-slate-500">{cat.mr}</span>
          </h1>
          <div className="flex flex-wrap items-center gap-2">
            {issue.demo && <DemoBadge />}
            <DeadlineBadge issue={issue} />
            <StatusBadge status={issue.status} />
          </div>
        </div>
        <p className="text-sm text-slate-600">
          Report #{issue.id} - {formatDate(issue.createdAt)} - {issue.wardName ?? "Unknown zone"}
          {issue.wardNameMr ? ` / ${issue.wardNameMr}` : ""}
        </p>
        {issue.department && (
          <p className="text-sm text-slate-600">
            Handled by <strong>{issue.department}</strong>
            {issue.slaDays ? ` - target: fixed within ${issue.slaDays} ${issue.slaDays === 1 ? "day" : "days"}` : ""}
          </p>
        )}
      </div>

      {issue.status === "REJECTED" && issue.statusReason && (
        <p className="rounded-lg border border-red-200 bg-red-50 p-3 text-sm text-red-900">
          <strong>Rejected:</strong> {issue.statusReason}
        </p>
      )}
      {issue.status === "DUPLICATE" && issue.duplicateOfId && (
        <p className="rounded-lg border border-slate-200 bg-slate-50 p-3 text-sm">
          This is the same problem as{" "}
          <Link href={`/issues/${issue.duplicateOfId}`} className="font-semibold text-emerald-700 underline">
            report #{issue.duplicateOfId}
          </Link>
          . Follow that report for updates.
        </p>
      )}
      {issue.status === "REOPENED" && issue.statusReason && (
        <p className="rounded-lg border border-orange-200 bg-orange-50 p-3 text-sm text-orange-900">
          <strong>Reopened by the citizen:</strong> {issue.statusReason}
        </p>
      )}

      {fixed ? (
        <div className="grid gap-3 sm:grid-cols-2">
          <figure className="space-y-1">
            <img
              src={cloudinaryUrl(issue.photoUrl, "c_limit,w_800,q_auto,f_auto")}
              alt={`Before: the reported ${cat.en.toLowerCase()}`}
              className="w-full rounded-lg border border-slate-200"
            />
            <figcaption className="text-xs font-semibold text-slate-500">BEFORE</figcaption>
          </figure>
          <figure className="space-y-1">
            <img
              src={cloudinaryUrl(issue.resolutionPhotoUrl ?? "", "c_limit,w_800,q_auto,f_auto")}
              alt="After: the fix"
              className="w-full rounded-lg border border-slate-200"
            />
            <figcaption className="text-xs font-semibold text-emerald-700">AFTER</figcaption>
          </figure>
        </div>
      ) : (
        <img
          src={cloudinaryUrl(issue.photoUrl, "c_limit,w_1000,q_auto,f_auto")}
          alt={`Photo of the reported ${cat.en.toLowerCase()}`}
          className="w-full rounded-lg border border-slate-200"
        />
      )}
      {issue.resolutionNote && fixed && (
        <p className="text-sm text-slate-700">
          <strong>Officer&apos;s note:</strong> {issue.resolutionNote}
        </p>
      )}
      {issue.description && (
        <p className="rounded-lg border border-slate-200 bg-white p-4">{issue.description}</p>
      )}

      <IssueActions issue={issue} onChanged={onChanged} />
      <ReportMapLoader position={{ lat: issue.lat, lng: issue.lng }} heightClass="h-56" />
      <IssueTimeline issueId={issue.id} refreshKey={refreshKey} />
    </main>
  );
}
