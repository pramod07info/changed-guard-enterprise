export type JsonObject = Record<string, unknown>;
export type Session = { subject: string; tenantId: string };
export type Change = { code: string; operation: string; location: string; explanation: string };
export type Report = {
  service: string; risk: 'NONE' | 'LOW' | 'HIGH'; requiresReview: boolean;
  breakingChanges: Change[]; potentialConsumers: { service: string; distance: number; path: string[] }[];
  recommendedTests: string[]; limitations: string[];
};
export type SavedAnalysis = { id: string; service: string; createdAt: string; beforeSnapshotId: string; afterSnapshotId: string; report: Report; baselineId?: string | null; githubSource?: GitHubSource | null };
export type Summary = { id: string; service: string; createdAt: string; risk: Report['risk']; requiresReview: boolean };
export type Snapshot = { id: string; service: string; createdAt: string; document: JsonObject };
export type AnalysisInput = { service: string; before: JsonObject; after: JsonObject; dependencies: Record<string, string[]> };
export type Project = { id: string; name: string; createdBy: string; createdAt: string };
export type CatalogService = { id: string; projectId: string; name: string; owner: string; repositoryUrl: string | null; createdAt: string };
export type BaselineSummary = { id: string; serviceId: string; serviceName: string; version: string; environment: string; publishedBy: string; publishedAt: string };
export type Baseline = BaselineSummary & { document: JsonObject };
export type GitHubSource = { repository: string; headRepository: string; pullNumber: number; pullUrl: string; baseSha: string; headSha: string; contractPath: string; blobSha: string; fetchedAt: string };
export type GitHubPrPreview = { baselineId: string; source: GitHubSource; report: Report };

export type WebhookSubscription = { id: string; baselineId: string; repository: string; contractPath: string; deliveryPath: string };
export type CheckPublication = { state: 'QUEUED' | 'PROCESSING' | 'PUBLISHED' | 'FAILED'; attempts: number; checkRunId: number | null; checkName: string; errorCode: string | null };
export type WebhookDelivery = { deliveryId: string; state: 'QUEUED' | 'PROCESSING' | 'SUCCEEDED' | 'SUPERSEDED' | 'FAILED'; attempts: number; analysisId: string | null; errorCode: string | null; pullNumber: number; headSha: string; createdAt: string; check: CheckPublication | null };
