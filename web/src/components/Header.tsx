"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { useAuth } from "@/components/AuthProvider";

export default function Header() {
  const { user, loading, logout } = useAuth();
  const router = useRouter();

  return (
    <header className="border-b border-slate-200 bg-white">
      <nav className="mx-auto flex max-w-5xl flex-wrap items-center justify-between gap-3 px-6 py-3 text-sm">
        <div className="flex items-center gap-4">
          <Link href="/" className="text-base font-bold text-emerald-700">
            NagarFix
          </Link>
          <Link href="/map" className="text-slate-600 hover:text-slate-900">
            Map
          </Link>
          <Link href="/wards" className="text-slate-600 hover:text-slate-900">
            Zones
          </Link>
          {user && (
            <Link href="/my-reports" className="text-slate-600 hover:text-slate-900">
              My reports
            </Link>
          )}
        </div>
        <div className="flex items-center gap-3">
          <Link
            href="/report"
            className="rounded bg-emerald-700 px-3 py-1 font-semibold text-white hover:bg-emerald-800"
          >
            Report issue
          </Link>
          {loading ? (
            <span className="text-slate-400">...</span>
          ) : user ? (
            <>
              <Link href="/account" className="text-slate-700 hover:text-slate-900">
                {user.fullName}
                <span className="ml-2 rounded bg-slate-100 px-1.5 py-0.5 text-xs text-slate-600">{user.role}</span>
              </Link>
              <button
                type="button"
                onClick={() => {
                  logout();
                  router.push("/");
                }}
                className="rounded border border-slate-300 px-3 py-1 hover:bg-slate-50"
              >
                Log out
              </button>
            </>
          ) : (
            <>
              <Link href="/login" className="text-slate-700 hover:text-slate-900">
                Log in
              </Link>
              <Link href="/register" className="rounded border border-slate-300 px-3 py-1 hover:bg-slate-50">
                Sign up
              </Link>
            </>
          )}
        </div>
      </nav>
    </header>
  );
}
