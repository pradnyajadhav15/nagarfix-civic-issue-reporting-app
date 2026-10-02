"use client";

import { useCallback, useEffect, useState } from "react";
import { api } from "@/lib/api";
import type { User } from "@/components/AuthProvider";

type WardOption = { code: string; name: string };
type WardCollection = { features: { properties: WardOption }[] };

const emptyForm = { fullName: "", email: "", password: "", wardCode: "" };
const inputCls =
  "w-full rounded border border-slate-300 px-3 py-2 text-sm focus:border-emerald-600 focus:outline-none";

export default function AdminPanel() {
  const [users, setUsers] = useState<User[]>([]);
  const [wards, setWards] = useState<WardOption[]>([]);
  const [form, setForm] = useState(emptyForm);
  const [message, setMessage] = useState("");
  const [busy, setBusy] = useState(false);

  const loadUsers = useCallback(() => {
    api<User[]>("/api/admin/users")
      .then(setUsers)
      .catch((e) => setMessage(e instanceof Error ? e.message : "Could not load users"));
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
      setMessage(err instanceof Error ? err.message : "Could not create officer");
    } finally {
      setBusy(false);
    }
  }

  return (
    <section className="space-y-6">
      <div className="rounded-lg border border-slate-200 bg-white p-5">
        <h2 className="mb-4 text-lg font-semibold">Create a ward officer</h2>
        <form onSubmit={createOfficer} className="grid gap-3 sm:grid-cols-2">
          <input required placeholder="Full name" value={form.fullName}
            onChange={(e) => setForm({ ...form, fullName: e.target.value })} className={inputCls} />
          <input required type="email" placeholder="Email" value={form.email}
            onChange={(e) => setForm({ ...form, email: e.target.value })} className={inputCls} />
          <input required type="password" minLength={8} placeholder="Temporary password (8+ characters)"
            value={form.password} onChange={(e) => setForm({ ...form, password: e.target.value })}
            className={inputCls} />
          <select required value={form.wardCode}
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
          <button type="submit" disabled={busy}
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
                <td className="px-4 py-2">{u.fullName}</td>
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
