pub mod analyzer;
pub mod app;
pub mod camera;
pub mod storage;
pub mod variations;

#[cfg(target_os = "android")]
#[no_mangle]
fn android_main(app: android_activity::AndroidApp) {
    let options = eframe::NativeOptions {
        android_app: Some(app),
        ..Default::default()
    };
    let _ = eframe::run_native(
        "SinavKoc",
        options,
        Box::new(|cc| Ok(Box::new(app::SinavKocApp::new(cc)))),
    );
}
