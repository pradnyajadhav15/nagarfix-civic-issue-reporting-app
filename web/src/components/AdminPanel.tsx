"use client";

import { useCallback, useEffect, useState } from "react";
import { useAuth, type User } from "@/components/AuthProvider";
import { DemoBadge } from "@/components/Badges";
import { api } from "@/lib/api";
import { categoryLabel, CATEGORIES } from "@/lib/issues";

type WardOption = { code: string; name: string };
type WardCollection = { features: { properties: WardOption }[] };
type Rule = { category: string; department: string; slaDays: number };
type Coverage = { code: string; name: string; officers: number; open: number; overdue: number };
type JobResult = { ran: boolean; autoClosed: number; demoReports: number | null };

const emptyForm = { fullName: "", email: "", password: "", wardCode: "" };
const inputCls =
  "w-full rounded border border-slate-300 px-3 py-2 text-sm focus:border-emerald-600 focus:outline-none disabled:bg-slate-50";
const card = "rounded-lg border border-slate-200 bg-white p-5";
const errorText = (e: unknown, fallback: string) => (e instanceof Error ? e.message : fallback);

/** Departments and deadlines: which department handles each category, and how many days it has. */
function RulesEditor({ readOnly }: { readOnly: boolean }) {
  const [rules, setRules] = useState<Rule[]>([]);
  const [message, setMessage] = useState("");

  useEffect(() => {
    let active = true;
    api<Rule[]>("/api/rules")
      .then((list) => {
        const order = CATEGORIES.map((c) => c.value as string);
        if (active) setRules([...list].sort((a, b) => order.indexOf(a.category) - order.indexOf(b.category)));
      })
      .catch((e) => {
        if (active) setMessage(errorText(e, "Could not load the departments"));
      });
    return () => {
      active = false;
    };
  }, []);

  function edit(category: string, change: Partial<Rule>) {
    setRules((list) => list.map((r) => (r.category === category ? { ...r, ...change } : r)));
  }

  async function save(rule: Rule) {
    setMessage("");
    try {
      await api<Rule>(`/api/admin/rules/${rule.category}`, {
        method: "PUT",
        body: JSON.stringify({ department: rule.department, slaDays: rule.slaDays }),
      });
      setMessage(`Saved: ${categoryLabel(rule.category).en} -> ${rule.department}, ${rule.slaDays} days.`);
    } catch (e) {
      setMessage(errorText(e, "Could not save"));
    }
  }

  return (
    <div className={card}>
      <h2 className="text-lg font-semibold">Departments and deadlines</h2>
      <p className="mb-4 text-sm text-slate-600">
        Each category goes to one department, which has this many days to fix it. Reports still open after that
        are marked overdue.
      </p>
      <div className="space-y-2">
        {rules.map((r) => (
          <div key={r.category} className="grid items-center gap-2 sm:grid-cols-[9rem_1fr_6rem_auto]">
            <span className="text-sm font-semibold">{categoryLabel(r.category).en}</span>
            <input value={r.department} disabled={readOnly} maxLength={80}
              onChange={(e) => edit(r.category, { department: e.target.value })} className={inputCls} />
            <label className="flex items-center gap-1 text-sm">
              <input type="number" min={1} max={90} value={r.slaDays} disabled={readOnly}
                onChange={(e) => edit(r.category, { slaDays: Number(e.target.value) })} className={inputCls} />
              days
            </label>
            <button type="button" disabled={readOnly} onClick={() => save(r)}
              className="rounded border border-slate-300 px-3 py-2 text-sm font-semibold hover:bg-slate-50 disabled:opacity-50">
              Save
            </button>
          </div>
        ))}
      </div>
      {message && <p className="mt-3 text-sm text-slate-700">{message}</p>}
    </div>
  );
}

