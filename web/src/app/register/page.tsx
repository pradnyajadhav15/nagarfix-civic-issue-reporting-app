"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { useState } from "react";
import { useAuth } from "@/components/AuthProvider";

const inputCls =
  "w-full rounded border border-slate-300 px-3 py-2 text-sm focus:border-emerald-600 focus:outline-none";

export default function RegisterPage() {
  const { register } = useAuth();
  const router = useRouter();
  const [fullName, setFullName] = useState("");
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);

  async function onSubmit(e: React.FormEvent<HTMLFormElement>) {
    e.preventDefault();
    setError("");
    setBusy(true);
    try {
      await register(fullName, email, password);
      router.push("/account");
    } catch (err) {
      setError(err instanceof Error ? err.message : "Sign up failed");
    } finally {
      setBusy(false);
    }
  }

  return (
    <main className="mx-auto max-w-sm px-6 py-16">
      <h1 className="mb-2 text-2xl font-bold">Create your account</h1>
      <p className="mb-6 text-sm text-slate-600">Citizens can report and track civic issues.</p>
      <form onSubmit={onSubmit} className="space-y-4 rounded-lg border border-slate-200 bg-white p-5">
        <label className="block space-y-1 text-sm">
          <span>Full name</span>
          <input required maxLength={120} autoComplete="name" value={fullName}
            onChange={(e) => setFullName(e.target.value)} className={inputCls} />
        </label>
        <label className="block space-y-1 text-sm">
          <span>Email</span>
          <input type="email" required autoComplete="email" value={email}
            onChange={(e) => setEmail(e.target.value)} className={inputCls} />
        </label>
        <label className="block space-y-1 text-sm">
          <span>Password (at least 8 characters)</span>
          <input type="password" required minLength={8} maxLength={72} autoComplete="new-password"
            value={password} onChange={(e) => setPassword(e.target.value)} className={inputCls} />
        </label>
        {error && <p className="text-sm text-red-600">{error}</p>}
        <button type="submit" disabled={busy}
          className="w-full rounded bg-emerald-700 py-2 text-sm font-semibold text-white hover:bg-emerald-800 disabled:opacity-60">
          {busy ? "Creating account... (first time can take a minute)" : "Sign up"}
        </button>
        <p className="text-center text-sm text-slate-600">
          Already have an account?{" "}
          <Link href="/login" className="text-emerald-700 hover:underline">
            Log in
          </Link>
        </p>
      </form>
    </main>
  );
}
