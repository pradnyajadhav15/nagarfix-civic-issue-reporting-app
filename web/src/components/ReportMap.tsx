"use client";

import { useEffect } from "react";
import { divIcon, type Marker as LeafletMarker } from "leaflet";
import { MapContainer, Marker, TileLayer, useMap, useMapEvents } from "react-leaflet";
import "leaflet/dist/leaflet.css";
import type { LatLng } from "@/lib/issues";

export type ReportMapProps = {
  position: LatLng;
  onChange?: (p: LatLng) => void;
  focusKey?: number;
  heightClass?: string;
};

const pinIcon = divIcon({
  className: "",
  html: '<div style="width:22px;height:22px;border-radius:9999px;background:#047857;border:3px solid #fff;box-shadow:0 1px 4px rgba(0,0,0,.45)"></div>',
  iconSize: [22, 22],
  iconAnchor: [11, 11],
});

/** Moves the map to the pin when focusKey changes (after "Use my location"), not on every drag. */
function Recenter({ position, focusKey }: { position: LatLng; focusKey: number }) {
  const map = useMap();
  useEffect(() => {
    if (focusKey > 0) map.setView([position.lat, position.lng], Math.max(map.getZoom(), 16));
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [map, focusKey]);
  return null;
}

function ClickToMove({ onChange }: { onChange: (p: LatLng) => void }) {
  useMapEvents({
    click(e) {
      onChange({ lat: e.latlng.lat, lng: e.latlng.lng });
    },
  });
  return null;
}

export default function ReportMap({ position, onChange, focusKey = 0, heightClass = "h-72" }: ReportMapProps) {
  return (
    <MapContainer
      center={[position.lat, position.lng]}
      zoom={14}
      scrollWheelZoom={false}
      className={`${heightClass} w-full rounded-lg border border-slate-200`}
    >
      <TileLayer
        attribution='&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> contributors'
        url="https://tile.openstreetmap.org/{z}/{x}/{y}.png"
      />
      <Marker
        position={[position.lat, position.lng]}
        icon={pinIcon}
        draggable={Boolean(onChange)}
        eventHandlers={
          onChange
            ? {
                dragend: (e) => {
                  const ll = (e.target as LeafletMarker).getLatLng();
                  onChange({ lat: ll.lat, lng: ll.lng });
                },
              }
            : {}
        }
      />
      {onChange && <ClickToMove onChange={onChange} />}
      <Recenter position={position} focusKey={focusKey} />
    </MapContainer>
  );
}
