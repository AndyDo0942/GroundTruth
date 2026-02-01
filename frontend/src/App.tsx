import { type ChangeEvent, type CSSProperties, useCallback, useEffect, useMemo, useRef, useState } from 'react';
import MapView from './components/MapView';
import { fetchRouteVariant, type RouteVariantKind } from './api/client';
import { geocodePlace, reverseGeocode } from './api/geocode';
import { fetchRouteMarkers } from './api/markers';
import { submitHazardReport, submitHazardReportWithImage } from './api/hazards';
import { HazardReportType, HazardResponse, LatLon, RouteMarkers, RouteResult, TravelMode } from './types';

const MAX_IMAGE_BYTES = 10 * 1024 * 1024;
const ALLOWED_IMAGE_TYPES = new Set(['image/jpeg', 'image/png', 'image/webp']);
const DEFAULT_HAZARD_TYPE: HazardReportType = 'cracks';

const parseNumber = (value: string) => {
  const parsed = Number(value);
  return Number.isFinite(parsed) ? parsed : null;
};

const isValidLat = (value: number) => value >= -90 && value <= 90;
const isValidLon = (value: number) => value >= -180 && value <= 180;

const formatCoord = (value: number) => value.toFixed(6);
const isSameLocation = (a: LatLon, b: LatLon, epsilon = 1e-6) =>
  Math.abs(a.lat - b.lat) < epsilon && Math.abs(a.lon - b.lon) < epsilon;

const routeKindLabels: Record<RouteVariantKind, string> = {
  walkSafe: 'Safe',
  walkAccessible: 'Accessible',
  walkSafeAccessible: 'Safe + Accessible',
  driveFast: 'Fastest',
  driveSafe: 'Safe',
};

const routeColors: Record<RouteVariantKind, string> = {
  walkSafe: '#16a34a',
  walkAccessible: '#1e3a8a',
  walkSafeAccessible: '#1f6feb',
  driveFast: '#1f6feb',
  driveSafe: '#16a34a',
};

const hexToRgba = (hex: string, alpha: number) => {
  const value = hex.replace('#', '');
  if (value.length !== 6) {
    return `rgba(0, 0, 0, ${alpha})`;
  }
  const r = parseInt(value.slice(0, 2), 16);
  const g = parseInt(value.slice(2, 4), 16);
  const b = parseInt(value.slice(4, 6), 16);
  return `rgba(${r}, ${g}, ${b}, ${alpha})`;
};

type RouteChoice = {
  id: string;
  kinds: RouteVariantKind[];
  result: RouteResult;
  color: string;
  label: string;
};

type MarkerBounds = {
  minLat: number;
  maxLat: number;
  minLon: number;
  maxLon: number;
};

