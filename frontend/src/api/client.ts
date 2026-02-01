import { RouteResult } from '../types';

export type RouteParams = {
  startLat: number;
  startLon: number;
  endLat: number;
  endLon: number;
};

export type RouteResponse =
  | { kind: 'ok'; data: RouteResult }
  | { kind: 'no_route'; message: string }
  | { kind: 'error'; message: string; status?: number };

export type RouteVariantKind = 'walkSafe' | 'walkAccessible' | 'walkSafeAccessible' | 'driveFast' | 'driveSafe';

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

const routeVariantPaths: Record<RouteVariantKind, string> = {
  walkSafe: '/routing/route/walk/safe',
  walkAccessible: '/routing/route/walk/accessible',
  walkSafeAccessible: '/routing/route/walk/safe-accessible',
  driveFast: '/routing/route/drive',
  driveSafe: '/routing/route/drive/safe',
};

export const fetchRouteVariant = async (params: RouteParams, variant: RouteVariantKind): Promise<RouteResponse> => {
  const url = buildUrl(routeVariantPaths[variant]);
  url.searchParams.set('startLat', String(params.startLat));
  url.searchParams.set('startLon', String(params.startLon));
  url.searchParams.set('endLat', String(params.endLat));
  url.searchParams.set('endLon', String(params.endLon));
  try {
    const response = await fetch(url.toString());
    if (response.ok) {
      const data = (await response.json()) as RouteResult;
      return { kind: 'ok', data };
    }

    const message = await readErrorMessage(response);
    if (response.status === 422) {
      return { kind: 'no_route', message: message || 'No route found.' };
    }

    return {
      kind: 'error',
      message: message || 'Routing failed. Please try again.',
      status: response.status,
    };
  } catch (error) {
    const message = error instanceof Error ? error.message : 'Network error.';
    return { kind: 'error', message };
  }
};
