"use client";

import { createContext, useCallback, useContext, useEffect, useMemo, useState } from "react";
import { api, getToken, setToken } from "@/lib/api";

export type Role = "CITIZEN" | "OFFICER" | "ADMIN";
export type User = {
  id: number;
  fullName: string;
  email: string;
  role: Role;
  wardCode: string | null;
  demo?: boolean;
};
type AuthResponse = { token: string; user: User };

type AuthState = {
  user: User | null;
  loading: boolean;
  login: (email: string, password: string) => Promise<void>;
  register: (fullName: string, email: string, password: string) => Promise<void>;
  loginDemo: (role: Role) => Promise<User>;
  logout: () => void;
};

const AuthContext = createContext<AuthState | null>(null);

export function AuthProvider({ children }: { children: React.ReactNode }) {
  const [user, setUser] = useState<User | null>(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    let active = true;
    // With a saved token, ask the API who we are; without one, there is nothing to check.
    const check: Promise<User | null> = getToken() ? api<User>("/api/me") : Promise.resolve(null);
    check
      .then((u) => {
        if (active) setUser(u);
      })
      .catch(() => {
        if (active) setUser(null);
      })
      .finally(() => {
        if (active) setLoading(false);
      });
    return () => {
      active = false;
    };
  }, []);

  const login = useCallback(async (email: string, password: string) => {
    const res = await api<AuthResponse>("/api/auth/login", {
      method: "POST",
      body: JSON.stringify({ email, password }),
    });
    setToken(res.token);
    setUser(res.user);
  }, []);

  const register = useCallback(async (fullName: string, email: string, password: string) => {
    const res = await api<AuthResponse>("/api/auth/register", {
      method: "POST",
      body: JSON.stringify({ fullName, email, password }),
    });
    setToken(res.token);
    setUser(res.user);
  }, []);

  const loginDemo = useCallback(async (role: Role) => {
    const res = await api<AuthResponse>("/api/auth/demo", {
      method: "POST",
      body: JSON.stringify({ role }),
    });
    setToken(res.token);
    setUser(res.user);
    return res.user;
  }, []);

  const logout = useCallback(() => {
    setToken(null);
    setUser(null);
  }, []);

  const value = useMemo(
    () => ({ user, loading, login, register, loginDemo, logout }),
    [user, loading, login, register, loginDemo, logout],
  );
  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth(): AuthState {
  const ctx = useContext(AuthContext);
  if (!ctx) throw new Error("useAuth must be used inside AuthProvider");
  return ctx;
}
