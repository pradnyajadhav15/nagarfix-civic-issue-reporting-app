"use client";

import { useEffect, useState } from "react";
import { GeoJSON, MapContainer, Popup, TileLayer, useMapEvents } from "react-leaflet";
import type { FeatureCollection } from "geojson";
import type { LatLng } from "leaflet";
import "leaflet/dist/leaflet.css";
import { API_URL } from "@/lib/config";

const SOLAPUR_CENTER: [number, number] = [17.6599, 75.9064];

type WardHit = { code: string; name: string; nameMr: string | null };

/** Click anywhere: ask the API which zone that point belongs to. */
function ClickToLocate() {
  const [pos, setPos] = useState<LatLng | null>(null);
  const [text, setText] = useState("");

  useMapEvents({
    click(e) {
      setPos(e.latlng);
      setText("Finding zone...");
      fetch(`${API_URL}/api/wards/locate?lat=${e.latlng.lat}&lng=${e.latlng.lng}`)
        .then(async (res) => {
          if (res.status === 404) return setText("Outside Solapur");
          if (!res.ok) return setText("Could not check this point");
          const w: WardHit = await res.json();
          setText(w.nameMr ? `${w.name} / ${w.nameMr}` : w.name);
        })
        .catch(() => setText("API not reachable"));
    },
  });

  return pos ? (
    <Popup key={`${pos.lat},${pos.lng}`} position={pos}>
      {text}
    </Popup>
  ) : null;
}

export default function WardMap() {
  const [wards, setWards] = useState<FeatureCollection | null>(null);
  const [error, setError] = useState(false);

  useEffect(() => {
    let active = true;
    fetch(`${API_URL}/api/wards`, { cache: "no-store" })
      .then((res) => (res.ok ? res.json() : Promise.reject(res.status)))
      .then((data: FeatureCollection) => {
        if (active) setWards(data);
      })
      .catch(() => {
        if (active) setError(true);
      });
    return () => {
      active = false;
    };
  }, []);

  return (
    <div className="space-y-2">
      <p className="text-sm text-slate-600">
        {error
          ? "Could not load zones - the API may be waking up. Refresh in a minute."
          : wards
            ? `${wards.features.length} zones loaded. Click anywhere to see which zone it is in.`
            : "Loading zones (the free server can take up to a minute to wake)..."}
      </p>
      <MapContainer
        center={SOLAPUR_CENTER}
        zoom={12}
        className="h-[70vh] w-full rounded-lg border border-slate-200"
      >
        <TileLayer
          attribution='&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> contributors'
          url="https://tile.openstreetmap.org/{z}/{x}/{y}.png"
        />
        {wards && (
          <GeoJSON
            data={wards}
            style={() => ({ color: "#047857", weight: 1.5, fillColor: "#10b981", fillOpacity: 0.12 })}
            onEachFeature={(feature, layer) =>
              layer.bindTooltip(String(feature.properties?.name ?? ""), { sticky: true })
            }
          />
        )}
        <ClickToLocate />
      </MapContainer>
    </div>
  );
}
