use std::time::Instant;

use chrono::Local;
use egui::{
    Align, Color32, CornerRadius, FontId, Frame, Layout, Pos2, Rect, RichText, Stroke,
    TextureHandle, TextureOptions, Vec2,
};

use crate::analyzer::{BodyPose, FormSeverity, LiveState, PushupAnalyzer};
use crate::camera::CameraManager;
use crate::storage::{
    calculate_streak, last_7_days, load_data, save_data, today_reps, top_form_issues, AppData,
    WorkoutSession,
};
use crate::variations::{get_variation, VariationId, VARIATIONS};

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
enum Tab {
    Workout,
    Progress,
    Coach,
}

const BG_DARK: Color32 = Color32::from_rgb(9, 13, 22);
const CARD_BG: Color32 = Color32::from_rgb(17, 24, 39);
const CARD_BORDER: Color32 = Color32::from_rgb(30, 41, 59);
const EMERALD: Color32 = Color32::from_rgb(16, 185, 129);
const AMBER: Color32 = Color32::from_rgb(245, 158, 11);
const RED: Color32 = Color32::from_rgb(239, 68, 68);
const SKY: Color32 = Color32::from_rgb(56, 189, 248);
const TEXT_MUTED: Color32 = Color32::from_rgb(148, 163, 184);

pub struct SinavKocApp {
    tab: Tab,
    selected_variation: VariationId,
    target_reps: Option<u32>,
    show_skeleton: bool,

    workout_active: bool,
    workout_start: Instant,
    analyzer: PushupAnalyzer,
    live_state: LiveState,
    camera: CameraManager,
    camera_texture: Option<TextureHandle>,

    data: AppData,
    summary_session: Option<WorkoutSession>,
}

impl SinavKocApp {
    pub fn new(cc: &eframe::CreationContext<'_>) -> Self {
        let mut visuals = egui::Visuals::dark();
        visuals.panel_fill = BG_DARK;
        visuals.window_fill = CARD_BG;
        visuals.widgets.noninteractive.bg_fill = CARD_BG;
        cc.egui_ctx.set_visuals(visuals);

        let analyzer = PushupAnalyzer::new(VariationId::Standard);
        let live_state = LiveState {
            phase: crate::analyzer::Phase::Idle,
            reps: 0,
            elbow_angle: None,
            body_angle: None,
            depth_percent: 0.0,
            severity: FormSeverity::Warning,
            feedback: "Antrenmana hazir".into(),
            current_score: 100,
            pose: None,
        };

        Self {
            tab: Tab::Workout,
            selected_variation: VariationId::Standard,
            target_reps: Some(20),
            show_skeleton: true,
            workout_active: false,
            workout_start: Instant::now(),
            analyzer,
            live_state,
            camera: CameraManager::default(),
            camera_texture: None,
            data: load_data(),
            summary_session: None,
        }
    }

    fn start_workout(&mut self, simulation: bool) {
        self.analyzer.reset(self.selected_variation);
        self.workout_start = Instant::now();
        self.workout_active = true;
        if simulation {
            self.camera.start_simulation();
        } else {
            self.camera.start_camera();
        }
    }

    fn finish_workout(&mut self) {
        self.camera.stop();
        self.workout_active = false;
        let reps = self.analyzer.reps();
        if reps > 0 {
            let v = get_variation(self.selected_variation);
            let duration_sec = self.workout_start.elapsed().as_secs().max(1);
            let session = WorkoutSession {
                id: format!("{}", Local::now().timestamp_millis()),
                timestamp: Local::now(),
                variation: self.selected_variation,
                reps,
                target_reps: self.target_reps,
                duration_sec,
                avg_form_score: self.analyzer.avg_score(),
                calories: ((reps as f32 * v.kcal_per_rep) * 10.0).round() / 10.0,
                issue_counts: self.analyzer.issue_counts().clone(),
                rep_records: self.analyzer.rep_records().to_vec(),
            };
            self.data.sessions.insert(0, session.clone());
            save_data(&self.data);
            self.summary_session = Some(session);
        }
    }

