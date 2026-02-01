export type LatLon = {
  lat: number;
  lon: number;
};

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
