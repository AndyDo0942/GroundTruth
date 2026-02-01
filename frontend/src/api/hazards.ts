import { HazardReportType, HazardResponse } from '../types';

const apiBaseUrl = import.meta.env.VITE_API_BASE_URL;

const buildUrl = (path: string) => {
  if (apiBaseUrl) {
    return new URL(path, apiBaseUrl);
  }
  return new URL(path, window.location.origin);
};

const readErrorMessage = async (response: Response) => {
  const contentType = response.headers.get('content-type') ?? '';
  if (contentType.includes('application/json')) {
    const body = await response.json().catch(() => null);
    if (body && typeof body.message === 'string') {
      return body.message;
    }
    if (body) {
      return JSON.stringify(body);
    }
  }
  const text = await response.text().catch(() => '');
  return text || response.statusText || 'Request failed.';
};

export type HazardReportRequest = {
  type: HazardReportType;
  latitude: number;
  longitude: number;
  severity: number;
};

export type HazardReportImageMetadata = {
  latitude: number;
  longitude: number;
};

export const submitHazardReport = async (payload: HazardReportRequest): Promise<HazardResponse> => {
  const url = buildUrl('/api/v1/hazards');
  const response = await fetch(url.toString(), {
    method: 'POST',
    headers: { 'content-type': 'application/json' },
    body: JSON.stringify(payload),
  });

  if (!response.ok) {
    const message = await readErrorMessage(response);
    throw new Error(message || 'Hazard upload failed.');
  }

  return (await response.json()) as HazardResponse;
};

export const submitHazardReportWithImage = async (
  file: File,
  metadata: HazardReportImageMetadata
): Promise<HazardResponse> => {
  const url = buildUrl('/api/v1/hazards');
  const form = new FormData();
  form.append('image', file);
  form.append('metadata', JSON.stringify(metadata));

  const response = await fetch(url.toString(), {
    method: 'POST',
    body: form,
  });

  if (!response.ok) {
    const message = await readErrorMessage(response);
    throw new Error(message || 'Hazard upload failed.');
  }

  return (await response.json()) as HazardResponse;
};
