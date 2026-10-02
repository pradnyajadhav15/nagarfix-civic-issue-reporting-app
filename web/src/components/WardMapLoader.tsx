"use client";

import dynamic from "next/dynamic";

// Leaflet needs the browser, so the map is loaded only on the client.
const WardMap = dynamic(() => import("./WardMap"), {
  ssr: false,
  loading: () => <p className="text-sm text-slate-600">Loading map...</p>,
});

export default function WardMapLoader() {
  return <WardMap />;
}
