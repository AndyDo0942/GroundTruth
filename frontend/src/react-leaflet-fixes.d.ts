import * as L from 'leaflet';

declare module 'react-leaflet' {
  interface CircleMarkerProps {
    center: L.LatLngExpression;
    radius?: number;
  }

  interface MapContainerProps {
    center: L.LatLngExpression;
    zoom?: number;
    scrollWheelZoom?: boolean;
  }

  interface TileLayerProps {
    attribution?: string;
  }
}
