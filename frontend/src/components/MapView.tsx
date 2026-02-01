import { useEffect, useMemo, useRef } from 'react';
import * as L from 'leaflet';
import { CircleMarker, MapContainer, Marker, Polyline, TileLayer, useMap, useMapEvents } from 'react-leaflet';
import { HazardMarker, LatLon } from '../types';

type MapViewProps = {
  start: LatLon | null;
  end: LatLon | null;
  deviceLocation?: LatLon | null;
  deviceZoom?: number;
  routes: Array<{
    id: string;
    color: string;
    coordinates: [number, number][];
  }>;
  selectedRouteId: string | null;
  onSelectRoute?: (routeId: string) => void;
  hazardMarkers: HazardMarker[];
  hazardLocation?: LatLon | null;
  hazardPickMode?: boolean;
  onHazardPick?: (lat: number, lon: number) => void;
  onBoundsChange?: (bounds: { minLat: number; maxLat: number; minLon: number; maxLon: number }) => void;
  onMapClick: (lat: number, lon: number) => void;
};

const defaultCenter: [number, number] = [39.5, -98.35];
const defaultZoom = 4;

const MapEventHandler = ({
  onMapClick,
  hazardPickMode,
  onHazardPick,
  onBoundsChange,
}: {
  onMapClick: (lat: number, lon: number) => void;
  hazardPickMode?: boolean;
  onHazardPick?: (lat: number, lon: number) => void;
  onBoundsChange?: (bounds: { minLat: number; maxLat: number; minLon: number; maxLon: number }) => void;
}) => {
  const map = useMapEvents({
    click: (event: { latlng: { lat: number; lng: number; }; }) => {
      const lat = event.latlng.lat;
      const lon = event.latlng.lng;
      if (hazardPickMode && onHazardPick) {
        onHazardPick(lat, lon);
        return;
      }
      onMapClick(lat, lon);
    },
    moveend: () => {
      if (!onBoundsChange) return;
      const bounds = map.getBounds();
      onBoundsChange({
        minLat: bounds.getSouth(),
        maxLat: bounds.getNorth(),
        minLon: bounds.getWest(),
        maxLon: bounds.getEast(),
      });
    },
  });
  return null;
};

const SelectedRouteBounds = ({
  routes,
  selectedRouteId,
}: {
  routes: MapViewProps['routes'];
  selectedRouteId: string | null;
}) => {
  const map = useMap();
  const selected = useMemo(() => {
    if (!routes.length) return null;
    return routes.find((route) => route.id === selectedRouteId) ?? routes[0];
  }, [routes, selectedRouteId]);
  const latLngs = useMemo(
    () => selected?.coordinates.map(([lon, lat]) => [lat, lon] as [number, number]),
    [selected]
  );

  useEffect(() => {
    if (latLngs && latLngs.length > 1) {
      map.fitBounds(latLngs, { padding: [32, 32] });
    }
  }, [latLngs, map]);

  return null;
};

const DeviceCenter = ({
  deviceLocation,
  deviceZoom = 17,
}: {
  deviceLocation?: LatLon | null;
  deviceZoom?: number;
}) => {
  const map = useMap();
  const hasCentered = useRef(false);

  useEffect(() => {
    if (!deviceLocation || hasCentered.current) return;
    map.setView([deviceLocation.lat, deviceLocation.lon], deviceZoom);
    hasCentered.current = true;
  }, [deviceLocation, deviceZoom, map]);

  return null;
};

const InitialBounds = ({ onBoundsChange }: { onBoundsChange?: MapViewProps['onBoundsChange'] }) => {
  const map = useMap();
  useEffect(() => {
    if (!onBoundsChange) return;
    const bounds = map.getBounds();
    onBoundsChange({
      minLat: bounds.getSouth(),
      maxLat: bounds.getNorth(),
      minLon: bounds.getWest(),
      maxLon: bounds.getEast(),
    });
  }, [map, onBoundsChange]);
  return null;
};

const RecenterControl = ({
  deviceLocation,
  deviceZoom = 17,
}: {
  deviceLocation?: LatLon | null;
  deviceZoom?: number;
}) => {
  const map = useMap();

  useEffect(() => {
    const control = L.control({ position: 'topleft' });
    control.onAdd = () => {
      const container = L.DomUtil.create('div', 'leaflet-bar leaflet-control leaflet-control-recenter');
      const button = L.DomUtil.create('button', 'leaflet-control-recenter-button', container) as HTMLButtonElement;
      button.type = 'button';
      button.title = 'Recenter map';
      button.setAttribute('aria-label', 'Recenter map');
      button.innerHTML =
        '<svg viewBox="0 0 24 24" aria-hidden="true" focusable="false"><circle cx="12" cy="12" r="9" fill="none" stroke="currentColor" stroke-width="2"></circle><path d="M15.5 8.5L9 15l1.5-5.5L17 7z" fill="currentColor"></path></svg>';

      const setButtonState = () => {
        const isDisabled = !deviceLocation;
        button.disabled = isDisabled;
        button.classList.toggle('is-disabled', isDisabled);
      };

      setButtonState();
      L.DomEvent.disableClickPropagation(container);
      L.DomEvent.disableScrollPropagation(container);

      L.DomEvent.on(button, 'click', (event: any) => {
        L.DomEvent.stopPropagation(event);
        L.DomEvent.preventDefault(event);
        if (!deviceLocation) return;
        map.setView([deviceLocation.lat, deviceLocation.lon], deviceZoom);
      });

      return container;
    };

    control.addTo(map);

    return () => {
      control.remove();
    };
  }, [map, deviceLocation, deviceZoom]);

  return null;
};

