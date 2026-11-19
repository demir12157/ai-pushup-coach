use std::sync::{
    atomic::{AtomicBool, Ordering},
    Arc, Mutex,
};
use std::time::Instant;

use crate::analyzer::{BodyPose, Point2D};

pub struct CameraFrame {
    pub width: usize,
    pub height: usize,
    pub rgba: Vec<u8>,
}

pub struct CameraManager {
    latest_frame: Arc<Mutex<Option<CameraFrame>>>,
    running: Arc<AtomicBool>,
    pub status: String,
    pub simulation_mode: bool,
    sim_start: Instant,
    prev_luma: Vec<u8>,
    smoothed_center_y: f32,
    baseline_top_y: f32,
}

impl Default for CameraManager {
    fn default() -> Self {
        Self {
            latest_frame: Arc::new(Mutex::new(None)),
            running: Arc::new(AtomicBool::new(false)),
            status: "Kamera kapali".into(),
            simulation_mode: false,
            sim_start: Instant::now(),
            prev_luma: Vec::new(),
            smoothed_center_y: 0.45,
            baseline_top_y: 0.45,
        }
    }
}

impl CameraManager {
    pub fn start_camera(&mut self) {
        self.stop();
        self.simulation_mode = false;
        self.running.store(true, Ordering::SeqCst);

        #[cfg(not(target_os = "android"))]
        {
            let frame_slot = Arc::clone(&self.latest_frame);
            let running = Arc::clone(&self.running);
            self.status = "Kamera aciliyor...".into();

            std::thread::spawn(move || {
                use nokhwa::pixel_format::RgbAFormat;
                use nokhwa::utils::{CameraIndex, RequestedFormat, RequestedFormatType};
                use nokhwa::Camera;

                let requested = RequestedFormat::new::<RgbAFormat>(
                    RequestedFormatType::AbsoluteHighestFrameRate,
                );
                let mut cam = match Camera::new(CameraIndex::Index(0), requested) {
                    Ok(c) => c,
                    Err(_) => {
                        running.store(false, Ordering::SeqCst);
                        return;
                    }
                };
                if cam.open_stream().is_err() {
                    running.store(false, Ordering::SeqCst);
                    return;
                }

                while running.load(Ordering::SeqCst) {
                    if let Ok(buf) = cam.frame() {
                        if let Ok(decoded) = buf.decode_image::<RgbAFormat>() {
                            let w = decoded.width() as usize;
                            let h = decoded.height() as usize;
                            let rgba = decoded.into_raw();
                            if let Ok(mut guard) = frame_slot.lock() {
                                *guard = Some(CameraFrame {
                                    width: w,
                                    height: h,
                                    rgba,
                                });
                            }
                        }
                    }
                    std::thread::sleep(std::time::Duration::from_millis(16));
                }
                let _ = cam.stop_stream();
            });
            self.status = "Canli kamera aktif".into();
        }

        #[cfg(target_os = "android")]
        {
            self.simulation_mode = true;
            self.sim_start = Instant::now();
            self.status = "Android simulasyon modu aktif".into();
        }
    }

    pub fn start_simulation(&mut self) {
        self.stop();
        self.simulation_mode = true;
        self.sim_start = Instant::now();
        self.status = "Simulasyon modu aktif".into();
    }

    pub fn stop(&mut self) {
        self.running.store(false, Ordering::SeqCst);
        if let Ok(mut guard) = self.latest_frame.lock() {
            *guard = None;
        }
        self.prev_luma.clear();
    }

    pub fn take_frame(&self) -> Option<CameraFrame> {
        if let Ok(mut guard) = self.latest_frame.lock() {
            return guard.take();
        }
        None
    }

    pub fn is_camera_running(&self) -> bool {
        self.running.load(Ordering::SeqCst)
    }

