"use client";

import Link from "next/link";
import AdminPanel from "@/components/AdminPanel";
import { useAuth } from "@/components/AuthProvider";

const roleText = {
  CITIZEN: "You can report civic issues and track them until they are fixed.",
  OFFICER: "You handle the issues reported in your ward.",
  ADMIN: "You manage users, officers and wards.",
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
        </p>
        <p className="mt-3 text-sm text-slate-600">{roleText[user.role]}</p>
      </section>
      {user.role === "ADMIN" && <AdminPanel />}
    </main>
  );
}