    fn draw_skeleton(painter: &egui::Painter, rect: Rect, pose: BodyPose, severity: FormSeverity) {
        let color = match severity {
            FormSeverity::Good => EMERALD,
            FormSeverity::Warning => AMBER,
            FormSeverity::Error => RED,
        };
        let to_pos = |p: crate::analyzer::Point2D| {
            Pos2::new(rect.left() + p.x * rect.width(), rect.top() + p.y * rect.height())
        };

        let pts = [
            (pose.ear, pose.shoulder),
            (pose.shoulder, pose.elbow),
            (pose.elbow, pose.wrist),
            (pose.shoulder, pose.hip),
            (pose.hip, pose.knee),
            (pose.knee, pose.ankle),
        ];

        for (a, b) in pts {
            painter.line_segment([to_pos(a), to_pos(b)], Stroke::new(4.0_f32, color));
        }
        for joint in [pose.ear, pose.shoulder, pose.elbow, pose.wrist, pose.hip, pose.knee, pose.ankle] {
            let pos = to_pos(joint);
            painter.circle_filled(pos, 6.0, Color32::WHITE);
            painter.circle_stroke(pos, 6.0, Stroke::new(2.5_f32, color));
        }
    }

    fn render_live_workout(&mut self, ctx: &egui::Context, ui: &mut egui::Ui) {
        ctx.request_repaint();

        let frame = self.camera.take_frame();
        if let Some(ref f) = frame {
            let color_img = egui::ColorImage::from_rgba_unmultiplied([f.width, f.height], &f.rgba);
            self.camera_texture = Some(ctx.load_texture("cam_feed", color_img, TextureOptions::LINEAR));
        }
        let pose = self.camera.estimate_pose(frame.as_ref());
        let (state, _rep) = self.analyzer.analyze(pose);
        self.live_state = state;

        let v = get_variation(self.selected_variation);
        let elapsed = self.workout_start.elapsed().as_secs();

        // Top HUD
        ui.horizontal(|ui| {
            if ui.button("← Iptal").clicked() {
                self.camera.stop();
                self.workout_active = false;
            }
            ui.vertical(|ui| {
                ui.label(RichText::new(v.name).strong().color(Color32::WHITE));
                ui.label(
                    RichText::new(format!(
                        "{:02}:{:02}  •  Form %{}  •  {}",
                        elapsed / 60,
                        elapsed % 60,
                        self.live_state.current_score,
                        self.camera.status
                    ))
                    .size(11.0)
                    .color(TEXT_MUTED),
                );
            });
            ui.with_layout(Layout::right_to_left(Align::Center), |ui| {
                ui.toggle_value(&mut self.show_skeleton, "Iskelet");
            });
        });

        ui.add_space(8.0);

        // Camera + Skeleton Canvas
        let avail = ui.available_width();
        let cam_size = Vec2::new(avail, (avail * 0.72).clamp(240.0, 380.0));
        let (rect, _) = ui.allocate_exact_size(cam_size, egui::Sense::hover());
        let painter = ui.painter_at(rect);

        painter.rect_filled(rect, CornerRadius::same(16), Color32::from_rgb(12, 18, 32));
        if let Some(ref tex) = self.camera_texture {
            if !self.camera.simulation_mode {
                painter.image(
                    tex.id(),
                    rect,
                    Rect::from_min_max(Pos2::ZERO, Pos2::new(1.0, 1.0)),
                    Color32::WHITE,
                );
            }
        }
        if self.show_skeleton {
            if let Some(pose) = self.live_state.pose {
                Self::draw_skeleton(&painter, rect, pose, self.live_state.severity);
            }
        }

        // Depth bar on right edge of viewport
        let bar_rect = Rect::from_min_size(
            Pos2::new(rect.right() - 22.0, rect.top() + 24.0),
            Vec2::new(10.0, rect.height() - 48.0),
        );
        painter.rect_filled(bar_rect, CornerRadius::same(5), Color32::from_rgba_unmultiplied(15, 23, 42, 200));
        let fill_h = bar_rect.height() * (self.live_state.depth_percent / 100.0);
        let fill_rect = Rect::from_min_max(
            Pos2::new(bar_rect.left(), bar_rect.bottom() - fill_h),
            bar_rect.right_bottom(),
        );
        painter.rect_filled(fill_rect, CornerRadius::same(5), EMERALD);

        // Angle badges
        if let Some(elbow) = self.live_state.elbow_angle {
            painter.text(
                Pos2::new(rect.left() + 12.0, rect.top() + 14.0),
                egui::Align2::LEFT_TOP,
                format!("Dirsek: {}°   Govde: {}°", elbow, self.live_state.body_angle.unwrap_or(180)),
                FontId::proportional(13.0),
                Color32::WHITE,
            );
        }

        ui.add_space(10.0);

        // Feedback Banner
        let (banner_bg, banner_fg) = match self.live_state.severity {
            FormSeverity::Good => (Color32::from_rgb(6, 78, 59), Color32::from_rgb(167, 243, 208)),
            FormSeverity::Warning => (Color32::from_rgb(120, 53, 15), Color32::from_rgb(253, 230, 138)),
            FormSeverity::Error => (Color32::from_rgb(127, 29, 29), Color32::from_rgb(254, 202, 202)),
        };
        Frame::new()
            .fill(banner_bg)
            .corner_radius(CornerRadius::same(12))
            .inner_margin(12.0)
            .show(ui, |ui| {
                ui.set_width(ui.available_width());
                ui.label(RichText::new(&self.live_state.feedback).strong().color(banner_fg));
            });

        ui.add_space(12.0);

        // Counter + Finish CTA
        ui.horizontal(|ui| {
            ui.vertical(|ui| {
                ui.label(
                    RichText::new(format!("{}", self.live_state.reps))
                        .size(54.0)
                        .strong()
                        .color(Color32::WHITE),
                );
                let target_txt = match self.target_reps {
                    Some(t) => format!("/ {} HEDEF", t),
                    None => "TEKRAR".into(),
                };
                ui.label(RichText::new(target_txt).size(12.0).color(EMERALD));
            });

            ui.with_layout(Layout::right_to_left(Align::Center), |ui| {
                let btn = egui::Button::new(
                    RichText::new("Bitir & Kaydet").strong().size(15.0).color(BG_DARK),
                )
                .fill(EMERALD)
                .corner_radius(CornerRadius::same(12))
                .min_size(Vec2::new(150.0, 48.0));
                if ui.add(btn).clicked() {
                    self.finish_workout();
                }
            });
        });
    }

