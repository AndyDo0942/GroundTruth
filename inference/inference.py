import os
import cv2
import numpy as np
import torch
from segmentation import PotholeDetector 
from depth import DepthEstimator    

def run_pothole_analysis(image_path):
    # Pro-tip: Initialize these once elsewhere if processing multiple images
    detector = PotholeDetector()
    depth_tool = DepthEstimator()

    print(f"--- Processing: {os.path.basename(image_path)} ---")
    
    segment_result = detector.detect(image_path)
    depth_map = depth_tool.get_depth(image_path)
    h_depth, w_depth = depth_map.shape

    # --- CAMERA SPECS (Assuming standard smartphone 65° H-FOV) ---
    HORIZONTAL_FOV = 65 
    # Calculate focal length in pixels: f = (w / 2) / tan(FOV / 2)
    focal_length_px = (w_depth / 2) / np.tan(np.radians(HORIZONTAL_FOV / 2))

    analysis_output = []

    if segment_result.masks is not None:
        masks = segment_result.masks.data.cpu().numpy()
        
        for i, mask in enumerate(masks):
            # 1. Align Mask
            mask_aligned = cv2.resize(mask, (w_depth, h_depth), interpolation=cv2.INTER_NEAREST).astype(np.uint8)
            
            # 2. CREATE THE "ROAD RING" (The Donut)
            kernel = np.ones((15, 15), np.uint8)
            dilated_mask = cv2.dilate(mask_aligned, kernel, iterations=2)
            road_ring_mask = dilated_mask - mask_aligned 
            
            # 3. EXTRACT PIXELS
            pothole_pixels = depth_map[mask_aligned > 0]
            road_pixels = depth_map[road_ring_mask > 0]
            
            if pothole_pixels.size > 0 and road_pixels.size > 0:
                # 4. CALCULATE DEPTH (Using Median for stability)
                road_surface_m = np.median(road_pixels)
                pothole_floor_m = np.median(pothole_pixels) 
                
                true_depth_cm = (pothole_floor_m - road_surface_m) * 100
                true_depth_cm = max(0, true_depth_cm)

                # 5. CALCULATE WIDTH (Using Back-Projection)
                # Find the horizontal pixel span of the mask
                coords = np.argwhere(mask_aligned > 0)
                x_min, x_max = coords[:, 1].min(), coords[:, 1].max()
                pixel_width = x_max - x_min
                
                # The Formula: TrueWidth = (PixelWidth * Distance) / FocalLength
                true_width_m = (pixel_width * road_surface_m) / focal_length_px
                true_width_cm = true_width_m * 100

                stats = {
                    "pothole_id": i,
                    "distance_to_road_m": round(float(road_surface_m), 2),
                    "estimated_depth_cm": round(float(true_depth_cm), 2),
                    "estimated_width_cm": round(float(true_width_cm), 2),
                    "pixel_area": int(np.sum(mask_aligned > 0))
                }
                analysis_output.append(stats)
                print(f"  > Pothole {i}: Dist={stats['distance_to_road_m']}m, "
                      f"Depth={stats['estimated_depth_cm']}cm, "
                      f"Width={stats['estimated_width_cm']}cm")
    
    return analysis_output

if __name__ == "__main__":
    BASE_DIR = os.path.dirname(__file__)
    # Ensure this path is correct for your setup
    TEST_IMG = os.path.join(BASE_DIR, "input", "pothole5.jpg")
    
    if os.path.exists(TEST_IMG):
        results = run_pothole_analysis(TEST_IMG)
        print("\n--- Final Results ---")
        for res in results:
            print(res)
    else:
        print(f"Error: Could not find image at {TEST_IMG}")