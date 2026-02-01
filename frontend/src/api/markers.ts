import { RouteMarkers } from '../types';

export type MarkerBounds = {
  minLat: number;
  maxLat: number;
  minLon: number;
  maxLon: number;
};

export type MarkerResponse =
  | { kind: 'ok'; data: RouteMarkers }
  | { kind: 'error'; message: string; status?: number };

type ApiHazardMarker = {
  id: string | number;
  latitude?: number;
  longitude?: number;
  lat?: number;
  lon?: number;
  label?: string;
  confidence?: number;
  iconType?: string;
  severity?: number;
  type?: string;
};

type ApiRouteMarkers = {
  hazardMarkers: ApiHazardMarker[];
  riskMarkers?: unknown[];
};

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

export const fetchRouteMarkers = async (bounds?: MarkerBounds): Promise<MarkerResponse> => {
  if (!bounds) {
    return { kind: 'error', message: 'Missing bounds for marker request.' };
  }

  const url = buildUrl('/routing/markers/hazards/all');
  url.searchParams.set('minLat', String(bounds.minLat));
  url.searchParams.set('maxLat', String(bounds.maxLat));
  url.searchParams.set('minLon', String(bounds.minLon));
  url.searchParams.set('maxLon', String(bounds.maxLon));

  try {
    const response = await fetch(url.toString());
    if (response.ok) {
      const data = (await response.json()) as ApiRouteMarkers;
      const normalized: RouteMarkers = {
        hazardMarkers: (data.hazardMarkers ?? [])
          .map((marker) => ({
            id: marker.id,
            lat: marker.lat ?? marker.latitude ?? NaN,
            lon: marker.lon ?? marker.longitude ?? NaN,
            label: marker.label,
            confidence: marker.confidence,
            iconType: marker.iconType,
            severity: marker.severity,
            type: marker.type,
          }))
          .filter((marker) => Number.isFinite(marker.lat) && Number.isFinite(marker.lon)),
        riskMarkers: [],
      };
      return { kind: 'ok', data: normalized };
    }

    const message = await readErrorMessage(response);
    return {
      kind: 'error',
      message: message || 'Failed to fetch markers.',
      status: response.status,
    };
  } catch (error) {
    const message = error instanceof Error ? error.message : 'Network error.';
    return { kind: 'error', message };
  }
};