const App = () => {
  const [start, setStart] = useState<LatLon | null>(null);
  const [end, setEnd] = useState<LatLon | null>(null);
  const [travelMode, setTravelMode] = useState<TravelMode>('WALK');

  const [startLat, setStartLat] = useState('');
  const [startLon, setStartLon] = useState('');
  const [endLat, setEndLat] = useState('');
  const [endLon, setEndLon] = useState('');
  const [startPlace, setStartPlace] = useState('');
  const [endPlace, setEndPlace] = useState('');

  const [routeChoices, setRouteChoices] = useState<RouteChoice[]>([]);
  const [selectedRouteId, setSelectedRouteId] = useState<string | null>(null);
  const [routeMarkers, setRouteMarkers] = useState<Record<string, RouteMarkers>>({});
  const [mapBounds, setMapBounds] = useState<MarkerBounds | null>(null);
  const [loading, setLoading] = useState(false);
  const [geocoding, setGeocoding] = useState<'start' | 'end' | null>(null);
  const [error, setError] = useState<string | null>(null);

  const [geoStatus, setGeoStatus] = useState<'idle' | 'locating' | 'ready' | 'unavailable'>('idle');
  const [geoError, setGeoError] = useState<string | null>(null);
  const [deviceLocation, setDeviceLocation] = useState<{ lat: number; lon: number; accuracy: number } | null>(null);

  const [hazardUploading, setHazardUploading] = useState(false);
  const [hazardSuccess, setHazardSuccess] = useState<HazardResponse | null>(null);
  const [hazardError, setHazardError] = useState<string | null>(null);
  const [hazardType, setHazardType] = useState<HazardReportType>(DEFAULT_HAZARD_TYPE);
  const [hazardSeverity, setHazardSeverity] = useState('72');
  const [hazardReportMode, setHazardReportMode] = useState<'quick' | 'photo'>('quick');
  const [hazardFile, setHazardFile] = useState<File | null>(null);
  const [hazardPickMode, setHazardPickMode] = useState(false);
  const [hazardLat, setHazardLat] = useState('');
  const [hazardLon, setHazardLon] = useState('');
  const [hazardLocation, setHazardLocation] = useState<LatLon | null>(null);
  const hazardFileRef = useRef<HTMLInputElement | null>(null);
  const startRef = useRef<LatLon | null>(null);
  const hasDefaultedStart = useRef(false);

  const selectedRoute = useMemo(
    () => routeChoices.find((choice) => choice.id === selectedRouteId) ?? null,
    [routeChoices, selectedRouteId]
  );

  const requestDeviceLocation = () => {
    if (!navigator.geolocation) {
      setGeoStatus('unavailable');
      setGeoError('Geolocation is not supported by this browser.');
      setDeviceLocation(null);
      return;
    }

    setGeoStatus('locating');
    setGeoError(null);
    navigator.geolocation.getCurrentPosition(
      (position) => {
        const lat = position.coords.latitude;
        const lon = position.coords.longitude;

        setGeoStatus('ready');
        setDeviceLocation({
          lat,
          lon,
          accuracy: position.coords.accuracy,
        });

        const currentStart = startRef.current;
        if (!hasDefaultedStart.current && !currentStart) {
          const defaultLocation = { lat, lon };
          setStart(defaultLocation);
          setStartLat(formatCoord(lat));
          setStartLon(formatCoord(lon));
          setStartPlace('Current location');
          hasDefaultedStart.current = true;

          reverseGeocode(lat, lon).then((result) => {
            if (result.kind !== 'ok') {
              return;
            }
            const latestStart = startRef.current;
            if (latestStart && isSameLocation(latestStart, defaultLocation)) {
              setStartPlace(result.label);
            }
          });
        }
      },
      (geoErr) => {
        let message = geoErr.message || 'Unable to retrieve location.';
        if (geoErr.code === geoErr.PERMISSION_DENIED) {
          message = 'Permission denied for location.';
        } else if (geoErr.code === geoErr.POSITION_UNAVAILABLE) {
          message = 'Location information is unavailable.';
        } else if (geoErr.code === geoErr.TIMEOUT) {
          message = 'Location request timed out.';
        }
        setGeoStatus('unavailable');
        setGeoError(message);
        setDeviceLocation(null);
      },
      { enableHighAccuracy: true, timeout: 8000, maximumAge: 60000 }
    );
  };

  useEffect(() => {
    requestDeviceLocation();
  }, []);

  useEffect(() => {
    startRef.current = start;
  }, [start]);

  const clearRouteState = () => {
    setRouteChoices([]);
    setSelectedRouteId(null);
    setRouteMarkers({});
    setError(null);
  };

  const updateStartFromInputs = (nextLat: string, nextLon: string) => {
    setStartLat(nextLat);
    setStartLon(nextLon);
    const latValue = parseNumber(nextLat);
    const lonValue = parseNumber(nextLon);
    if (latValue !== null && lonValue !== null && isValidLat(latValue) && isValidLon(lonValue)) {
      setStart({ lat: latValue, lon: lonValue });
    } else {
      setStart(null);
    }
    setStartPlace('');
    clearRouteState();
  };

  const updateEndFromInputs = (nextLat: string, nextLon: string) => {
    setEndLat(nextLat);
    setEndLon(nextLon);
    const latValue = parseNumber(nextLat);
    const lonValue = parseNumber(nextLon);
    if (latValue !== null && lonValue !== null && isValidLat(latValue) && isValidLon(lonValue)) {
      setEnd({ lat: latValue, lon: lonValue });
    } else {
      setEnd(null);
    }
    setEndPlace('');
    clearRouteState();
  };

  const applyGeocodeResult = (kind: 'start' | 'end', lat: number, lon: number, label: string) => {
    const formattedLat = formatCoord(lat);
    const formattedLon = formatCoord(lon);

    if (kind === 'start') {
      setStart({ lat, lon });
      setStartLat(formattedLat);
      setStartLon(formattedLon);
      setStartPlace(label);
      return;
    }

    setEnd({ lat, lon });
    setEndLat(formattedLat);
    setEndLon(formattedLon);
    setEndPlace(label);
  };

  const handlePlaceSearch = async (kind: 'start' | 'end') => {
    const query = (kind === 'start' ? startPlace : endPlace).trim();
    if (!query) {
      setError('Please enter a place name.');
      return;
    }

    clearRouteState();
    setGeocoding(kind);
    const result = await geocodePlace(query);
    setGeocoding(null);

    if (result.kind === 'ok') {
      if (!isValidLat(result.data.lat) || !isValidLon(result.data.lon)) {
        setError('Geocoder returned out-of-range coordinates.');
        return;
      }
      applyGeocodeResult(kind, result.data.lat, result.data.lon, result.data.label);
      return;
    }

    setError(result.message);
  };

  const handleMapClick = (lat: number, lon: number) => {
    clearRouteState();
    const formattedLat = formatCoord(lat);
    const formattedLon = formatCoord(lon);

    if (!start) {
      setStart({ lat, lon });
      setStartLat(formattedLat);
      setStartLon(formattedLon);
      setStartPlace('');
      return;
    }

    if (!end) {
      setEnd({ lat, lon });
      setEndLat(formattedLat);
      setEndLon(formattedLon);
      setEndPlace('');
      return;
    }

    // Third click resets the cycle to a new start point.
    setStart({ lat, lon });
    setEnd(null);
    setStartLat(formattedLat);
    setStartLon(formattedLon);
    setEndLat('');
    setEndLon('');
    setStartPlace('');
    setEndPlace('');
  };

  const handleRoute = async () => {
    clearRouteState();

    if (!start || !end) {
      setError('Please set both a start and end location.');
      return;
    }

    if (!isValidLat(start.lat) || !isValidLon(start.lon) || !isValidLat(end.lat) || !isValidLon(end.lon)) {
      setError('Coordinates are out of range.');
      return;
    }

    setLoading(true);
    const params = {
      startLat: start.lat,
      startLon: start.lon,
      endLat: end.lat,
      endLon: end.lon,
    };

    const variants: RouteVariantKind[] =
      travelMode === 'DRIVE'
        ? ['driveFast', 'driveSafe']
        : ['walkSafe', 'walkAccessible', 'walkSafeAccessible'];
    const responses = await Promise.all(variants.map((variant) => fetchRouteVariant(params, variant)));
    setLoading(false);

    const okRoutes: Array<{ kind: RouteVariantKind; result: RouteResult }> = [];
    const errorMessages: string[] = [];

    responses.forEach((response, index) => {
      const kind = variants[index];
      if (response.kind === 'ok') {
        okRoutes.push({ kind, result: response.data });
        return;
      }

      if (response.kind === 'no_route') {
        const message = response.message ? `No ${routeKindLabels[kind]} route: ${response.message}` : 'No route found.';
        errorMessages.push(message);
        return;
      }

      errorMessages.push(`${routeKindLabels[kind]} route error: ${response.message}`);
    });

    if (okRoutes.length === 0) {
      setError(errorMessages[0] ?? 'No route found.');
      return;
    }

    const consolidated = new Map<string, { kinds: RouteVariantKind[]; result: RouteResult }>();
    okRoutes.forEach(({ kind, result }) => {
      const coords = result.routeGeojson?.geometry?.coordinates ?? [];
      const key = JSON.stringify(coords);
      const entry = consolidated.get(key);
      if (entry) {
        entry.kinds.push(kind);
      } else {
        consolidated.set(key, { kinds: [kind], result });
      }
    });

    const kindOrder: RouteVariantKind[] =
      travelMode === 'DRIVE'
        ? ['driveFast', 'driveSafe']
        : ['walkSafeAccessible', 'walkSafe', 'walkAccessible'];
    const choices: RouteChoice[] = Array.from(consolidated.values()).map(({ kinds, result }) => {
      const orderedKinds = kindOrder.filter((kind) => kinds.includes(kind));
      const color = orderedKinds[0] ? routeColors[orderedKinds[0]] : '#1f6feb';
      return {
        id: orderedKinds.join('-'),
        kinds: orderedKinds,
        result,
        color,
        label: orderedKinds.map((kind) => routeKindLabels[kind]).join(' · '),
      };
    });

    choices.sort((a, b) => {
      const aIndex = kindOrder.findIndex((kind) => a.kinds.includes(kind));
      const bIndex = kindOrder.findIndex((kind) => b.kinds.includes(kind));
      return aIndex - bIndex;
    });

    setRouteChoices(choices);
    const defaultChoice = choices.find((choice) =>
      travelMode === 'DRIVE' ? choice.kinds.includes('driveFast') : choice.kinds.includes('walkSafeAccessible')
    );
    setSelectedRouteId(defaultChoice?.id ?? choices[0]?.id ?? null);

    if (mapBounds) {
      const response = await fetchRouteMarkers(mapBounds);
      if (response.kind === 'ok') {
        const nextMarkers: Record<string, RouteMarkers> = {};
        choices.forEach((choice) => {
          nextMarkers[choice.id] = response.data;
        });
        setRouteMarkers(nextMarkers);
      }
    }
  };

  const resetHazardMessages = () => {
    setHazardError(null);
    setHazardSuccess(null);
  };

  const handleHazardFileChange = (event: ChangeEvent<HTMLInputElement>) => {
    const file = event.target.files?.[0] ?? null;
    setHazardFile(file);
    resetHazardMessages();
  };

  const handleHazardSubmit = async () => {
    resetHazardMessages();

    if (geoStatus !== 'ready' || !deviceLocation) {
      setHazardError('Location is unavailable. Please refresh location and try again.');
      return;
    }

    const selectedLocation = hazardLocation ?? (deviceLocation ? { lat: deviceLocation.lat, lon: deviceLocation.lon } : null);
    if (!selectedLocation) {
      setHazardError('Please select a location for the hazard report.');
      return;
    }

    setHazardUploading(true);
    try {
      if (hazardReportMode === 'photo') {
        if (!hazardFile) {
          setHazardError('Please select an image to upload.');
          setHazardUploading(false);
          return;
        }
        if (!ALLOWED_IMAGE_TYPES.has(hazardFile.type)) {
          setHazardError('Image type must be JPEG, PNG, or WEBP.');
          setHazardUploading(false);
          return;
        }
        if (hazardFile.size > MAX_IMAGE_BYTES) {
          setHazardError('Image must be 10MB or smaller.');
          setHazardUploading(false);
          return;
        }
        const response = await submitHazardReportWithImage(hazardFile, {
          latitude: selectedLocation.lat,
          longitude: selectedLocation.lon,
        });
        setHazardSuccess(response);
        setHazardFile(null);
        if (hazardFileRef.current) {
          hazardFileRef.current.value = '';
        }
      } else {
        const severityValue = parseNumber(hazardSeverity);
        if (severityValue === null || severityValue < 0 || severityValue > 100) {
          setHazardError('Severity must be between 0 and 100.');
          setHazardUploading(false);
          return;
        }
        const response = await submitHazardReport({
          type: hazardType,
          latitude: selectedLocation.lat,
          longitude: selectedLocation.lon,
          severity: Math.round(severityValue),
        });
        setHazardSuccess(response);
      }
      setHazardPickMode(false);
      setHazardLocation(null);
      setHazardLat('');
      setHazardLon('');
    } catch (err) {
      const message = err instanceof Error ? err.message : 'Upload failed.';
      setHazardError(message);
    } finally {
      setHazardUploading(false);
    }
  };

  const handleClear = () => {
    setStart(null);
    setEnd(null);
    setStartLat('');
    setStartLon('');
    setEndLat('');
    setEndLon('');
    setStartPlace('');
    setEndPlace('');
    setGeocoding(null);
    setRouteChoices([]);
    setSelectedRouteId(null);
    setRouteMarkers({});
    setError(null);
  };

  const distanceKm = useMemo(() => {
    if (!selectedRoute) return null;
    return (selectedRoute.result.distanceMeters / 1000).toFixed(2);
  }, [selectedRoute]);

  const durationMinutes = useMemo(() => {
    if (!selectedRoute) return null;
    return (selectedRoute.result.durationSeconds / 60).toFixed(1);
  }, [selectedRoute]);

  const formatList = (items: number[]) => items.slice(0, 10).join(', ');
  const geocodeStatus =
    geocoding === 'start'
      ? 'Searching for start place...'
      : geocoding === 'end'
      ? 'Searching for end place...'
      : null;
  const routeDisabled = loading || !start || !end;
  const hazardInputsDisabled = geoStatus !== 'ready' || hazardUploading;
  const hazardSubmitDisabled =
    hazardInputsDisabled ||
    (!hazardLocation && (!deviceLocation || geoStatus !== 'ready')) ||
    (hazardReportMode === 'photo' && !hazardFile);
  const mapRoutes = useMemo(() => {
    const mapped = routeChoices.map((choice) => ({
      id: choice.id,
      color: choice.color,
      coordinates: choice.result.routeGeojson.geometry.coordinates ?? [],
    }));

    if (!selectedRouteId) {
      return mapped;
    }

    const selected = mapped.find((route) => route.id === selectedRouteId);
    const rest = mapped.filter((route) => route.id !== selectedRouteId);
    return selected ? [...rest, selected] : mapped;
  }, [routeChoices, selectedRouteId]);
  const activeRouteId = selectedRouteId ?? routeChoices[0]?.id ?? null;
  const activeMarkers = activeRouteId ? routeMarkers[activeRouteId] ?? null : null;
  const locationLabel =
    geoStatus === 'ready' && deviceLocation
      ? `Location: ${deviceLocation.lat.toFixed(6)}, ${deviceLocation.lon.toFixed(6)} (accuracy ~ ${Math.round(
          deviceLocation.accuracy
        )} m)`
      : geoStatus === 'locating'
      ? 'Location: Locating...'
      : geoStatus === 'unavailable'
      ? `Location unavailable: ${geoError ?? 'Unable to retrieve location.'}`
      : 'Location: Not requested';

  const handleHazardLocationInput = (nextLat: string, nextLon: string) => {
    setHazardLat(nextLat);
    setHazardLon(nextLon);
    const latValue = parseNumber(nextLat);
    const lonValue = parseNumber(nextLon);
    if (latValue !== null && lonValue !== null && isValidLat(latValue) && isValidLon(lonValue)) {
      setHazardLocation({ lat: latValue, lon: lonValue });
    } else {
      setHazardLocation(null);
    }
  };

  useEffect(() => {
    if (!mapBounds || routeChoices.length === 0) {
      return;
    }
    let cancelled = false;
    const run = async () => {
      const response = await fetchRouteMarkers(mapBounds);
      if (cancelled || response.kind !== 'ok') {
        return;
      }
      const nextMarkers: Record<string, RouteMarkers> = {};
      routeChoices.forEach((choice) => {
        nextMarkers[choice.id] = response.data;
      });
      setRouteMarkers(nextMarkers);
    };
    run();
    return () => {
      cancelled = true;
    };
  }, [mapBounds, routeChoices]);

  const routeOptionStyle = (choice: RouteChoice, selected: boolean): CSSProperties =>
    ({
      '--route-color': choice.color,
      '--route-bg': hexToRgba(choice.color, selected ? 0.2 : 0.1),
    }) as CSSProperties;

  const handleBoundsChange = useCallback((bounds: MarkerBounds) => {
    setMapBounds(bounds);
  }, []);

  return (
    <div className="app">
      <aside className="panel">
        <header>
          <h1>Route Planner</h1>
          <p>Click the map, search for a place, or enter coordinates to plan a route.</p>
        </header>

        <section className="section">
          <h2>Travel mode</h2>
          <div className="mode-toggle">
            <button
              type="button"
              className={`toggle-button ${travelMode === 'WALK' ? 'active' : ''}`}
              onClick={() => {
                if (travelMode !== 'WALK') {
                  setTravelMode('WALK');
                  clearRouteState();
                }
              }}
            >
              Walk
            </button>
            <button
              type="button"
              className={`toggle-button ${travelMode === 'DRIVE' ? 'active' : ''}`}
              onClick={() => {
                if (travelMode !== 'DRIVE') {
                  setTravelMode('DRIVE');
                  clearRouteState();
                }
              }}
            >
              Drive
            </button>
          </div>
        </section>

        <section className="section">
          <h2>Start</h2>
          <div className="place-row">
            <label className="full">
              Place name
              <input
                type="text"
                value={startPlace}
                onChange={(event) => setStartPlace(event.target.value)}
                onKeyDown={(event) => {
                  if (event.key === 'Enter') {
                    event.preventDefault();
                    handlePlaceSearch('start');
                  }
                }}
                placeholder="e.g. Ithaca Commons"
              />
            </label>
            <button type="button" className="secondary" onClick={() => handlePlaceSearch('start')} disabled={geocoding === 'start'}>
              {geocoding === 'start' ? 'Searching...' : 'Find'}
            </button>
          </div>
          <div className="grid">
            <label>
              Lat
              <input
                type="number"
                inputMode="decimal"
                value={startLat}
                onChange={(event) => updateStartFromInputs(event.target.value, startLon)}
                placeholder="e.g. 42.4440"
              />
            </label>
            <label>
              Lon
              <input
                type="number"
                inputMode="decimal"
                value={startLon}
                onChange={(event) => updateStartFromInputs(startLat, event.target.value)}
                placeholder="e.g. -76.4951"
              />
            </label>
          </div>
        </section>

        <section className="section">
          <h2>End</h2>
          <div className="place-row">
            <label className="full">
              Place name
              <input
                type="text"
                value={endPlace}
                onChange={(event) => setEndPlace(event.target.value)}
                onKeyDown={(event) => {
                  if (event.key === 'Enter') {
                    event.preventDefault();
                    handlePlaceSearch('end');
                  }
                }}
                placeholder="e.g. Cornell University"
              />
            </label>
            <button type="button" className="secondary" onClick={() => handlePlaceSearch('end')} disabled={geocoding === 'end'}>
              {geocoding === 'end' ? 'Searching...' : 'Find'}
            </button>
          </div>
          <div className="grid">
            <label>
              Lat
              <input
                type="number"
                inputMode="decimal"
                value={endLat}
                onChange={(event) => updateEndFromInputs(event.target.value, endLon)}
                placeholder="e.g. 42.4442"
              />
            </label>
            <label>
              Lon
              <input
                type="number"
                inputMode="decimal"
                value={endLon}
                onChange={(event) => updateEndFromInputs(endLat, event.target.value)}
                placeholder="e.g. -76.4947"
              />
            </label>
          </div>
        </section>

        <div className="actions">
          <button type="button" onClick={handleRoute} disabled={routeDisabled}>
            {loading ? 'Routing...' : 'Route'}
          </button>
          <button type="button" className="secondary" onClick={handleClear}>
            Clear
          </button>
        </div>

        {error && <div className="status error">{error}</div>}
        {geocodeStatus && <div className="status">{geocodeStatus}</div>}
        {loading && <div className="status">Fetching route...</div>}

        <section className="section">
          <h2>Routes</h2>
          {routeChoices.length === 0 && <p className="muted">No route yet. Click Route to calculate.</p>}
          {routeChoices.length > 0 && (
            <>
              <div className="route-options">
                {routeChoices.map((choice) => (
                  <button
                    key={choice.id}
                    type="button"
                    className={`route-option ${selectedRouteId === choice.id ? 'selected' : ''}`}
                    style={routeOptionStyle(choice, selectedRouteId === choice.id)}
                    onClick={() => setSelectedRouteId(choice.id)}
                  >
                    <span>{choice.label}</span>
                  </button>
                ))}
              </div>
              {selectedRoute && (
                <div className="results">
                  <div className="route-label">
                    <span>Selected</span>
                    <strong>{selectedRoute.label}</strong>
                  </div>
                  <div>
                    <span>Distance</span>
                    <strong>
                      {selectedRoute.result.distanceMeters.toFixed(1)} m ({distanceKm} km)
                    </strong>
                  </div>
                  <div>
                    <span>Duration</span>
                    <strong>
                      {selectedRoute.result.durationSeconds.toFixed(1)} s ({durationMinutes} min)
                    </strong>
                  </div>
                  <details>
                    <summary>Debug info</summary>
                    <div className="debug">
                      <div>
                        <span>Node count</span>
                        <strong>{selectedRoute.result.pathNodeIds.length}</strong>
                      </div>
                      <div>
                        <span>Edge count</span>
                        <strong>{selectedRoute.result.pathEdgeIds.length}</strong>
                      </div>
                      <div>
                        <span>First 10 node IDs</span>
                        <strong>{formatList(selectedRoute.result.pathNodeIds) || '—'}</strong>
                      </div>
                      <div>
                        <span>First 10 edge IDs</span>
                        <strong>{formatList(selectedRoute.result.pathEdgeIds) || '—'}</strong>
                      </div>
                    </div>
                  </details>
                </div>
              )}
            </>
          )}
        </section>

        <section className="section">
          <h2>Hazard Report</h2>
          <div className="location-row">
            <div className="location-text">{locationLabel}</div>
            <button type="button" className="secondary" onClick={requestDeviceLocation} disabled={geoStatus === 'locating'}>
              {geoStatus === 'locating' ? 'Locating...' : 'Refresh location'}
            </button>
          </div>
          <label className="full">
            Report mode
            <select
              value={hazardReportMode}
              onChange={(event) => setHazardReportMode(event.target.value as 'quick' | 'photo')}
              disabled={hazardInputsDisabled}
            >
              <option value="quick">Quick report</option>
              <option value="photo">Photo report</option>
            </select>
          </label>
          {hazardReportMode === 'quick' && (
            <div className="grid">
              <label>
                Type
                <select
                  value={hazardType}
                  onChange={(event) => setHazardType(event.target.value as HazardReportType)}
                  disabled={hazardInputsDisabled}
                >
                  <option value="cracks">Cracks</option>
                  <option value="blocked sidewalk">Blocked sidewalk</option>
                </select>
              </label>
              <label>
                Severity (0-100)
                <input
                  type="number"
                  min={0}
                  max={100}
                  value={hazardSeverity}
                  onChange={(event) => setHazardSeverity(event.target.value)}
                  disabled={hazardInputsDisabled}
                />
              </label>
            </div>
          )}
          <div className="grid">
            <label>
              Hazard Lat
              <input
                type="number"
                inputMode="decimal"
                value={hazardLat}
                onChange={(event) => handleHazardLocationInput(event.target.value, hazardLon)}
                placeholder="e.g. 42.4440"
                disabled={hazardInputsDisabled}
              />
            </label>
            <label>
              Hazard Lon
              <input
                type="number"
                inputMode="decimal"
                value={hazardLon}
                onChange={(event) => handleHazardLocationInput(hazardLat, event.target.value)}
                placeholder="e.g. -76.4951"
                disabled={hazardInputsDisabled}
              />
            </label>
          </div>
          <div className="hazard-location-actions">
            <button
              type="button"
              className={`secondary ${hazardPickMode ? 'is-active' : ''}`}
              onClick={() => setHazardPickMode((prev) => !prev)}
              disabled={hazardInputsDisabled}
            >
              {hazardPickMode ? 'Click map to set location' : 'Pick location on map'}
            </button>
            {hazardLocation && (
              <button
                type="button"
                className="secondary"
                onClick={() => {
                  setHazardLocation(null);
                  setHazardLat('');
                  setHazardLon('');
                  setHazardPickMode(false);
                }}
                disabled={hazardInputsDisabled}
              >
                Clear location
              </button>
            )}
          </div>
          {hazardReportMode === 'photo' && (
            <div className="file-row">
              <input
                ref={hazardFileRef}
                className="file-input"
                type="file"
                accept="image/*"
                capture="environment"
                onChange={handleHazardFileChange}
                disabled={hazardInputsDisabled}
              />
              <button
                type="button"
                className="secondary file-button"
                onClick={() => hazardFileRef.current?.click()}
                disabled={hazardInputsDisabled}
              >
                Take Photo
              </button>
              <span className="file-name">{hazardFile ? hazardFile.name : 'No file selected'}</span>
            </div>
          )}
          <button type="button" className="hazard-submit" onClick={handleHazardSubmit} disabled={hazardSubmitDisabled}>
            {hazardUploading ? 'Uploading...' : 'Submit hazard'}
          </button>
          <p className="helper">Your report will be reviewed (status: PENDING).</p>
          {hazardError && <div className="status error">{hazardError}</div>}
          {hazardSuccess && <div className="status">Hazard submitted. ID: {hazardSuccess.id}</div>}
        </section>
      </aside>

      <main className="map-wrap">
        <MapView
          start={start}
          end={end}
          deviceLocation={deviceLocation}
          deviceZoom={17}
          routes={mapRoutes}
          selectedRouteId={selectedRouteId}
          onSelectRoute={(routeId) => setSelectedRouteId(routeId)}
          hazardMarkers={activeMarkers?.hazardMarkers ?? []}
          hazardLocation={hazardLocation}
          hazardPickMode={hazardPickMode}
          onHazardPick={(lat, lon) => {
            setHazardPickMode(false);
            const formattedLat = formatCoord(lat);
            const formattedLon = formatCoord(lon);
            setHazardLat(formattedLat);
            setHazardLon(formattedLon);
            setHazardLocation({ lat, lon });
          }}
          onBoundsChange={handleBoundsChange}
          onMapClick={handleMapClick}
        />
      </main>
    </div>
  );
};

export default App;
