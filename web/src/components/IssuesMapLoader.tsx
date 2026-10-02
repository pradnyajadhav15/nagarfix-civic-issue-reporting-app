"use client";

import dynamic from "next/dynamic";
import type { IssuesMapProps } from "@/components/IssuesMap";

// Leaflet needs the browser, so the map loads only on the client.
const IssuesMap = dynamic(() => import("@/components/IssuesMap"), {
  ssr: false,
  loading: () => <div className="h-[65vh] w-full animate-pulse rounded-lg bg-slate-100" />,
});

export default function IssuesMapLoader(props: IssuesMapProps) {
  return <IssuesMap {...props} />;
}
