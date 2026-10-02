"use client";

import Link from "next/link";
import ReportForm from "@/components/ReportForm";
import { useAuth } from "@/components/AuthProvider";

export default function ReportPage() {
  const { user, loading } = useAuth();

  return (
    <main className="mx-auto max-w-2xl space-y-4 px-6 py-8">
      <h1 className="text-2xl font-bold">Report a civic issue</h1>
      {loading ? (
        <p className="text-slate-600">Loading...</p>
      ) : !user ? (
        <div className="rounded-lg border border-slate-200 bg-white p-5 text-sm">
          <p>Please log in to report an issue, so you can track it until it is fixed.</p>
          <div className="mt-4 flex gap-3">
            <Link href="/login?next=/report" className="rounded bg-emerald-700 px-4 py-2 font-semibold text-white hover:bg-emerald-800">
              Log in
            </Link>
            <Link href="/register?next=/report" className="rounded border border-slate-300 px-4 py-2 font-semibold hover:bg-slate-50">
              Create an account
            </Link>
          </div>
        </div>
      ) : (
        <ReportForm />
      )}
    </main>
  );
}
