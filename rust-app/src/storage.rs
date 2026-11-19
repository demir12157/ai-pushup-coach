use std::collections::HashMap;
use std::fs;
use std::path::PathBuf;

use chrono::{DateTime, Duration, Local, NaiveDate};
use serde::{Deserialize, Serialize};

use crate::analyzer::RepRecord;
use crate::variations::{FormIssue, VariationId};

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct WorkoutSession {
    pub id: String,
    pub timestamp: DateTime<Local>,
    pub variation: VariationId,
    pub reps: u32,
    pub target_reps: Option<u32>,
    pub duration_sec: u64,
    pub avg_form_score: u32,
    pub calories: f32,
    pub issue_counts: HashMap<FormIssue, u32>,
    pub rep_records: Vec<RepRecord>,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct AppData {
    pub daily_goal: u32,
    pub sessions: Vec<WorkoutSession>,
}

impl Default for AppData {
    fn default() -> Self {
        Self {
            daily_goal: 30,
            sessions: Vec::new(),
        }
    }
}

fn data_file_path() -> PathBuf {
    if let Some(proj) = directories::ProjectDirs::from("com", "sinavkoc", "SinavKocRs") {
        let dir = proj.data_dir();
        let _ = fs::create_dir_all(dir);
        return dir.join("sessions.json");
    }
    PathBuf::from("sinavkoc_sessions.json")
}

pub fn load_data() -> AppData {
    let path = data_file_path();
    fs::read_to_string(path)
        .ok()
        .and_then(|s| serde_json::from_str(&s).ok())
        .unwrap_or_default()
}

pub fn save_data(data: &AppData) {
    let path = data_file_path();
    if let Ok(json) = serde_json::to_string_pretty(data) {
        let _ = fs::write(path, json);
    }
}

pub fn today_reps(sessions: &[WorkoutSession]) -> u32 {
    let today = Local::now().date_naive();
    sessions
        .iter()
        .filter(|s| s.timestamp.date_naive() == today)
        .map(|s| s.reps)
        .sum()
}

pub fn calculate_streak(sessions: &[WorkoutSession]) -> u32 {
    if sessions.is_empty() {
        return 0;
    }
    let dates: std::collections::HashSet<NaiveDate> = sessions
        .iter()
        .map(|s| s.timestamp.date_naive())
        .collect();
    let today = Local::now().date_naive();
    let yesterday = today - Duration::days(1);

    let mut cursor = if dates.contains(&today) {
        today
    } else if dates.contains(&yesterday) {
        yesterday
    } else {
        return 0;
    };

    let mut streak = 0;
    while dates.contains(&cursor) {
        streak += 1;
        cursor -= Duration::days(1);
    }
    streak
}

pub fn last_7_days(sessions: &[WorkoutSession]) -> Vec<(String, u32)> {
    let today = Local::now().date_naive();
    let mut out = Vec::with_capacity(7);
    for i in (0..7).rev() {
        let d = today - Duration::days(i);
        let reps: u32 = sessions
            .iter()
            .filter(|s| s.timestamp.date_naive() == d)
            .map(|s| s.reps)
            .sum();
        out.push((d.format("%a").to_string(), reps));
    }
    out
}

pub fn top_form_issues(sessions: &[WorkoutSession]) -> Vec<(FormIssue, u32)> {
    let mut map: HashMap<FormIssue, u32> = HashMap::new();
    for s in sessions {
        for (k, v) in &s.issue_counts {
            *map.entry(*k).or_insert(0) += *v;
        }
    }
    let mut list: Vec<_> = map.into_iter().filter(|(_, c)| *c > 0).collect();
    list.sort_by(|a, b| b.1.cmp(&a.1));
    list
}
