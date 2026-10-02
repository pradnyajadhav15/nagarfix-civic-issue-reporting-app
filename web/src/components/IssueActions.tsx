"use client";

import Link from "next/link";
import { useEffect, useState } from "react";
import { useAuth } from "@/components/AuthProvider";
import { api } from "@/lib/api";
import { compressImage, uploadPhoto, type Issue, type IssueAction } from "@/lib/issues";

type Form = null | "RESOLVE" | "REJECT" | "DUPLICATE" | "REOPEN";

const inputCls =
  "w-full rounded border border-slate-300 px-3 py-2 text-sm focus:border-emerald-600 focus:outline-none";
const primary =
  "rounded bg-emerald-700 px-3 py-1.5 text-sm font-semibold text-white hover:bg-emerald-800 disabled:opacity-60";
const secondary =
  "rounded border border-slate-300 bg-white px-3 py-1.5 text-sm font-semibold hover:bg-slate-50 disabled:opacity-60";
const danger =
  "rounded border border-red-300 bg-white px-3 py-1.5 text-sm font-semibold text-red-700 hover:bg-red-50 disabled:opacity-60";

/**
 * The buttons for a report. The API decides which ones this person may use
 * (officers of the zone and admins move the report along; the reporter confirms the fix or reopens it).
 */