/** Zones with their officers and open / overdue reports; zones without an officer stand out. */
function CoverageTable({ demoDefault }: { demoDefault: boolean }) {
  const [demo, setDemo] = useState(demoDefault);
  const [rows, setRows] = useState<{ demo: boolean; list: Coverage[] } | null>(null);
  const [message, setMessage] = useState("");

  useEffect(() => {
    let active = true;
    api<Coverage[]>(`/api/admin/coverage?demo=${demo}`)
      .then((list) => {
        if (active) setRows({ demo, list });
      })
      .catch((e) => {
        if (active) setMessage(errorText(e, "Could not load the zones"));
      });
    return () => {
      active = false;
    };
  }, [demo]);

  const list = rows?.demo === demo ? rows.list : null;
  const uncovered = list?.filter((z) => z.officers === 0).length ?? 0;

  return (
    <div className={card}>
      <div className="mb-3 flex flex-wrap items-center justify-between gap-2">
        <h2 className="text-lg font-semibold">Zones</h2>
        <label className="flex items-center gap-2 text-sm text-slate-600">
          <input type="checkbox" checked={demo} onChange={(e) => setDemo(e.target.checked)} />
          Show demo data instead
        </label>
      </div>
      {list && uncovered > 0 && (
        <p className="mb-3 text-sm text-amber-800">
          {uncovered} of {list.length} zones have no officer yet. Their reports can only be handled by an admin.
        </p>
      )}
      {message && <p className="text-sm text-red-600">{message}</p>}
      <div className="max-h-96 overflow-auto">
        <table className="w-full text-left text-sm">
          <thead className="sticky top-0 bg-slate-50 text-slate-600">
            <tr>
              <th className="px-3 py-2">Zone</th>
              <th className="px-3 py-2 text-right">Officers</th>
              <th className="px-3 py-2 text-right">Open</th>
              <th className="px-3 py-2 text-right">Overdue</th>
            </tr>
          </thead>
          <tbody>
            {list?.map((z) => (
              <tr key={z.code} className="border-t border-slate-100">
                <td className="px-3 py-1.5">
                  {z.code} - {z.name}
                </td>
                <td className="px-3 py-1.5 text-right">
                  {z.officers === 0 ? (
                    <span className="rounded bg-amber-100 px-1.5 py-0.5 text-xs font-semibold text-amber-800">none</span>
                  ) : (
                    z.officers
                  )}
                </td>
                <td className="px-3 py-1.5 text-right">{z.open}</td>
                <td className={`px-3 py-1.5 text-right ${z.overdue > 0 ? "font-semibold text-red-700" : ""}`}>
                  {z.overdue}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </div>
  );
}

/** Runs auto-close now and rebuilds the demo data (normally this happens by itself). */
function JobsCard({ readOnly }: { readOnly: boolean }) {
  const [busy, setBusy] = useState(false);
  const [message, setMessage] = useState("");

  async function runNow() {
    setBusy(true);
    setMessage("");
    try {
      const r = await api<JobResult>("/api/admin/jobs/run", { method: "POST" });
      setMessage(
        r.ran
          ? `Done. Auto-closed ${r.autoClosed} report(s). Demo data rebuilt: ${r.demoReports ?? 0} reports.`
          : "The jobs are already running - try again in a minute.",
      );
    } catch (e) {
      setMessage(errorText(e, "Could not run the jobs"));
    } finally {
      setBusy(false);
    }
  }

  return (
    <div className={card}>
      <h2 className="text-lg font-semibold">Background jobs</h2>
      <p className="mb-3 text-sm text-slate-600">
        Every hour while the server is awake (and once a day via GitHub Actions), NagarFix closes reports that stayed
        &ldquo;Resolved&rdquo; for 7 days without an answer, and rebuilds the demo data once a day.
      </p>
      <button type="button" disabled={busy || readOnly} onClick={runNow}
        className="rounded bg-slate-800 px-3 py-2 text-sm font-semibold text-white hover:bg-slate-900 disabled:opacity-50">
        {busy ? "Running..." : "Run now"}
      </button>
      {message && <p className="mt-3 text-sm text-slate-700">{message}</p>}
    </div>
  );
}

export default function AdminPanel() {
  const { user } = useAuth();
  const readOnly = Boolean(user?.demo);
  const [users, setUsers] = useState<User[]>([]);
  const [wards, setWards] = useState<WardOption[]>([]);
  const [form, setForm] = useState(emptyForm);
  const [message, setMessage] = useState("");
  const [busy, setBusy] = useState(false);

  const loadUsers = useCallback(() => {
    api<User[]>("/api/admin/users")
      .then(setUsers)
      .catch((e) => setMessage(errorText(e, "Could not load users")));
  }, []);

  useEffect(() => {
    loadUsers();
    api<WardCollection>("/api/wards")
      .then((fc) => setWards(fc.features.map((f) => f.properties)))
      .catch(() => setWards([]));
  }, [loadUsers]);

  async function createOfficer(e: React.FormEvent<HTMLFormElement>) {
    e.preventDefault();
    setBusy(true);
    setMessage("");
    try {
      await api<User>("/api/admin/users", {
        method: "POST",
        body: JSON.stringify({ ...form, role: "OFFICER" }),
      });
      setMessage(`Officer ${form.email} created for ${form.wardCode}.`);
      setForm(emptyForm);
      loadUsers();
    } catch (err) {
      setMessage(errorText(err, "Could not create officer"));
    } finally {
      setBusy(false);
    }
  }

  return (
    <section className="space-y-6">
      {readOnly && (
        <p className="rounded-lg border border-violet-200 bg-violet-50 p-3 text-sm text-violet-900">
          You are the <strong>demo admin</strong>: you can look at everything, but changes are switched off.
        </p>
      )}

      <CoverageTable demoDefault={readOnly} />
      <RulesEditor readOnly={readOnly} />
      <JobsCard readOnly={readOnly} />

      <div className={card}>
        <h2 className="mb-4 text-lg font-semibold">Create a ward officer</h2>
        <form onSubmit={createOfficer} className="grid gap-3 sm:grid-cols-2">
          <input required placeholder="Full name" value={form.fullName} disabled={readOnly}
            onChange={(e) => setForm({ ...form, fullName: e.target.value })} className={inputCls} />
          <input required type="email" placeholder="Email" value={form.email} disabled={readOnly}
            onChange={(e) => setForm({ ...form, email: e.target.value })} className={inputCls} />
          <input required type="password" minLength={8} placeholder="Temporary password (8+ characters)"
            value={form.password} disabled={readOnly}
            onChange={(e) => setForm({ ...form, password: e.target.value })} className={inputCls} />
          <select required value={form.wardCode} disabled={readOnly}
            onChange={(e) => setForm({ ...form, wardCode: e.target.value })} className={inputCls}>
            <option value="" disabled>
              Choose ward / zone
            </option>
            {wards.map((w) => (
              <option key={w.code} value={w.code}>
                {w.code} - {w.name}
              </option>
            ))}
          </select>
          <button type="submit" disabled={busy || readOnly}
            className="rounded bg-emerald-700 py-2 text-sm font-semibold text-white hover:bg-emerald-800 disabled:opacity-60 sm:col-span-2">
            {busy ? "Creating..." : "Create officer"}
          </button>
        </form>
        {message && <p className="mt-3 text-sm text-slate-700">{message}</p>}
      </div>

      <div className="overflow-x-auto rounded-lg border border-slate-200 bg-white">
        <table className="w-full text-left text-sm">
          <thead className="bg-slate-50 text-slate-600">
            <tr>
              <th className="px-4 py-2">Name</th>
              <th className="px-4 py-2">Email</th>
              <th className="px-4 py-2">Role</th>
              <th className="px-4 py-2">Ward</th>
            </tr>
          </thead>
          <tbody>
            {users.map((u) => (
              <tr key={u.id} className="border-t border-slate-100">
                <td className="px-4 py-2">
                  {u.fullName} {u.demo && <DemoBadge />}
                </td>
                <td className="px-4 py-2">{u.email}</td>
                <td className="px-4 py-2">{u.role}</td>
                <td className="px-4 py-2">{u.wardCode ?? "-"}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </section>
  );
}
