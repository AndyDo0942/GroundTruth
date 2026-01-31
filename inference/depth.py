import torch
from PIL import Image
import numpy as np
from transformers import pipeline

class DepthEstimator:
    def __init__(self):
        device = 0 if torch.cuda.is_available() else -1
        self.pipe = pipeline(
            task="depth-estimation", 
            model="depth-anything/Depth-Anything-V2-Large-hf", 
            device=device
        )

    def get_depth(self, image_path):
        raw_img = Image.open(image_path)
        # Depth Anything returns a PIL Image in its 'depth' key
        output = self.pipe(raw_img)["depth"]
        return np.array(output)