    fn render_workout_setup(&mut self, ui: &mut egui::Ui) {
        let today = today_reps(&self.data.sessions);

        Frame::new()
            .fill(CARD_BG)
            .stroke(Stroke::new(1.0_f32, CARD_BORDER))
            .corner_radius(CornerRadius::same(14))
            .inner_margin(12.0)
            .show(ui, |ui| {
                ui.set_width(ui.available_width());
                ui.horizontal(|ui| {
                    ui.vertical(|ui| {
                        ui.label(RichText::new("Bugunku Toplam").size(11.0).color(TEXT_MUTED));
                        ui.label(
                            RichText::new(format!("{} / {} sinav", today, self.data.daily_goal))
                                .size(18.0)
                                .strong()
                                .color(Color32::WHITE),
                        );
                    });
                    ui.with_layout(Layout::right_to_left(Align::Center), |ui| {
                        if ui.button("Gelisimi Gor ->").clicked() {
                            self.tab = Tab::Progress;
                        }
                    });
                });
            });

        ui.add_space(12.0);
        ui.label(RichText::new("1. Sinav Varyasyonu Sec").strong().size(14.0).color(Color32::WHITE));
        ui.add_space(6.0);

        egui::Grid::new("var_grid")
            .num_columns(2)
            .spacing([8.0, 8.0])
            .show(ui, |ui| {
                for (i, v) in VARIATIONS.iter().enumerate() {
                    let selected = v.id == self.selected_variation;
                    let fill = if selected {
                        Color32::from_rgb(6, 78, 59)
                    } else {
                        CARD_BG
                    };
                    let stroke = if selected {
                        Stroke::new(1.5_f32, EMERALD)
                    } else {
                        Stroke::new(1.0_f32, CARD_BORDER)
                    };
                    let resp = Frame::new()
                        .fill(fill)
                        .stroke(stroke)
                        .corner_radius(CornerRadius::same(12))
                        .inner_margin(10.0)
                        .show(ui, |ui| {
                            ui.set_min_width(170.0);
                            ui.label(RichText::new(v.difficulty).size(10.0).color(SKY));
                            ui.label(RichText::new(v.short_name).strong().size(14.0).color(Color32::WHITE));
                            ui.label(RichText::new(v.muscles).size(10.5).color(TEXT_MUTED));
                        })
                        .response
                        .interact(egui::Sense::click());
                    if resp.clicked() {
                        self.selected_variation = v.id;
                    }
                    if i % 2 == 1 {
                        ui.end_row();
                    }
                }
            });

        ui.add_space(10.0);
        let sel = get_variation(self.selected_variation);
        Frame::new()
            .fill(CARD_BG)
            .stroke(Stroke::new(1.0_f32, CARD_BORDER))
            .corner_radius(CornerRadius::same(12))
            .inner_margin(10.0)
            .show(ui, |ui| {
                ui.set_width(ui.available_width());
                ui.label(RichText::new(sel.name).strong().color(Color32::WHITE));
                ui.label(RichText::new(sel.description).size(12.0).color(TEXT_MUTED));
                ui.add_space(4.0);
                ui.label(RichText::new(format!("Kamera Ipucu: {}", sel.camera_tip)).size(11.5).color(SKY));
            });

        ui.add_space(10.0);
        ui.label(RichText::new("2. Set Hedefi").strong().size(14.0).color(Color32::WHITE));
        ui.add_space(4.0);
        ui.horizontal(|ui| {
            for opt in [None, Some(10), Some(20), Some(30), Some(50)] {
                let label = opt.map(|n| n.to_string()).unwrap_or_else(|| "Serbest".into());
                let active = self.target_reps == opt;
                if ui.selectable_label(active, label).clicked() {
                    self.target_reps = opt;
                }
            }
        });

        ui.add_space(14.0);
        let cam_btn = egui::Button::new(
            RichText::new("Kamerayi Ac & Sinava Basla").strong().size(15.0).color(BG_DARK),
        )
        .fill(EMERALD)
        .corner_radius(CornerRadius::same(14))
        .min_size(Vec2::new(ui.available_width(), 46.0));
        if ui.add(cam_btn).clicked() {
            self.start_workout(false);
        }

        ui.add_space(6.0);
        let sim_btn = egui::Button::new(
            RichText::new("Simulasyon Moduyla Test Et (Kamerasiz)").size(13.0).color(Color32::WHITE),
        )
        .fill(CARD_BG)
        .stroke(Stroke::new(1.0_f32, CARD_BORDER))
        .corner_radius(CornerRadius::same(12))
        .min_size(Vec2::new(ui.available_width(), 38.0));
        if ui.add(sim_btn).clicked() {
            self.start_workout(true);
        }
    }

