import { STATUS } from "@/lib/issues";

export default function StatusBadge({ status }: { status: string }) {
  const s = STATUS[status] ?? { label: status, cls: "bg-slate-100 text-slate-700" };
  return <span className={`inline-block rounded px-2 py-0.5 text-xs font-semibold ${s.cls}`}>{s.label}</span>;
}