export default function IssueActions({ issue, onChanged }: { issue: Issue; onChanged: (issue: Issue) => void }) {
  const { user } = useAuth();
  const [allowed, setAllowed] = useState<IssueAction[]>([]);
  const [form, setForm] = useState<Form>(null);
  const [note, setNote] = useState("");
  const [original, setOriginal] = useState("");
  const [photo, setPhoto] = useState<Blob | null>(null);
  const [preview, setPreview] = useState<string | null>(null);
  const [busy, setBusy] = useState("");
  const [error, setError] = useState("");

  // Ask the API which actions are allowed whenever the person or the report's status changes
  useEffect(() => {
    if (!user) return;
    let active = true;
    api<IssueAction[]>(`/api/issues/${issue.id}/actions`)
      .then((list) => {
        if (active) setAllowed(list);
      })
      .catch(() => {
        if (active) setAllowed([]);
      });
    return () => {
      active = false;
    };
  }, [user, issue.id, issue.status]);

  // Free the old preview image from memory when it changes
  useEffect(() => {
    return () => {
      if (preview) URL.revokeObjectURL(preview);
    };
  }, [preview]);

  function open(next: Form) {
    setForm(form === next ? null : next);
    setError("");
  }

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

  async function run(action: IssueAction, extra: Record<string, unknown> = {}) {
    setError("");
    setBusy("Saving...");
    try {
      const updated = await api<Issue>(`/api/issues/${issue.id}/actions`, {
        method: "POST",
        body: JSON.stringify({ action, ...extra }),
      });
      setForm(null);
      setNote("");
      setOriginal("");
      setPhoto(null);
      setPreview(null);
      onChanged(updated);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Could not save. Please try again.");
    } finally {
      setBusy("");
    }
  }

  async function resolve() {
    if (!photo) {
      setError("Add an 'after' photo that shows the fix.");
      return;
    }
    setError("");
    setBusy("Uploading photo...");
    let photoUrl: string;
    try {
      photoUrl = await uploadPhoto(photo);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Photo upload failed - please try again");
      setBusy("");
      return;
    }
    await run("RESOLVE", { photoUrl, note });
  }

  if (!user) {
    return issue.status === "RESOLVED" ? (
      <p className="rounded-lg border border-emerald-200 bg-emerald-50 p-3 text-sm text-emerald-900">
        Did you report this?{" "}
        <Link href={`/login?next=/issues/${issue.id}`} className="font-semibold underline">
          Log in
        </Link>{" "}
        to confirm the fix.
      </p>
    ) : null;
  }

  const forReporter = allowed.filter((a) => a === "CONFIRM" || a === "REOPEN");
  const forOfficer = allowed.filter((a) => a !== "CONFIRM" && a !== "REOPEN");
  const working = busy !== "";

  return (
    <>
      {forReporter.length > 0 && (
        <section className="space-y-3 rounded-lg border-2 border-emerald-600 bg-emerald-50 p-4">
          <h2 className="font-semibold text-emerald-900">Is it fixed?</h2>
          <p className="text-sm text-emerald-900">
            The officer marked your report as fixed. Please check and tell us. If you don&apos;t answer, it
            closes automatically after 7 days.
          </p>
          <div className="flex flex-wrap gap-2">
            {forReporter.includes("CONFIRM") && (
              <button type="button" disabled={working} onClick={() => run("CONFIRM")} className={primary}>
                Yes, it&apos;s fixed
              </button>
            )}
            {forReporter.includes("REOPEN") && (
              <button type="button" disabled={working} onClick={() => open("REOPEN")} className={danger}>
                No, it&apos;s not fixed
              </button>
            )}
          </div>
          {form === "REOPEN" && (
            <div className="space-y-2">
              <textarea
                rows={2}
                maxLength={1000}
                placeholder="What is still wrong? (required)"
                value={note}
                onChange={(e) => setNote(e.target.value)}
                className={inputCls}
              />
              <button type="button" disabled={working} onClick={() => run("REOPEN", { note })} className={danger}>
                {busy || "Reopen the report"}
              </button>
            </div>
          )}
          {error && forOfficer.length === 0 && <p className="text-sm font-semibold text-red-600">{error}</p>}
        </section>
      )}

      {forOfficer.length > 0 && (
        <section className="space-y-3 rounded-lg border border-slate-200 bg-white p-4">
          <h2 className="font-semibold">{user.role === "ADMIN" ? "Admin actions" : "Officer actions"}</h2>
          <div className="flex flex-wrap gap-2">
            {forOfficer.includes("ASSIGN") && (
              <button type="button" disabled={working} onClick={() => run("ASSIGN")} className={primary}>
                Take this report
              </button>
            )}
            {forOfficer.includes("START") && (
              <button type="button" disabled={working} onClick={() => run("START")} className={primary}>
                Start work
              </button>
            )}
            {forOfficer.includes("RESOLVE") && (
              <button type="button" disabled={working} onClick={() => open("RESOLVE")} className={primary}>
                Mark as fixed
              </button>
            )}
            {forOfficer.includes("DUPLICATE") && (
              <button type="button" disabled={working} onClick={() => open("DUPLICATE")} className={secondary}>
                Duplicate
              </button>
            )}
            {forOfficer.includes("REJECT") && (
              <button type="button" disabled={working} onClick={() => open("REJECT")} className={danger}>
                Reject
              </button>
            )}
          </div>

          {form === "RESOLVE" && (
            <div className="space-y-2 rounded border border-slate-200 p-3">
              <p className="text-sm font-semibold">Add an &ldquo;after&rdquo; photo that shows the fix</p>
              <input
                type="file"
                accept="image/*"
                capture="environment"
                onChange={onPhoto}
                className="block w-full text-sm file:mr-3 file:rounded file:border-0 file:bg-emerald-700 file:px-3 file:py-2 file:font-semibold file:text-white"
              />
              {preview && <img src={preview} alt="After photo" className="max-h-48 rounded border border-slate-200" />}
              <textarea
                rows={2}
                maxLength={1000}
                placeholder="What was done? (optional)"
                value={note}
                onChange={(e) => setNote(e.target.value)}
                className={inputCls}
              />
              <button type="button" disabled={working} onClick={resolve} className={primary}>
                {busy || "Save as fixed"}
              </button>
            </div>
          )}

          {form === "REJECT" && (
            <div className="space-y-2 rounded border border-slate-200 p-3">
              <textarea
                rows={2}
                maxLength={1000}
                placeholder="Why is it rejected? The citizen will see this. (required)"
                value={note}
                onChange={(e) => setNote(e.target.value)}
                className={inputCls}
              />
              <button type="button" disabled={working} onClick={() => run("REJECT", { note })} className={danger}>
                {busy || "Reject the report"}
              </button>
            </div>
          )}

          {form === "DUPLICATE" && (
            <div className="space-y-2 rounded border border-slate-200 p-3">
              <input
                type="number"
                min={1}
                placeholder="Number of the original report, e.g. 42"
                value={original}
                onChange={(e) => setOriginal(e.target.value)}
                className={inputCls}
              />
              <input
                maxLength={300}
                placeholder="Note (optional)"
                value={note}
                onChange={(e) => setNote(e.target.value)}
                className={inputCls}
              />
              <button
                type="button"
                disabled={working || !original}
                onClick={() => run("DUPLICATE", { duplicateOf: Number(original), note })}
                className={secondary}
              >
                {busy || "Mark as duplicate"}
              </button>
            </div>
          )}
          {error && <p className="text-sm font-semibold text-red-600">{error}</p>}
        </section>
      )}
    </>
  );
}