const buildDangerIcon = (color: string) => {
  const svg = `
    <svg xmlns="http://www.w3.org/2000/svg" width="32" height="32" viewBox="0 0 24 24">
      <path d="M12 3L2.6 20.5c-.4.8.1 1.5 1 1.5h16.8c.9 0 1.4-.7 1-1.5L12 3z" fill="${color}" stroke="rgba(15,23,42,0.6)" stroke-width="1"/>
      <rect x="11" y="8" width="2" height="7" fill="#111827"/>
      <circle cx="12" cy="18" r="1.2" fill="#111827"/>
    </svg>
  `;
  return L.icon({
    iconUrl: `data:image/svg+xml;utf8,${encodeURIComponent(svg)}`,
    iconSize: [28, 28],
    iconAnchor: [14, 26],
    popupAnchor: [0, -24],
  });
};

const buildPinIcon = (color: string) => {
  const svg = `
    <svg xmlns="http://www.w3.org/2000/svg" width="36" height="48" viewBox="0 0 36 48">
      <path d="M18 2C9.7 2 3 8.7 3 17c0 9.2 12.3 25.8 14.5 28.6.3.4.9.4 1.2 0C20.7 42.8 33 26.2 33 17 33 8.7 26.3 2 18 2z" fill="${color}" />
      <circle cx="18" cy="17" r="6.5" fill="rgba(255,255,255,0.9)" />
    </svg>
  `;
  return L.icon({
    iconUrl: `data:image/svg+xml;utf8,${encodeURIComponent(svg)}`,
    iconSize: [28, 36],
    iconAnchor: [14, 34],
    className: 'map-pin-icon',
  });
};

const isValidCoord = (lat?: number, lon?: number) =>
  Number.isFinite(lat) && Number.isFinite(lon) && Math.abs(lat!) <= 90 && Math.abs(lon!) <= 180;

const MapView = ({
  start,
  end,
  deviceLocation,
  deviceZoom,
  routes,
  selectedRouteId,
  onSelectRoute,
  hazardMarkers,
  hazardLocation,
  hazardPickMode,
  onHazardPick,
  onBoundsChange,
  onMapClick,
}: MapViewProps) => {
  const routeLines = useMemo(
    () =>
      routes.map((route) => ({
        ...route,
        // GeoJSON is [lon, lat]; Leaflet expects [lat, lon].
        latLngs: route.coordinates.map(([lon, lat]) => [lat, lon] as [number, number]),
      })),
    [routes]
  );

  const hazardIcon = useMemo(() => buildDangerIcon('#facc15'), []);
  const startIcon = useMemo(() => buildPinIcon('#16a34a'), []);
  const endIcon = useMemo(() => buildPinIcon('#ef4444'), []);
  const hazardPinIcon = useMemo(() => buildPinIcon('#f59e0b'), []);
  const safeHazardMarkers = useMemo(
    () => hazardMarkers.filter((marker) => isValidCoord(marker.lat, marker.lon)),
    [hazardMarkers]
  );

  return (
    <MapContainer className="map" center={defaultCenter} zoom={defaultZoom} scrollWheelZoom>
      <TileLayer
        attribution='&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> contributors'
        url="https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png"
      />
      <MapEventHandler
        onMapClick={onMapClick}
        hazardPickMode={hazardPickMode}
        onHazardPick={onHazardPick}
        onBoundsChange={onBoundsChange}
      />
      <DeviceCenter deviceLocation={deviceLocation} deviceZoom={deviceZoom} />
      <RecenterControl deviceLocation={deviceLocation} deviceZoom={deviceZoom} />
      <InitialBounds onBoundsChange={onBoundsChange} />
      <SelectedRouteBounds routes={routes} selectedRouteId={selectedRouteId} />
      {deviceLocation && (
        <CircleMarker
          center={[deviceLocation.lat, deviceLocation.lon]}
          radius={6}
          pathOptions={{ color: '#0f766e', fillColor: '#14b8a6', fillOpacity: 0.6 }}
        />
      )}
      {start && isValidCoord(start.lat, start.lon) && <Marker position={[start.lat, start.lon]} icon={startIcon} />}
      {end && isValidCoord(end.lat, end.lon) && <Marker position={[end.lat, end.lon]} icon={endIcon} />}
      {hazardLocation && isValidCoord(hazardLocation.lat, hazardLocation.lon) && (
        <Marker position={[hazardLocation.lat, hazardLocation.lon]} icon={hazardPinIcon} />
      )}
      {safeHazardMarkers.map((marker, index) => (
        <Marker
          key={`hazard-${marker.id ?? index}`}
          position={[marker.lat, marker.lon]}
          icon={hazardIcon}
          title={marker.label ?? 'Hazard'}
        />
      ))}
      {routeLines.map((route) => {
        if (route.latLngs.length === 0) {
          return null;
        }
        const isSelected = route.id === selectedRouteId;
        const pathOptions = {
          color: route.color,
          weight: isSelected ? 5 : 3,
          opacity: isSelected ? 0.95 : 0.35,
          dashArray: isSelected ? undefined : '6 10',
        };

        return (
          <Polyline
            key={route.id}
            positions={route.latLngs}
            pathOptions={pathOptions}
            eventHandlers={
              onSelectRoute
                ? {
                    click: (event) => {
                      event.originalEvent?.stopPropagation();
                      onSelectRoute(route.id);
                    },
                  }
                : undefined
            }
          />
        );
      })}
    </MapContainer>
  );
};

export default MapView;
