import { deadlineText, type Issue } from "@/lib/issues";

/** Marks demo data, so it is never mistaken for a real report. */
export function DemoBadge() {
  return (
    <span className="inline-block rounded bg-violet-100 px-1.5 py-0.5 text-[10px] font-bold tracking-wider text-violet-800">
      DEMO
    </span>
  );
}

/** "Due in 2 days" (grey) or "Overdue by 3 days" (red) while a report is still open. */
export function DeadlineBadge({ issue }: { issue: Issue }) {
  const deadline = deadlineText(issue);
  if (!deadline) return null;
  return (
    <span
      className={`inline-block rounded px-2 py-0.5 text-xs font-semibold ${
        deadline.late ? "bg-red-100 text-red-800" : "bg-slate-100 text-slate-600"
      }`}
    >
      {deadline.text}
    </span>
  );
}
