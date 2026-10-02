import Link from "next/link";
import ServiceStatus from "@/components/ServiceStatus";

export default function Home() {
  return (
    <main className="mx-auto flex min-h-[80vh] max-w-2xl flex-col justify-center gap-6 px-6 py-16">
      <p className="text-sm font-semibold uppercase tracking-widest text-emerald-700">Pilot city: Solapur</p>
      <h1 className="text-4xl font-bold sm:text-5xl">NagarFix</h1>
      <p className="text-lg text-slate-600">
        Report potholes, garbage and broken streetlights with a photo and your location.
      </p>
      <div className="flex flex-wrap gap-3">
        <Link
          href="/report"
          className="rounded-lg bg-emerald-700 px-5 py-2.5 text-sm font-semibold text-white hover:bg-emerald-800"
        >
          Report an issue &rarr;
        </Link>
        <Link
          href="/map"
          className="rounded-lg border border-slate-300 bg-white px-5 py-2.5 text-sm font-semibold hover:bg-slate-50"
        >
          See all reports
        </Link>
        <Link
          href="/wards"
          className="rounded-lg border border-slate-300 bg-white px-5 py-2.5 text-sm font-semibold hover:bg-slate-50"
        >
          Zone map
        </Link>
      </div>
      <p className="rounded-lg border border-amber-300 bg-amber-50 p-4 text-sm text-amber-900">
        Independent student project. Not affiliated with Solapur Municipal Corporation or any
        government body. Reports are not forwarded to any authority.
      </p>
      <ServiceStatus />
      <p className="text-sm text-slate-500">Status: Phase 1 - reporting and public map are live.</p>
    </main>
  );
}
