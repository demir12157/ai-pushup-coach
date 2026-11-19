use std::collections::{HashMap, HashSet};
use std::time::Instant;

use serde::{Deserialize, Serialize};

use crate::variations::{get_variation, FormIssue, PushupVariation, VariationId};

#[derive(Debug, Clone, Copy, Default)]
pub struct Point2D {
    pub x: f32,
    pub y: f32,
    pub confidence: f32,
}

#[derive(Debug, Clone, Copy, Default)]
pub struct BodyPose {
    pub ear: Point2D,
    pub shoulder: Point2D,
    pub elbow: Point2D,
    pub wrist: Point2D,
    pub hip: Point2D,
    pub knee: Point2D,
    pub ankle: Point2D,
}

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum Phase {
    Idle,
    Up,
    Down,
}

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum FormSeverity {
    Good,
    Warning,
    Error,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct RepRecord {
    pub rep_number: u32,
    pub min_elbow_angle: u32,
    pub duration_ms: u64,
    pub score: u32,
    pub issues: Vec<FormIssue>,
}

#[derive(Debug, Clone)]
pub struct LiveState {
    pub phase: Phase,
    pub reps: u32,
    pub elbow_angle: Option<u32>,
    pub body_angle: Option<u32>,
    pub depth_percent: f32,
    pub severity: FormSeverity,
    pub feedback: String,
    pub current_score: u32,
    pub pose: Option<BodyPose>,
}

pub fn calculate_angle(a: Point2D, b: Point2D, c: Point2D) -> f32 {
    let rad = (c.y - b.y).atan2(c.x - b.x) - (a.y - b.y).atan2(a.x - b.x);
    let mut deg = rad.to_degrees().abs();
    if deg > 180.0 {
        deg = 360.0 - deg;
    }
    deg
}

pub struct PushupAnalyzer {
    variation: &'static PushupVariation,
    phase: Phase,
    reps: u32,
    rep_records: Vec<RepRecord>,
    issue_counts: HashMap<FormIssue, u32>,

    smoothed_elbow: Option<f32>,
    smoothed_body: Option<f32>,

    rep_start: Instant,
    rep_min_elbow: f32,
    rep_issues: HashSet<FormIssue>,
    attempted_descent: bool,
}

impl PushupAnalyzer {
    pub fn new(variation_id: VariationId) -> Self {
        Self {
            variation: get_variation(variation_id),
            phase: Phase::Idle,
            reps: 0,
            rep_records: Vec::new(),
            issue_counts: HashMap::new(),
            smoothed_elbow: None,
            smoothed_body: None,
            rep_start: Instant::now(),
            rep_min_elbow: 180.0,
            rep_issues: HashSet::new(),
            attempted_descent: false,
        }
    }

    pub fn reset(&mut self, variation_id: VariationId) {
        *self = Self::new(variation_id);
    }

    pub fn avg_score(&self) -> u32 {
        if self.rep_records.is_empty() {
            return 100;
        }
        let sum: u32 = self.rep_records.iter().map(|r| r.score).sum();
        sum / self.rep_records.len() as u32
    }

    pub fn rep_records(&self) -> &[RepRecord] {
        &self.rep_records
    }

    pub fn issue_counts(&self) -> &HashMap<FormIssue, u32> {
        &self.issue_counts
    }

    pub fn reps(&self) -> u32 {
        self.reps
    }

    pub fn analyze(&mut self, pose: Option<BodyPose>) -> (LiveState, Option<RepRecord>) {
        let Some(p) = pose else {
            return (
                LiveState {
                    phase: self.phase,
                    reps: self.reps,
                    elbow_angle: self.smoothed_elbow.map(|v| v.round() as u32),
                    body_angle: self.smoothed_body.map(|v| v.round() as u32),
                    depth_percent: 0.0,
                    severity: FormSeverity::Warning,
                    feedback: "Kameraya yan profilden tum vucudun gorunecek sekilde gec".into(),
                    current_score: self.avg_score(),
                    pose: None,
                },
                None,
            );
        };

        let lower = if self.variation.thresholds.use_knee_line || p.ankle.confidence < 0.35 {
            p.knee
        } else {
            p.ankle
        };

        let raw_elbow = calculate_angle(p.shoulder, p.elbow, p.wrist);
        let raw_body = calculate_angle(p.shoulder, p.hip, lower);

        const ALPHA: f32 = 0.35;
        let elbow_angle = match self.smoothed_elbow {
            Some(prev) => prev * (1.0 - ALPHA) + raw_elbow * ALPHA,
            None => raw_elbow,
        };
        let body_angle = match self.smoothed_body {
            Some(prev) => prev * (1.0 - ALPHA) + raw_body * ALPHA,
            None => raw_body,
        };
        self.smoothed_elbow = Some(elbow_angle);
        self.smoothed_body = Some(body_angle);

        let t = self.variation.thresholds;
        let mut active_issues: Vec<FormIssue> = Vec::new();

        // Check hip sag vs hip pike
        if body_angle < t.min_body_angle {
            let span_x = lower.x - p.shoulder.x;
            if span_x.abs() > 0.05 {
                let ratio = ((p.hip.x - p.shoulder.x) / span_x).clamp(0.0, 1.0);
                let expected_y = p.shoulder.y + ratio * (lower.y - p.shoulder.y);
                if p.hip.y > expected_y + 0.015 {
                    active_issues.push(FormIssue::HipSag);
                } else if p.hip.y < expected_y - 0.015 {
                    active_issues.push(FormIssue::HipPike);
                }
            }
        }

        // Check head drop
        if p.ear.confidence > 0.4 {
            let torso_len = (p.shoulder.x - p.hip.x).hypot(p.shoulder.y - p.hip.y);
            if torso_len > 0.08 && p.ear.y > p.shoulder.y + torso_len * 0.28 {
                active_issues.push(FormIssue::HeadDrop);
            }
        }

        let depth_percent =
            (((t.up_angle - elbow_angle) / (t.up_angle - t.down_angle)) * 100.0).clamp(0.0, 100.0);

        if self.phase == Phase::Up || self.phase == Phase::Down {
            if elbow_angle < self.rep_min_elbow {
                self.rep_min_elbow = elbow_angle;
            }
            for issue in &active_issues {
                self.rep_issues.insert(*issue);
            }
        }

        let mut completed: Option<RepRecord> = None;

        match self.phase {
            Phase::Idle => {
                if elbow_angle >= t.up_angle - 12.0 {
                    self.phase = Phase::Up;
                    self.rep_start = Instant::now();
                    self.rep_min_elbow = elbow_angle;
                    self.rep_issues.clear();
                }
            }
            Phase::Up => {
                if elbow_angle < t.up_angle - 22.0 && !self.attempted_descent {
                    self.attempted_descent = true;
                    self.rep_start = Instant::now();
                }
                if elbow_angle <= t.down_angle {
                    self.phase = Phase::Down;
                } else if self.attempted_descent
                    && self.rep_min_elbow < t.up_angle - 25.0
                    && self.rep_min_elbow > t.down_angle + 8.0
                    && elbow_angle >= t.up_angle - 4.0
                {
                    self.attempted_descent = false;
                    self.rep_min_elbow = 180.0;
                    *self.issue_counts.entry(FormIssue::ShallowDepth).or_insert(0) += 1;
                    active_issues.push(FormIssue::ShallowDepth);
                }
            }
            Phase::Down => {
                if elbow_angle >= t.up_angle {
                    self.reps += 1;
                    let duration_ms = self.rep_start.elapsed().as_millis() as u64;
                    if duration_ms < 750 {
                        self.rep_issues.insert(FormIssue::TooFast);
                    }

                    let issues: Vec<FormIssue> = self.rep_issues.iter().copied().collect();
                    for issue in &issues {
                        *self.issue_counts.entry(*issue).or_insert(0) += 1;
                    }

                    let mut score: i32 = 100;
                    if self.rep_issues.contains(&FormIssue::HipSag) {
                        score -= 20;
                    }
                    if self.rep_issues.contains(&FormIssue::HipPike) {
                        score -= 16;
                    }
                    if self.rep_issues.contains(&FormIssue::HeadDrop) {
                        score -= 10;
                    }
                    if self.rep_issues.contains(&FormIssue::TooFast) {
                        score -= 12;
                    }
                    let score = score.clamp(40, 100) as u32;

                    let rec = RepRecord {
                        rep_number: self.reps,
                        min_elbow_angle: self.rep_min_elbow.round() as u32,
                        duration_ms,
                        score,
                        issues,
                    };
                    self.rep_records.push(rec.clone());
                    completed = Some(rec);

                    self.phase = Phase::Up;
                    self.rep_start = Instant::now();
                    self.rep_min_elbow = 180.0;
                    self.rep_issues.clear();
                    self.attempted_descent = false;
                }
            }
        }

        let (severity, feedback) = if let Some(first) = active_issues.first() {
            let sev = match first {
                FormIssue::HipSag | FormIssue::HipPike => FormSeverity::Error,
                _ => FormSeverity::Warning,
            };
            (sev, first.live_cue().to_string())
        } else if self.phase == Phase::Down {
            (FormSeverity::Good, "Super derinlik! Simdi guclu it".to_string())
        } else if self.phase == Phase::Up && self.reps > 0 {
            (FormSeverity::Good, "Harika form! Tempoyu koru".to_string())
        } else {
            (FormSeverity::Good, "Hazir! Kontrollu sekilde asagi in".to_string())
        };

        (
            LiveState {
                phase: self.phase,
                reps: self.reps,
                elbow_angle: Some(elbow_angle.round() as u32),
                body_angle: Some(body_angle.round() as u32),
                depth_percent,
                severity,
                feedback,
                current_score: self.avg_score(),
                pose: Some(p),
            },
            completed,
        )
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    fn make_pose(elbow_bend: f32, hip_sag: f32) -> BodyPose {
        // elbow_bend: 0.0 = straight arm (~175 deg), 1.0 = deep bottom (~80 deg)
        BodyPose {
            ear: Point2D { x: 0.25, y: 0.42 + elbow_bend * 0.14, confidence: 0.99 },
            shoulder: Point2D { x: 0.35, y: 0.45 + elbow_bend * 0.16, confidence: 0.99 },
            elbow: Point2D { x: 0.34 - elbow_bend * 0.10, y: 0.58 + elbow_bend * 0.05, confidence: 0.99 },
            wrist: Point2D { x: 0.35, y: 0.72, confidence: 0.99 },
            hip: Point2D { x: 0.58, y: 0.54 + elbow_bend * 0.09 + hip_sag, confidence: 0.99 },
            knee: Point2D { x: 0.74, y: 0.62 + elbow_bend * 0.04, confidence: 0.99 },
            ankle: Point2D { x: 0.88, y: 0.70, confidence: 0.99 },
        }
    }

    #[test]
    fn counts_clean_pushup_rep() {
        let mut analyzer = PushupAnalyzer::new(VariationId::Standard);
        for _ in 0..8 {
            analyzer.analyze(Some(make_pose(0.0, 0.0)));
        }
        for _ in 0..10 {
            analyzer.analyze(Some(make_pose(1.0, 0.0)));
        }
        for _ in 0..10 {
            analyzer.analyze(Some(make_pose(0.0, 0.0)));
        }
        assert_eq!(analyzer.reps(), 1);
    }

    #[test]
    fn detects_hip_sag() {
        let mut analyzer = PushupAnalyzer::new(VariationId::Standard);
        let mut saw_error = false;
        for _ in 0..10 {
            let (state, _) = analyzer.analyze(Some(make_pose(0.3, 0.14)));
            if state.severity == FormSeverity::Error {
                saw_error = true;
            }
        }
        assert!(saw_error);
    }
}
