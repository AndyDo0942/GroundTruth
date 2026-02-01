export type LatLon = {
  lat: number;
  lon: number;
};

export type TravelMode = 'WALK' | 'DRIVE';

export type RouteGeojson = {
  type: 'Feature';
  properties: Record<string, unknown>;
  geometry: {
    type: 'LineString';
    coordinates: [number, number][];
  };
};

export type RouteResult = {
  distanceMeters: number;
  durationSeconds: number;
  pathNodeIds: number[];
  pathEdgeIds: number[];
  routeGeojson: RouteGeojson;
};

export type HazardType = 'POTHOLE' | 'BLOCKED' | 'CONSTRUCTION' | 'OTHER';

export type HazardReportType = 'cracks' | 'blocked sidewalk';

export type HazardMetadata = {
  lat: number;
  lon: number;
  type: HazardType;
  description?: string;
  capturedAt?: string;
};

export type HazardResponse = {
  id: string;
  status: string;
  lat: number;
  lon: number;
  type: HazardType;
  createdAt: string;
};

export type HazardMarker = {
  id: string | number;
  lat: number;
  lon: number;
  label?: string;
  confidence?: number;
  iconType?: string;
  severity?: number;
  type?: string;
};

export type RiskMarker = {
  lat: number;
  lon: number;
  riskType?: string;
  value?: number;
  severity?: number;
  iconType?: string;
};

export type RouteMarkers = {
  hazardMarkers: HazardMarker[];
  riskMarkers: RiskMarker[];
};
