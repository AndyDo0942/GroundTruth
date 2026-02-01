import { HazardMetadata, HazardResponse } from '../types';

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

export const submitHazardReport = async (file: File, meta: HazardMetadata): Promise<HazardResponse> => {
  const url = buildUrl('/api/v1/hazards');
  const form = new FormData();
  form.append('image', file);
  form.append('metadata', JSON.stringify(meta));

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
