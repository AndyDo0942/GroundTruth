import os
import cv2
import numpy as np
import torch
from fastapi import FastAPI, UploadFile, File, HTTPException
from PIL import Image
import io

# Import your custom classes
from segmentation import PotholeDetector 
from depth import DepthEstimator

app = FastAPI(title="Pothole Analysis API")

# --- GLOBAL MODELS ---
# Initialize once so they stay in VRAM/RAM
detector = PotholeDetector()
depth_tool = DepthEstimator()

# Camera Constant
HORIZONTAL_FOV = 65 

@app.post("/analyze-potholes")
async def analyze_potholes(file: UploadFile = File(...)):
    # 1. Validate file type
    if not file.content_type.startswith("image/"):
        raise HTTPException(status_code=400, detail="File must be an image")

    # 2. Read image into memory
    contents = await file.read()
    # Convert to OpenCV format
    nparr = np.frombuffer(contents, np.uint8)
    img = cv2.imdecode(nparr, cv2.IMREAD_COLOR)
    
    if img is None:
        raise HTTPException(status_code=400, detail="Invalid image data")

    # 3. Run Inference
    # Note: Ensure your classes can accept a numpy array or a temporary file path
    # If your classes require a path, we save briefly:
    temp_path = "temp_inference.jpg"
    cv2.imwrite(temp_path, img)

    try:
        segment_result = detector.detect(temp_path)
        depth_map = depth_tool.get_depth(temp_path)
        h_depth, w_depth = depth_map.shape

        focal_length_px = (w_depth / 2) / np.tan(np.radians(HORIZONTAL_FOV / 2))
        analysis_output = []

        if segment_result.masks is not None:
            masks = segment_result.masks.data.cpu().numpy()
            
            for i, mask in enumerate(masks):
                mask_aligned = cv2.resize(mask, (w_depth, h_depth), interpolation=cv2.INTER_NEAREST).astype(np.uint8)
                
                # Create Road Ring
                kernel = np.ones((15, 15), np.uint8)
                dilated_mask = cv2.dilate(mask_aligned, kernel, iterations=2)
                road_ring_mask = dilated_mask - mask_aligned 
                
                pothole_pixels = depth_map[mask_aligned > 0]
                road_pixels = depth_map[road_ring_mask > 0]
                
                if pothole_pixels.size > 0 and road_pixels.size > 0:
                    road_surface_m = np.median(road_pixels)
                    pothole_floor_m = np.median(pothole_pixels) 
                    
                    true_depth_cm = max(0, (pothole_floor_m - road_surface_m) * 100)

                    # Width Calculation
                    coords = np.argwhere(mask_aligned > 0)
                    x_min, x_max = coords[:, 1].min(), coords[:, 1].max()
                    pixel_width = x_max - x_min
                    true_width_cm = (pixel_width * road_surface_m) / focal_length_px * 100

                    analysis_output.append({
                        "pothole_id": i,
                        "distance_m": round(float(road_surface_m), 2),
                        "depth_cm": round(float(true_depth_cm), 2),
                        "width_cm": round(float(true_width_cm), 2)
                    })

        return {
            "pothole_count": len(analysis_output),
            "results": analysis_output
        }

    finally:
        if os.path.exists(temp_path):
            os.remove(temp_path)