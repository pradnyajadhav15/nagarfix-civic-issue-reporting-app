"use client";

import { useEffect, useState } from "react";
import { api } from "@/lib/api";
import { cloudinaryUrl, EVENT_TEXT, formatDate, ROLE_TEXT, STATUS_COLOR, type IssueEvent } from "@/lib/issues";

/** Everything that happened to a report, oldest first. Shows roles (citizen, officer), never names. */
export default function IssueTimeline({ issueId, refreshKey }: { issueId: number; refreshKey: number }) {
  const [events, setEvents] = useState<IssueEvent[] | null>(null);
  const [error, setError] = useState("");

  useEffect(() => {
    let active = true;
    api<IssueEvent[]>(`/api/issues/${issueId}/history`)
      .then((list) => {
        if (active) setEvents(list);
      })
      .catch((e) => {
        if (active) setError(e instanceof Error ? e.message : "Could not load the history");
      });
    return () => {
      active = false;
    };
  }, [issueId, refreshKey]);

  return (
    <section className="rounded-lg border border-slate-200 bg-white p-4">
      <h2 className="mb-3 font-semibold">History</h2>
      {error && <p className="text-sm text-red-600">{error}</p>}
      {!events && !error && <p className="text-sm text-slate-500">Loading...</p>}
      <ol className="space-y-4 border-l-2 border-slate-100 pl-4">
        {events?.map((e, index) => (
          <li key={`${e.createdAt}-${index}`} className="relative space-y-1">
            <span
              className="absolute -left-[23px] top-1.5 h-3 w-3 rounded-full border-2 border-white"
              style={{ backgroundColor: STATUS_COLOR[e.toStatus] ?? "#64748b" }}
            />
            <p className="text-sm">
              <span className="font-semibold">{EVENT_TEXT[e.action] ?? e.action}</span>{" "}
              <span className="text-slate-500">by {ROLE_TEXT[e.actorRole] ?? e.actorRole}</span>
            </p>
            <p className="text-xs text-slate-500">{formatDate(e.createdAt)}</p>
            {e.note && <p className="text-sm text-slate-700">&ldquo;{e.note}&rdquo;</p>}
            {e.photoUrl && (
              <img
                src={cloudinaryUrl(e.photoUrl, "c_fill,w_240,h_160,q_auto,f_auto")}
                alt="Photo added at this step"
                className="h-24 w-36 rounded border border-slate-200 object-cover"
              />
            )}
          </li>
        ))}
      </ol>
    </section>
  );
}
