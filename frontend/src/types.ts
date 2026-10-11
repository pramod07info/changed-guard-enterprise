export type JsonObject = Record<string, unknown>;
export type Session = { subject: string; tenantId: string };
export type Change = { code: string; operation: string; location: string; explanation: string };
export type Report = {
  service: string; risk: 'NONE' | 'LOW' | 'HIGH'; requiresReview: boolean;
  breakingChanges: Change[]; potentialConsumers: { service: string; distance: number; path: string[] }[];
  recommendedTests: string[]; limitations: string[];
};
export type SavedAnalysis = { id: string; service: string; createdAt: string; beforeSnapshotId: string; afterSnapshotId: string; report: Report };
export type Summary = { id: string; service: string; createdAt: string; risk: Report['risk']; requiresReview: boolean };
export type Snapshot = { id: string; service: string; createdAt: string; document: JsonObject };
export type AnalysisInput = { service: string; before: JsonObject; after: JsonObject; dependencies: Record<string, string[]> };
