"use client";

import Link from "next/link";
import { CircleMarker, GeoJSON, MapContainer, Popup, TileLayer } from "react-leaflet";
import type { FeatureCollection } from "geojson";
import "leaflet/dist/leaflet.css";
import { DemoBadge } from "@/components/Badges";
import { categoryLabel, cloudinaryUrl, formatDate, STATUS, STATUS_COLOR, type Issue } from "@/lib/issues";

export type IssuesMapProps = {
  issues: Issue[];
  zones: FeatureCollection | null;
};

export default function IssuesMap({ issues, zones }: IssuesMapProps) {
  return (
    <MapContainer
      center={[17.6599, 75.9064]}
      zoom={12}
      className="h-[65vh] w-full rounded-lg border border-slate-200"
    >
      <TileLayer
        attribution='&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> contributors'
        url="https://tile.openstreetmap.org/{z}/{x}/{y}.png"
      />
      {zones && (
        <GeoJSON
          data={zones}
          style={() => ({ color: "#047857", weight: 1, fillColor: "#10b981", fillOpacity: 0.03 })}
        />
      )}
      {issues.map((i) => {
        const cat = categoryLabel(i.category);
        return (
          <CircleMarker
            key={i.id}
            center={[i.lat, i.lng]}
            radius={8}
            pathOptions={{ color: "#ffffff", weight: 2, fillColor: STATUS_COLOR[i.status] ?? "#64748b", fillOpacity: 0.95 }}
          >
            <Popup>
              <div className="w-48 space-y-1">
                <img
                  src={cloudinaryUrl(i.photoUrl, "c_fill,w_200,h_130,q_auto,f_auto")}
                  alt=""
                  className="h-28 w-full rounded object-cover"
                />
                <div className="font-semibold">
                  {cat.en} <span className="font-normal text-slate-500">{cat.mr}</span> {i.demo && <DemoBadge />}
                </div>
                <div className="text-xs">
                  {STATUS[i.status]?.label ?? i.status}
                  {i.wardName ? ` - ${i.wardName}` : ""}
                </div>
                <div className="text-xs text-slate-500">{formatDate(i.createdAt)}</div>
                <Link href={`/issues/${i.id}`} className="text-xs font-semibold text-emerald-700 underline">
                  View report
                </Link>
              </div>
            </Popup>
          </CircleMarker>
        );
      })}
    </MapContainer>
  );
}
