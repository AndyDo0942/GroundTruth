export type GeocodeResult = {
  lat: number;
  lon: number;
  label: string;
};

export type GeocodeResponse =
  | { kind: 'ok'; data: GeocodeResult }
  | { kind: 'not_found'; message: string }
  | { kind: 'error'; message: string; status?: number };

export type ReverseGeocodeResponse =
  | { kind: 'ok'; label: string }
  | { kind: 'not_found'; message: string }
  | { kind: 'error'; message: string; status?: number };

type NominatimResult = {
  lat: string;
  lon: string;
  display_name?: string;
};

type NominatimReverseResult = {
  display_name?: string;
};

const nominatimBaseUrl = 'https://nominatim.openstreetmap.org';

export const geocodePlace = async (query: string): Promise<GeocodeResponse> => {
  const url = new URL('/search', nominatimBaseUrl);
  url.searchParams.set('format', 'jsonv2');
  url.searchParams.set('q', query);
  url.searchParams.set('limit', '1');
  url.searchParams.set('addressdetails', '0');

  try {
    const response = await fetch(url.toString(), {
      headers: {
        Accept: 'application/json',
      },
    });

    if (!response.ok) {
      return {
        kind: 'error',
        message: response.statusText || 'Place search failed.',
        status: response.status,
      };
    }

    const results = (await response.json()) as NominatimResult[];
    if (!Array.isArray(results) || results.length === 0) {
      return { kind: 'not_found', message: 'No matching place found.' };
    }

    const first = results[0];
    const lat = Number(first.lat);
    const lon = Number(first.lon);
    if (!Number.isFinite(lat) || !Number.isFinite(lon)) {
      return { kind: 'error', message: 'Invalid coordinates from geocoder.' };
    }

    return {
      kind: 'ok',
      data: {
        lat,
        lon,
        label: first.display_name ?? query,
      },
    };
  } catch (error) {
    const message = error instanceof Error ? error.message : 'Network error.';
    return { kind: 'error', message };
  }
};

export const reverseGeocode = async (lat: number, lon: number): Promise<ReverseGeocodeResponse> => {
  const url = new URL('/reverse', nominatimBaseUrl);
  url.searchParams.set('format', 'jsonv2');
  url.searchParams.set('lat', String(lat));
  url.searchParams.set('lon', String(lon));
  url.searchParams.set('zoom', '18');
  url.searchParams.set('addressdetails', '0');

  try {
    const response = await fetch(url.toString(), {
      headers: {
        Accept: 'application/json',
      },
    });

    if (!response.ok) {
      return {
        kind: 'error',
        message: response.statusText || 'Reverse geocoding failed.',
        status: response.status,
      };
    }

    const result = (await response.json()) as NominatimReverseResult;
    if (!result || !result.display_name) {
      return { kind: 'not_found', message: 'No location name found.' };
    }

    return { kind: 'ok', label: result.display_name };
  } catch (error) {
    const message = error instanceof Error ? error.message : 'Network error.';
    return { kind: 'error', message };
  }
};
