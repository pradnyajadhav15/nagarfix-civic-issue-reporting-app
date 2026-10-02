"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { useState } from "react";
import { useAuth, type Role } from "@/components/AuthProvider";

const inputCls =
  "w-full rounded border border-slate-300 px-3 py-2 text-sm focus:border-emerald-600 focus:outline-none";

const DEMOS: { role: Role; label: string; hint: string; goTo: string }[] = [
  { role: "CITIZEN", label: "Citizen", hint: "Report issues and confirm fixes", goTo: "/my-reports" },
  { role: "OFFICER", label: "Ward officer", hint: "Handle zone Z13's reports", goTo: "/officer" },
  { role: "ADMIN", label: "Admin", hint: "Look around (read-only)", goTo: "/account" },
];

function nextPath(): string {
  const next = new URLSearchParams(window.location.search).get("next");
  return next && next.startsWith("/") && !next.startsWith("//") ? next : "/account";
}

export default function LoginPage() {
  const { login, loginDemo } = useAuth();
  const router = useRouter();
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);

  async function onSubmit(e: React.FormEvent<HTMLFormElement>) {
    e.preventDefault();
    setError("");
    setBusy(true);
    try {
      await login(email, password);
      router.push(nextPath());
    } catch (err) {
      setError(err instanceof Error ? err.message : "Login failed");
    } finally {
      setBusy(false);
    }
  }

  async function tryDemo(role: Role, goTo: string) {
    setError("");
    setBusy(true);
    try {
      await loginDemo(role);
      router.push(goTo);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Could not open the demo account");
    } finally {
      setBusy(false);
    }
  }

  return (
    <main className="mx-auto max-w-md space-y-6 px-6 py-16">
      <h1 className="text-2xl font-bold">Log in</h1>
      <form onSubmit={onSubmit} className="space-y-4 rounded-lg border border-slate-200 bg-white p-5">
        <label className="block space-y-1 text-sm">
          <span>Email</span>
          <input type="email" required autoComplete="email" value={email}
            onChange={(e) => setEmail(e.target.value)} className={inputCls} />
        </label>
        <label className="block space-y-1 text-sm">
          <span>Password</span>
          <input type="password" required autoComplete="current-password" value={password}
            onChange={(e) => setPassword(e.target.value)} className={inputCls} />
        </label>
        {error && <p className="text-sm text-red-600">{error}</p>}
        <button type="submit" disabled={busy}
          className="w-full rounded bg-emerald-700 py-2 text-sm font-semibold text-white hover:bg-emerald-800 disabled:opacity-60">
          {busy ? "Logging in... (first time can take a minute)" : "Log in"}
        </button>
        <p className="text-center text-sm text-slate-600">
          New here?{" "}
          <Link href="/register" className="text-emerald-700 hover:underline">
            Create an account
          </Link>
        </p>
      </form>

      <section id="demo" className="space-y-3 rounded-lg border border-violet-200 bg-violet-50 p-5">
        <h2 className="font-semibold text-violet-900">Or try a demo account</h2>
        <p className="text-sm text-violet-900">
          No sign-up needed. Demo accounts only see and change demo reports, and the demo data resets every day.
        </p>
        <div className="grid gap-2 sm:grid-cols-3">
          {DEMOS.map((d) => (
            <button
              key={d.role}
              type="button"
              disabled={busy}
              onClick={() => tryDemo(d.role, d.goTo)}
              className="rounded border border-violet-300 bg-white px-3 py-2 text-left hover:bg-violet-100 disabled:opacity-60"
            >
              <span className="block text-sm font-semibold text-violet-900">{d.label}</span>
              <span className="block text-xs text-violet-700">{d.hint}</span>
            </button>
          ))}
        </div>
      </section>
    </main>
  );
}
