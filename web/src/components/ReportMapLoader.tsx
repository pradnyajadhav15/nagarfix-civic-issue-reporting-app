"use client";

import dynamic from "next/dynamic";
import type { ReportMapProps } from "@/components/ReportMap";

// Leaflet needs the browser, so the map loads only on the client.
const ReportMap = dynamic(() => import("@/components/ReportMap"), {
  ssr: false,
  loading: () => <div className="h-56 w-full animate-pulse rounded-lg bg-slate-100" />,
});

export default function ReportMapLoader(props: ReportMapProps) {
  return <ReportMap {...props} />;
}
