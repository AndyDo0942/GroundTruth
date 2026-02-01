import { type ChangeEvent, useEffect, useMemo, useRef, useState } from 'react';
import MapView from './components/MapView';
import { fetchRoute } from './api/client';
import { geocodePlace, reverseGeocode } from './api/geocode';
import { submitHazardReport } from './api/hazards';
import { HazardMetadata, HazardResponse, HazardType, LatLon, RouteResult } from './types';

const MAX_IMAGE_BYTES = 10 * 1024 * 1024;
const ALLOWED_IMAGE_TYPES = new Set(['image/jpeg', 'image/png', 'image/webp']);
const DEFAULT_HAZARD_TYPE: HazardType = 'POTHOLE';

const parseNumber = (value: string) => {
  const parsed = Number(value);
  return Number.isFinite(parsed) ? parsed : null;
};

const isValidLat = (value: number) => value >= -90 && value <= 90;
const isValidLon = (value: number) => value >= -180 && value <= 180;

const formatCoord = (value: number) => value.toFixed(6);
const isSameLocation = (a: LatLon, b: LatLon, epsilon = 1e-6) =>
  Math.abs(a.lat - b.lat) < epsilon && Math.abs(a.lon - b.lon) < epsilon;

const App = () => {
  const [start, setStart] = useState<LatLon | null>(null);
  const [end, setEnd] = useState<LatLon | null>(null);

  const [startLat, setStartLat] = useState('');
  const [startLon, setStartLon] = useState('');
  const [endLat, setEndLat] = useState('');
  const [endLon, setEndLon] = useState('');
  const [startPlace, setStartPlace] = useState('');
  const [endPlace, setEndPlace] = useState('');

  const [route, setRoute] = useState<RouteResult | null>(null);
  const [loading, setLoading] = useState(false);
  const [geocoding, setGeocoding] = useState<'start' | 'end' | null>(null);
  const [error, setError] = useState<string | null>(null);

  const [geoStatus, setGeoStatus] = useState<'idle' | 'locating' | 'ready' | 'unavailable'>('idle');
  const [geoError, setGeoError] = useState<string | null>(null);
  const [deviceLocation, setDeviceLocation] = useState<{ lat: number; lon: number; accuracy: number } | null>(null);

  const [hazardFile, setHazardFile] = useState<File | null>(null);
  const [hazardUploading, setHazardUploading] = useState(false);
  const [hazardSuccess, setHazardSuccess] = useState<HazardResponse | null>(null);
  const [hazardError, setHazardError] = useState<string | null>(null);
  const hazardFileRef = useRef<HTMLInputElement | null>(null);
  const startRef = useRef<LatLon | null>(null);
  const hasDefaultedStart = useRef(false);

  const routeCoords = route?.routeGeojson?.geometry?.coordinates ?? null;

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
    setRoute(null);
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
    const result = await fetchRoute({
      startLat: start.lat,
      startLon: start.lon,
      endLat: end.lat,
      endLon: end.lon,
    });
    setLoading(false);

    if (result.kind === 'ok') {
      setRoute(result.data);
      return;
    }

    if (result.kind === 'no_route') {
      const message = result.message ? `No route found: ${result.message}` : 'No route found.';
      setError(message);
      return;
    }

    setError(result.message);
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

    if (!hazardFile) {
      setHazardError('Please select an image to upload.');
      return;
    }

    if (!ALLOWED_IMAGE_TYPES.has(hazardFile.type)) {
      setHazardError('Image type must be JPEG, PNG, or WEBP.');
      return;
    }

    if (hazardFile.size > MAX_IMAGE_BYTES) {
      setHazardError('Image must be 10MB or smaller.');
      return;
    }

    const metadata: HazardMetadata = {
      lat: deviceLocation.lat,
      lon: deviceLocation.lon,
      type: DEFAULT_HAZARD_TYPE,
      capturedAt: new Date().toISOString(),
    };

    setHazardUploading(true);
    try {
      const response = await submitHazardReport(hazardFile, metadata);
      setHazardSuccess(response);
      setHazardFile(null);
      if (hazardFileRef.current) {
        hazardFileRef.current.value = '';
      }
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
    setRoute(null);
    setError(null);
  };

  const distanceKm = useMemo(() => {
    if (!route) return null;
    return (route.distanceMeters / 1000).toFixed(2);
  }, [route]);

  const durationMinutes = useMemo(() => {
    if (!route) return null;
    return (route.durationSeconds / 60).toFixed(1);
  }, [route]);

  const formatList = (items: number[]) => items.slice(0, 10).join(', ');
  const geocodeStatus =
    geocoding === 'start'
      ? 'Searching for start place...'
      : geocoding === 'end'
      ? 'Searching for end place...'
      : null;
  const routeDisabled = loading || !start || !end;
  const hazardInputsDisabled = geoStatus !== 'ready' || hazardUploading;
  const hazardSubmitDisabled = hazardInputsDisabled || !hazardFile;
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

  return (
    <div className="app">
      <aside className="panel">
        <header>
          <h1>Walking Route</h1>
          <p>Click the map, search for a place, or enter coordinates to plan a route.</p>
        </header>

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
          <h2>Results</h2>
          {!route && <p className="muted">No route yet. Click Route to calculate.</p>}
          {route && (
            <div className="results">
              <div>
                <span>Distance</span>
                <strong>
                  {route.distanceMeters.toFixed(1)} m ({distanceKm} km)
                </strong>
              </div>
              <div>
                <span>Duration</span>
                <strong>
                  {route.durationSeconds.toFixed(1)} s ({durationMinutes} min)
                </strong>
              </div>
              <details>
                <summary>Debug info</summary>
                <div className="debug">
                  <div>
                    <span>Node count</span>
                    <strong>{route.pathNodeIds.length}</strong>
                  </div>
                  <div>
                    <span>Edge count</span>
                    <strong>{route.pathEdgeIds.length}</strong>
                  </div>
                  <div>
                    <span>First 10 node IDs</span>
                    <strong>{formatList(route.pathNodeIds) || '—'}</strong>
                  </div>
                  <div>
                    <span>First 10 edge IDs</span>
                    <strong>{formatList(route.pathEdgeIds) || '—'}</strong>
                  </div>
                </div>
              </details>
            </div>
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
            Image
            <input
              ref={hazardFileRef}
              type="file"
              accept="image/*"
              onChange={handleHazardFileChange}
              disabled={hazardInputsDisabled}
            />
          </label>
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
          routeCoords={routeCoords}
          onMapClick={handleMapClick}
        />
      </main>
    </div>
  );
};

export default App;
