import torch
import os
from ultralyticsplus import YOLO, render_result

# Global patch for torch.load to handle weight serialization issues
original_load = torch.load
def patched_load(f, map_location=None, pickle_module=None, **kwargs):
    kwargs['weights_only'] = False
    return original_load(f, map_location, pickle_module, **kwargs)
torch.load = patched_load

class PotholeDetector:
    def __init__(self, model_id='keremberke/yolov8m-pothole-segmentation'):
        """
        Initializes the YOLO model. 
        In an API context, this should only be called once.
        """
        self.model = YOLO(model_id)
        self.model.overrides['conf'] = 0.2
        self.model.overrides['iou'] = 0.45
        self.model.overrides['max_det'] = 1000
        self.model.overrides['imgsz'] = 1280

    def detect(self, image_path):
        """
        Runs inference on a single image.
        Returns the Ultralytics results object.
        """
        if not os.path.exists(image_path):
            raise FileNotFoundError(f"Image not found: {image_path}")
            
        results = self.model.predict(image_path)
        return results[0]

    def visualize(self, image_path, result, show=True):
        """
        Renders the segmentation masks on the image.
        """
        render = render_result(model=self.model, image=image_path, result=result)
        if show:
            render.show()
        return render
