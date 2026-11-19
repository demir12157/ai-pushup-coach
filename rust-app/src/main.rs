use sinav_koc_rs::app::SinavKocApp;

fn main() -> eframe::Result<()> {
    let options = eframe::NativeOptions {
        viewport: egui::ViewportBuilder::default()
            .with_inner_size([430.0, 820.0])
            .with_min_inner_size([360.0, 640.0])
            .with_title("SinavKoc — Pure Rust AI Pushup Coach"),
        ..Default::default()
    };

    eframe::run_native(
        "SinavKoc",
        options,
        Box::new(|cc| Ok(Box::new(SinavKocApp::new(cc)))),
    )
}
