import type { Metadata } from "next";
import Link from "next/link";
import WardMapLoader from "@/components/WardMapLoader";

export const metadata: Metadata = {
  title: "Solapur zones - NagarFix",
};

export default function WardsPage() {
  return (
    <main className="mx-auto max-w-5xl space-y-4 px-6 py-10">
      <Link href="/" className="text-sm text-emerald-700 hover:underline">
        &larr; Home
      </Link>
      <h1 className="text-3xl font-bold">Solapur zones</h1>
      <p className="rounded-lg border border-amber-300 bg-amber-50 p-3 text-sm text-amber-900">
        These 26 zones are approximate, generated from the city boundary for this student project.
        They are not the official Solapur Municipal Corporation wards.
      </p>
      <WardMapLoader />
    </main>
  );
}
