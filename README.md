# SafeStep

A comprehensive walking route safety and accessibility platform that uses machine learning to detect and analyze road hazards, providing intelligent routing based on safety and accessibility needs.

## Overview

GroundTruth combines computer vision, geospatial analysis, and intelligent routing to help users navigate urban environments safely. The platform detects hazards like potholes using YOLOv8, calculates their severity through depth estimation, and provides optimized walking routes that prioritize safety and accessibility.

## Features

- **AI-Powered Hazard Detection**: Real-time pothole detection and dimensional analysis using YOLOv8 segmentation
- **Intelligent Routing**: A* algorithm-based pathfinding with customizable weights for safety and accessibility
- **Interactive Map Interface**: Real-time visualization of routes and hazards using Leaflet
- **Hazard Reporting**: Community-driven hazard reporting and verification system
- **Accessibility Routing**: Specialized route calculations for wheelchair users and those with mobility challenges
- **User Authentication**: Secure OAuth2-based authentication system
- **Geospatial Analysis**: PostgreSQL with PostGIS for efficient spatial queries

## Tech Stack

### Backend
- **Framework**: Spring Boot 4.0.2 (Java 25)
- **Security**: Spring Security with OAuth2
- **Database**: PostgreSQL with Hibernate Spatial (PostGIS)
- **AI Integration**: Spring AI with OpenAI
- **Geospatial**: JTS Topology Suite, Hibernate Spatial

### Frontend
- **Framework**: React 18 with TypeScript
- **Build Tool**: Vite 5
- **Mapping**: Leaflet + React Leaflet
- **Styling**: Modern responsive design

### Inference Service
- **Framework**: FastAPI (Python)
- **ML Models**:
  - YOLOv8m for pothole segmentation
  - Depth estimation for dimensional analysis
- **Computer Vision**: OpenCV, PyTorch

## Architecture

```
┌─────────────────┐
│  React Frontend │
│   (TypeScript)  │
└────────┬────────┘
         │
         │ REST API
         ▼
┌─────────────────┐      ┌──────────────────┐
│  Spring Boot    │      │  FastAPI ML      │
│    Backend      │─────▶│  Inference API   │
│  (Java 25)      │      │   (Python)       │
└────────┬────────┘      └──────────────────┘
         │
         ▼
┌─────────────────┐
│   PostgreSQL    │
│  with PostGIS   │
└─────────────────┘
```

## Getting Started

### Prerequisites

- Java 25
- Node.js 18+
- Python 3.8+
- PostgreSQL with PostGIS extension
- Docker (optional, for containerized deployment)

### Backend Setup

1. Navigate to the backend directory:
```bash
cd backend
```

2. Configure your `application.properties` with database credentials and OAuth2 settings

3. Run the Spring Boot application:
```bash
./gradlew bootRun
```

The backend will start on `http://localhost:8080`

### Frontend Setup

1. Navigate to the frontend directory:
```bash
cd frontend
```

2. Install dependencies:
```bash
npm install
```

3. Start the development server:
```bash
npm run dev
```

The frontend will start on `http://localhost:5173`

### Inference Service Setup

1. Navigate to the inference directory:
```bash
cd inference
```

2. Install Python dependencies:
```bash
pip install -r requirements.txt
```

3. Download the YOLOv8 model (if not already present):
```bash
# The model will be automatically downloaded on first run
```

4. Start the FastAPI server:
```bash
uvicorn inference:app --reload
```

The inference API will start on `http://localhost:8000`

## API Endpoints

### Backend Endpoints

- `POST /api/routing/route` - Calculate optimal walking route
- `POST /api/walksafe/route` - Calculate safety-optimized route
- `POST /api/accessibility/route` - Calculate accessibility-optimized route
- `POST /api/hazards/report` - Report a new hazard
- `GET /api/hazards` - Get hazards in area
- `POST /api/auth/login` - User authentication

### Inference API Endpoints

- `POST /analyze-potholes` - Analyze uploaded image for potholes
  - Returns: pothole count, dimensions (width, depth), and distance from camera

## How It Works

### Pothole Detection Pipeline

1. **Image Upload**: User submits an image through the API
2. **Segmentation**: YOLOv8m model identifies pothole regions with instance segmentation
3. **Depth Analysis**: Depth estimation model creates a depth map of the scene
4. **Dimensional Calculation**:
   - Road surface depth is calculated from surrounding pixels
   - Pothole depth is measured relative to road surface
   - Width is calculated using camera focal length and distance
5. **Results**: Returns structured data with pothole locations, dimensions, and severity

### Routing Algorithm

- Uses A* pathfinding for optimal route calculation
- Customizable edge weights based on:
  - Road surface quality
  - Reported hazards
  - Accessibility features (curb cuts, ramps, etc.)
  - Historical safety data

## Development

### Building for Production

**Frontend:**
```bash
cd frontend
npm run build
```

**Backend:**
```bash
cd backend
./gradlew build
```

### Testing

**Backend:**
```bash
cd backend
./gradlew test
```

## Project Structure

```
GroundTruth/
├── backend/              # Spring Boot application
│   ├── src/
│   │   ├── main/
│   │   │   └── java/com/team/GroundTruth/
│   │   │       ├── Controller/      # REST endpoints
│   │   │       ├── services/        # Business logic
│   │   │       ├── routing/         # A* routing engine
│   │   │       └── models/          # JPA entities
│   │   └── test/
│   └── build.gradle
├── frontend/             # React application
│   ├── src/
│   ├── package.json
│   └── vite.config.ts
└── inference/            # ML inference service
    ├── inference.py      # FastAPI endpoints
    ├── segmentation.py   # YOLOv8 detection
    ├── depth.py          # Depth estimation
    └── input/            # Sample images
```

## Contributing

Contributions are welcome! Please follow these steps:

1. Fork the repository
2. Create a feature branch (`git checkout -b feature/amazing-feature`)
3. Commit your changes (`git commit -m 'Add amazing feature'`)
4. Push to the branch (`git push origin feature/amazing-feature`)
5. Open a Pull Request

## License

This project is licensed under the MIT License - see the LICENSE file for details.

## Acknowledgments

- YOLOv8 model by Ultralytics
- OpenStreetMap for mapping data
- Spring Boot and React communities
- All contributors and testers

## Contact

For questions or support, please open an issue on GitHub.