    /// Generates a synthetic pushup pose for simulation/testing, or estimates body vertical travel
    /// from live camera motion centroid when running pure-Rust without an external model file.
    pub fn estimate_pose(&mut self, frame: Option<&CameraFrame>) -> Option<BodyPose> {
        if self.simulation_mode {
            let t = self.sim_start.elapsed().as_secs_f32();
            let cycle = (t / 2.2).fract(); // 2.2s per pushup
            let depth = (cycle * std::f32::consts::PI).sin().clamp(0.0, 1.0);
            // Simulate occasional hip sag on every 3rd rep so user can see form coaching live
            let rep_idx = (t / 2.2).floor() as u32;
            let sag = if rep_idx % 3 == 2 { 0.11 } else { 0.0 };

            return Some(BodyPose {
                ear: Point2D { x: 0.24, y: 0.41 + depth * 0.14, confidence: 0.99 },
                shoulder: Point2D { x: 0.34, y: 0.44 + depth * 0.16, confidence: 0.99 },
                elbow: Point2D { x: 0.32 - depth * 0.10, y: 0.58 + depth * 0.05, confidence: 0.99 },
                wrist: Point2D { x: 0.35, y: 0.72, confidence: 0.99 },
                hip: Point2D { x: 0.58, y: 0.53 + depth * 0.09 + sag, confidence: 0.99 },
                knee: Point2D { x: 0.74, y: 0.62 + depth * 0.04, confidence: 0.99 },
                ankle: Point2D { x: 0.88, y: 0.70, confidence: 0.99 },
            });
        }

        let frame = frame?;
        // Pure-Rust lightweight optical-flow centroid tracker (downsampled 64x48)
        const SW: usize = 64;
        const SH: usize = 48;
        let mut luma = vec![0u8; SW * SH];
        for sy in 0..SH {
            let fy = sy * frame.height / SH;
            for sx in 0..SW {
                let fx = sx * frame.width / SW;
                let idx = (fy * frame.width + fx) * 4;
                if idx + 2 < frame.rgba.len() {
                    let r = frame.rgba[idx] as u32;
                    let g = frame.rgba[idx + 1] as u32;
                    let b = frame.rgba[idx + 2] as u32;
                    luma[sy * SW + sx] = ((r * 30 + g * 59 + b * 11) / 100) as u8;
                }
            }
        }

        if self.prev_luma.len() == luma.len() {
            let mut sum_y = 0.0f32;
            let mut weight = 0.0f32;
            for sy in 0..SH {
                for sx in 0..SW {
                    let i = sy * SW + sx;
                    let diff = (luma[i] as i32 - self.prev_luma[i] as i32).abs();
                    if diff > 18 {
                        let w = diff as f32;
                        sum_y += (sy as f32 / SH as f32) * w;
                        weight += w;
                    }
                }
            }
            if weight > 450.0 {
                let cy = sum_y / weight;
                self.smoothed_center_y = self.smoothed_center_y * 0.75 + cy * 0.25;
                if self.smoothed_center_y < self.baseline_top_y {
                    self.baseline_top_y = self.baseline_top_y * 0.92 + self.smoothed_center_y * 0.08;
                }
            }
        }
        self.prev_luma = luma;

        let depth = ((self.smoothed_center_y - self.baseline_top_y) / 0.22).clamp(0.0, 1.0);
        Some(BodyPose {
            ear: Point2D { x: 0.24, y: 0.41 + depth * 0.14, confidence: 0.9 },
            shoulder: Point2D { x: 0.34, y: 0.44 + depth * 0.16, confidence: 0.9 },
            elbow: Point2D { x: 0.32 - depth * 0.10, y: 0.58 + depth * 0.05, confidence: 0.9 },
            wrist: Point2D { x: 0.35, y: 0.72, confidence: 0.9 },
            hip: Point2D { x: 0.58, y: 0.53 + depth * 0.09, confidence: 0.9 },
            knee: Point2D { x: 0.74, y: 0.62 + depth * 0.04, confidence: 0.9 },
            ankle: Point2D { x: 0.88, y: 0.70, confidence: 0.9 },
        })
    }
}
