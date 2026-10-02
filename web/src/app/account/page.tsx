"use client";

import Link from "next/link";
import AdminPanel from "@/components/AdminPanel";
import { useAuth } from "@/components/AuthProvider";
import { DemoBadge } from "@/components/Badges";

const roleText = {
  CITIZEN: "You can report civic issues, follow them, and confirm when they are fixed.",
  OFFICER: "You handle the reports in your zone: take them, fix them and upload an after-photo.",
  ADMIN: "You manage officers, departments and deadlines, and can act on reports in any zone.",
} as const;

export default function AccountPage() {
  const { user, loading } = useAuth();

  if (loading) {
    return <main className="mx-auto max-w-3xl px-6 py-10 text-slate-600">Loading...</main>;
  }

  if (!user) {
    return (
      <main className="mx-auto max-w-3xl space-y-3 px-6 py-10">
        <p>You are not logged in.</p>
        <Link href="/login" className="text-emerald-700 hover:underline">
          Log in
        </Link>
      </main>
    );
  }

  return (
    <main className="mx-auto max-w-4xl space-y-6 px-6 py-10">
      <section className="rounded-lg border border-slate-200 bg-white p-5">
        <h1 className="text-2xl font-bold">{user.fullName}</h1>
        <p className="text-sm text-slate-600">{user.email}</p>
        <p className="mt-2 text-sm">
          <span className="rounded bg-emerald-50 px-2 py-0.5 font-semibold text-emerald-800">
            {user.role}
          </span>
          {user.wardCode && <span className="ml-2 text-slate-600">Ward: {user.wardCode}</span>}
          {user.demo && (
            <span className="ml-2">
              <DemoBadge />
            </span>
          )}
        </p>
        <p className="mt-3 text-sm text-slate-600">{roleText[user.role]}</p>
        <div className="mt-4 flex flex-wrap gap-2 text-sm">
          {user.role !== "CITIZEN" && (
            <Link href="/officer" className="rounded bg-emerald-700 px-3 py-1.5 font-semibold text-white hover:bg-emerald-800">
              Open My zone
            </Link>
          )}
          <Link href="/my-reports" className="rounded border border-slate-300 px-3 py-1.5 font-semibold hover:bg-slate-50">
            My reports
          </Link>
        </div>
      </section>
      {user.role === "ADMIN" && <AdminPanel />}
    </main>
  );
}
