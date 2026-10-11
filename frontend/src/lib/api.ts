export class ApiError extends Error {
  constructor(public status: number, message: string) { super(message); }
}

export async function api<T>(path: string, token: string, body?: unknown): Promise<T> {
  const response = await fetch('/api/v1' + path, {
    method: body === undefined ? 'GET' : 'POST',
    headers: { Authorization: 'Bearer ' + token, ...(body === undefined ? {} : { 'Content-Type': 'application/json' }) },
    ...(body === undefined ? {} : { body: JSON.stringify(body) }),
    signal: AbortSignal.timeout(30000)
  });
  if (!response.ok) {
    let detail = response.status === 401 ? 'Your session is invalid or expired. Sign in again.' : 'Request failed (' + response.status + ').';
    try {
      const error = await response.json() as { detail?: string };
      if (typeof error.detail === 'string') detail = error.detail;
    } catch { /* An empty error body is valid for authentication failures. */ }
    throw new ApiError(response.status, detail);
  }
  return await response.json() as T;
}
