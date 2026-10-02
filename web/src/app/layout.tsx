import type { Metadata } from "next";
import "./globals.css";

export const metadata: Metadata = {
  title: "NagarFix - Report civic issues in Solapur",
  description:
    "Independent student project for reporting potholes, garbage and streetlight issues.",
};

export default function RootLayout({
  children,
}: Readonly<{ children: React.ReactNode }>) {
  return (
    <html lang="en">
      <body className="antialiased">{children}</body>
    </html>
  );
}