    fn render_progress(&mut self, ui: &mut egui::Ui) {
        let total_reps: u32 = self.data.sessions.iter().map(|s| s.reps).sum();
        let streak = calculate_streak(&self.data.sessions);
        let best = self.data.sessions.iter().map(|s| s.reps).max().unwrap_or(0);
        let avg_score = if self.data.sessions.is_empty() {
            0
        } else {
            self.data.sessions.iter().map(|s| s.avg_form_score).sum::<u32>()
                / self.data.sessions.len() as u32
        };

        ui.label(RichText::new("Gelisimin").size(20.0).strong().color(Color32::WHITE));
        ui.add_space(8.0);

        ui.horizontal(|ui| {
            for (title, val) in [
                ("Toplam", format!("{}", total_reps)),
                ("Seri", format!("{} gun", streak)),
                ("Ort. Form", format!("%{}", avg_score)),
                ("En Iyi", format!("{}", best)),
            ] {
                Frame::new()
                    .fill(CARD_BG)
                    .stroke(Stroke::new(1.0_f32, CARD_BORDER))
                    .corner_radius(CornerRadius::same(12))
                    .inner_margin(10.0)
                    .show(ui, |ui| {
                        ui.set_min_width(78.0);
                        ui.label(RichText::new(title).size(10.5).color(TEXT_MUTED));
                        ui.label(RichText::new(val).size(17.0).strong().color(Color32::WHITE));
                    });
            }
        });

        ui.add_space(12.0);
        // 7-day bar chart
        let days = last_7_days(&self.data.sessions);
        let max_v = days.iter().map(|(_, r)| *r).max().unwrap_or(10).max(10) as f32;

        Frame::new()
            .fill(CARD_BG)
            .stroke(Stroke::new(1.0_f32, CARD_BORDER))
            .corner_radius(CornerRadius::same(14))
            .inner_margin(12.0)
            .show(ui, |ui| {
                ui.label(RichText::new("Son 7 Gun").strong().color(Color32::WHITE));
                let (rect, _) = ui.allocate_exact_size(Vec2::new(ui.available_width(), 120.0), egui::Sense::hover());
                let painter = ui.painter_at(rect);
                let col_w = rect.width() / 7.0;
                for (i, (label, reps)) in days.iter().enumerate() {
                    let h = if *reps > 0 {
                        ((*reps as f32 / max_v) * 76.0).max(10.0)
                    } else {
                        4.0
                    };
                    let cx = rect.left() + col_w * (i as f32 + 0.5);
                    let bar = Rect::from_min_max(
                        Pos2::new(cx - 12.0, rect.bottom() - 22.0 - h),
                        Pos2::new(cx + 12.0, rect.bottom() - 22.0),
                    );
                    let col = if *reps > 0 { EMERALD } else { CARD_BORDER };
                    painter.rect_filled(bar, CornerRadius::same(4), col);
                    painter.text(
                        Pos2::new(cx, rect.bottom() - 6.0),
                        egui::Align2::CENTER_BOTTOM,
                        label,
                        FontId::proportional(11.0),
                        TEXT_MUTED,
                    );
                }
            });

        let issues = top_form_issues(&self.data.sessions);
        if !issues.is_empty() {
            ui.add_space(12.0);
            ui.label(RichText::new("En Sik Form Hatalarin").strong().color(AMBER));
            for (issue, count) in issues.iter().take(3) {
                Frame::new()
                    .fill(CARD_BG)
                    .stroke(Stroke::new(1.0_f32, CARD_BORDER))
                    .corner_radius(CornerRadius::same(10))
                    .inner_margin(10.0)
                    .show(ui, |ui| {
                        ui.set_width(ui.available_width());
                        ui.label(RichText::new(format!("{} ({} kez)", issue.label(), count)).strong().color(AMBER));
                        ui.label(RichText::new(issue.recommendation()).size(11.5).color(TEXT_MUTED));
                    });
            }
        }

        ui.add_space(12.0);
        ui.label(RichText::new("Antrenman Gecmisi").strong().color(Color32::WHITE));
        if self.data.sessions.is_empty() {
            ui.label(RichText::new("Henuz kayitli antrenman yok.").color(TEXT_MUTED));
        } else {
            for s in &self.data.sessions {
                let v = get_variation(s.variation);
                Frame::new()
                    .fill(CARD_BG)
                    .stroke(Stroke::new(1.0_f32, CARD_BORDER))
                    .corner_radius(CornerRadius::same(10))
                    .inner_margin(10.0)
                    .show(ui, |ui| {
                        ui.set_width(ui.available_width());
                        ui.horizontal(|ui| {
                            ui.vertical(|ui| {
                                ui.label(RichText::new(v.name).strong().color(Color32::WHITE));
                                ui.label(
                                    RichText::new(format!(
                                        "{} • {}sn • Form %{}",
                                        s.timestamp.format("%d.%m %H:%M"),
                                        s.duration_sec,
                                        s.avg_form_score
                                    ))
                                    .size(11.0)
                                    .color(TEXT_MUTED),
                                );
                            });
                            ui.with_layout(Layout::right_to_left(Align::Center), |ui| {
                                ui.label(RichText::new(format!("{} tekrar", s.reps)).strong().size(16.0).color(EMERALD));
                            });
                        });
                    });
            }
        }
    }

