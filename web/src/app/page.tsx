export default function Home() {
  return (
    <main className="mx-auto flex min-h-screen max-w-2xl flex-col justify-center gap-6 px-6 py-16">
      <p className="text-sm font-semibold uppercase tracking-widest text-emerald-700">
        Pilot city: Solapur
      </p>
      <h1 className="text-4xl font-bold sm:text-5xl">NagarFix</h1>
      <p className="text-lg text-slate-600">
        Report potholes, garbage and broken streetlights with a photo and your location.
      </p>
      <p className="rounded-lg border border-amber-300 bg-amber-50 p-4 text-sm text-amber-900">
        Independent student project. Not affiliated with Solapur Municipal Corporation or any
        government body. Reports are not forwarded to any authority.
      </p>
      <p className="text-sm text-slate-500">Status: Phase 0 - setting up.</p>
    </main>
  );
}
