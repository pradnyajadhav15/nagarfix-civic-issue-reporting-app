"use client";

import Link from "next/link";
import { useCallback, useEffect, useRef, useState } from "react";
import { api } from "@/lib/api";
import { timeAgo } from "@/lib/issues";

type Inbox = {
  unread: number;
  items: { id: number; issueId: number | null; message: string; read: boolean; createdAt: string }[];
};

/** The bell in the header: unread count, latest notifications, "mark all as read". Checks every minute. */
export default function NotificationBell() {
  const [inbox, setInbox] = useState<Inbox | null>(null);
  const [open, setOpen] = useState(false);
  const box = useRef<HTMLDivElement>(null);

  const load = useCallback(() => {
    api<Inbox>("/api/notifications")
      .then(setInbox)
      .catch(() => {
        // the bell is optional; try again next minute
      });
  }, []);

  useEffect(() => {
    load();
    const timer = setInterval(load, 60000);
    return () => clearInterval(timer);
  }, [load]);

  // Close the panel when clicking anywhere else
  useEffect(() => {
    if (!open) return;
    function onClick(e: MouseEvent) {
      if (box.current && !box.current.contains(e.target as Node)) setOpen(false);
    }
    document.addEventListener("mousedown", onClick);
    return () => document.removeEventListener("mousedown", onClick);
  }, [open]);

  async function markAllRead() {
    try {
      await api<void>("/api/notifications/read", { method: "POST" });
    } finally {
      load();
    }
  }

  const unread = inbox?.unread ?? 0;

  return (
    <div className="relative" ref={box}>
      <button
        type="button"
        aria-label={unread > 0 ? `Notifications (${unread} unread)` : "Notifications"}
        onClick={() => setOpen((o) => !o)}
        className="relative rounded p-1.5 text-slate-600 hover:bg-slate-100 hover:text-slate-900"
      >
        <svg viewBox="0 0 24 24" className="h-5 w-5" fill="none" stroke="currentColor" strokeWidth="2" aria-hidden="true">
          <path d="M18 8a6 6 0 1 0-12 0c0 7-3 9-3 9h18s-3-2-3-9" strokeLinecap="round" strokeLinejoin="round" />
          <path d="M13.7 21a2 2 0 0 1-3.4 0" strokeLinecap="round" strokeLinejoin="round" />
        </svg>
        {unread > 0 && (
          <span className="absolute -right-1 -top-1 min-w-[18px] rounded-full bg-red-600 px-1 text-center text-[10px] font-bold leading-[18px] text-white">
            {unread > 9 ? "9+" : unread}
          </span>
        )}
      </button>
      {open && (
        <div className="absolute right-0 z-[1000] mt-2 w-80 max-w-[90vw] rounded-lg border border-slate-200 bg-white text-sm shadow-lg">
          <div className="flex items-center justify-between border-b border-slate-100 px-3 py-2">
            <span className="font-semibold">Notifications</span>
            {unread > 0 && (
              <button type="button" onClick={markAllRead} className="text-xs font-semibold text-emerald-700 hover:underline">
                Mark all as read
              </button>
            )}
          </div>
          <ul className="max-h-96 overflow-y-auto">
            {(inbox?.items ?? []).length === 0 && <li className="px-3 py-4 text-slate-500">Nothing yet.</li>}
            {inbox?.items.map((n) => (
              <li key={n.id} className="border-b border-slate-50 last:border-0">
                <Link
                  href={n.issueId ? `/issues/${n.issueId}` : "#"}
                  onClick={() => setOpen(false)}
                  className={`block px-3 py-2 hover:bg-slate-50 ${n.read ? "text-slate-600" : "font-semibold text-slate-900"}`}
                >
                  {!n.read && <span className="mr-1.5 inline-block h-2 w-2 rounded-full bg-emerald-600" />}
                  {n.message}
                  <span className="mt-0.5 block text-xs font-normal text-slate-500">{timeAgo(n.createdAt)}</span>
                </Link>
              </li>
            ))}
          </ul>
        </div>
      )}
    </div>
  );
}