    fn render_coach(&mut self, ui: &mut egui::Ui) {
        ui.label(RichText::new("AI Form Kocu & Rehber").size(20.0).strong().color(Color32::WHITE));
        ui.add_space(8.0);
        for v in VARIATIONS {
            Frame::new()
                .fill(CARD_BG)
                .stroke(Stroke::new(1.0_f32, CARD_BORDER))
                .corner_radius(CornerRadius::same(12))
                .inner_margin(10.0)
                .show(ui, |ui| {
                    ui.set_width(ui.available_width());
                    ui.label(RichText::new(format!("{} [{}]", v.name, v.difficulty)).strong().color(Color32::WHITE));
                    ui.label(RichText::new(v.description).size(11.5).color(TEXT_MUTED));
                    ui.add_space(4.0);
                    for tip in v.checklist {
                        ui.label(RichText::new(format!("✓ {}", tip)).size(11.5).color(EMERALD));
                    }
                });
            ui.add_space(6.0);
        }
    }
}

impl eframe::App for SinavKocApp {
    fn update(&mut self, ctx: &egui::Context, _frame: &mut eframe::Frame) {
        egui::TopBottomPanel::top("top_bar").show(ctx, |ui| {
            ui.add_space(4.0);
            ui.horizontal(|ui| {
                ui.label(RichText::new("SinavKoc").size(18.0).strong().color(EMERALD));
                ui.label(RichText::new("PURE RUST").size(10.0).strong().color(SKY));
                ui.with_layout(Layout::right_to_left(Align::Center), |ui| {
                    let streak = calculate_streak(&self.data.sessions);
                    ui.label(RichText::new(format!("{} gun seri", streak)).color(AMBER).strong());
                });
            });
            ui.add_space(4.0);
        });

        if !self.workout_active {
            egui::TopBottomPanel::bottom("nav_bar").show(ctx, |ui| {
                ui.add_space(4.0);
                ui.columns(3, |cols| {
                    if cols[0].selectable_label(self.tab == Tab::Workout, "Antrenman").clicked() {
                        self.tab = Tab::Workout;
                    }
                    if cols[1].selectable_label(self.tab == Tab::Progress, "Gelisim").clicked() {
                        self.tab = Tab::Progress;
                    }
                    if cols[2].selectable_label(self.tab == Tab::Coach, "Koc").clicked() {
                        self.tab = Tab::Coach;
                    }
                });
                ui.add_space(4.0);
            });
        }

        egui::CentralPanel::default().show(ctx, |ui| {
            egui::ScrollArea::vertical().show(ui, |ui| {
                if self.workout_active {
                    self.render_live_workout(ctx, ui);
                } else {
                    match self.tab {
                        Tab::Workout => self.render_workout_setup(ui),
                        Tab::Progress => self.render_progress(ui),
                        Tab::Coach => self.render_coach(ui),
                    }
                }
            });
        });

        // Summary modal
        let mut close_summary = false;
        if let Some(ref session) = self.summary_session {
            egui::Window::new("Antrenman Ozeti")
                .collapsible(false)
                .resizable(false)
                .anchor(egui::Align2::CENTER_CENTER, [0.0, 0.0])
                .show(ctx, |ui| {
                    let v = get_variation(session.variation);
                    ui.label(RichText::new(v.name).strong().size(16.0).color(Color32::WHITE));
                    ui.label(format!(
                        "{} tekrar  •  Form Puanin: %{}  •  {}sn  •  {} kcal",
                        session.reps, session.avg_form_score, session.duration_sec, session.calories
                    ));
                    ui.add_space(8.0);
                    if session.issue_counts.is_empty() {
                        ui.label(RichText::new("Harika! Belirgin bir form hatasi tespit edilmedi.").color(EMERALD));
                    } else {
                        for (issue, cnt) in &session.issue_counts {
                            ui.label(RichText::new(format!("• {} ({} kez)", issue.label(), cnt)).strong().color(AMBER));
                            ui.label(RichText::new(issue.recommendation()).size(11.5).color(TEXT_MUTED));
                        }
                    }
                    ui.add_space(10.0);
                    if ui.button("Tamam").clicked() {
                        close_summary = true;
                    }
                });
        }
        if close_summary {
            self.summary_session = None;
        }
    }
}
