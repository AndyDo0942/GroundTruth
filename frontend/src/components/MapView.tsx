import { useEffect, useMemo, useRef } from 'react';
import * as L from 'leaflet';
import { CircleMarker, MapContainer, Polyline, TileLayer, useMap, useMapEvents } from 'react-leaflet';
import { LatLon } from '../types';

type MapViewProps = {
  start: LatLon | null;
  end: LatLon | null;
  deviceLocation?: LatLon | null;
  deviceZoom?: number;
  routeCoords: [number, number][] | null;
  onMapClick: (lat: number, lon: number) => void;
};

const defaultCenter: [number, number] = [39.5, -98.35];
const defaultZoom = 4;

const MapClickHandler = ({ onMapClick }: { onMapClick: (lat: number, lon: number) => void }) => {
  useMapEvents({
    click: (event: { latlng: { lat: number; lng: number; }; }) => {
      onMapClick(event.latlng.lat, event.latlng.lng);
    },
  });
  return null;
};

const RouteLine = ({ routeCoords }: { routeCoords: [number, number][] | null }) => {
  const map = useMap();
  const latLngs = useMemo(
    () => routeCoords?.map(([lon, lat]) => [lat, lon] as [number, number]),
    [routeCoords]
  );

  useEffect(() => {
    if (latLngs && latLngs.length > 1) {
      map.fitBounds(latLngs, { padding: [32, 32] });
    }
  }, [latLngs, map]);

  if (!latLngs || latLngs.length === 0) {
    return null;
  }

  // Leaflet expects [lat, lon] points, so convert from GeoJSON [lon, lat].
  return <Polyline positions={latLngs} pathOptions={{ color: '#1f6feb', weight: 4 }} />;
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

const MapView = ({ start, end, deviceLocation, deviceZoom, routeCoords, onMapClick }: MapViewProps) => {
  return (
    <MapContainer className="map" center={defaultCenter} zoom={defaultZoom} scrollWheelZoom>
      <TileLayer
        attribution='&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> contributors'
        url="https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png"
      />
      <MapClickHandler onMapClick={onMapClick} />
      <DeviceCenter deviceLocation={deviceLocation} deviceZoom={deviceZoom} />
      <RecenterControl deviceLocation={deviceLocation} deviceZoom={deviceZoom} />
      {deviceLocation && (
        <CircleMarker
          center={[deviceLocation.lat, deviceLocation.lon]}
          radius={6}
          pathOptions={{ color: '#0f766e', fillColor: '#14b8a6', fillOpacity: 0.6 }}
        />
      )}
      {start && (
        <CircleMarker
          center={[start.lat, start.lon]}
          radius={8}
          pathOptions={{ color: '#1f883d', fillColor: '#1f883d', fillOpacity: 0.9 }}
        />
      )}
      {end && (
        <CircleMarker
          center={[end.lat, end.lon]}
          radius={8}
          pathOptions={{ color: '#cf222e', fillColor: '#cf222e', fillOpacity: 0.9 }}
        />
      )}
      <RouteLine routeCoords={routeCoords} />
    </MapContainer>
  );
};

export default MapView;
