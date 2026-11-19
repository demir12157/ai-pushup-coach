import type { AppSettings, FormIssueType, WorkoutSession } from '../types';

const SESSIONS_KEY = 'sinavkoc_sessions_v1';
const SETTINGS_KEY = 'sinavkoc_settings_v1';

export const DEFAULT_SETTINGS: AppSettings = {
  voiceEnabled: true,
  showSkeleton: true,
  facingMode: 'user',
  dailyGoal: 30,
};

export function loadSessions(): WorkoutSession[] {
  try {
    const raw = localStorage.getItem(SESSIONS_KEY);
    if (!raw) return [];
    const parsed = JSON.parse(raw);
    return Array.isArray(parsed) ? parsed : [];
  } catch {
    return [];
  }
}

export function saveSession(session: WorkoutSession): WorkoutSession[] {
  const existing = loadSessions();
  const updated = [session, ...existing];
  try {
    localStorage.setItem(SESSIONS_KEY, JSON.stringify(updated));
  } catch {
    // storage full or unavailable
  }
  return updated;
}

export function deleteSession(id: string): WorkoutSession[] {
  const updated = loadSessions().filter((s) => s.id !== id);
  try {
    localStorage.setItem(SESSIONS_KEY, JSON.stringify(updated));
  } catch {
    // ignore
  }
  return updated;
}

export function loadSettings(): AppSettings {
  try {
    const raw = localStorage.getItem(SETTINGS_KEY);
    if (!raw) return DEFAULT_SETTINGS;
    return { ...DEFAULT_SETTINGS, ...JSON.parse(raw) };
  } catch {
    return DEFAULT_SETTINGS;
  }
}

export function saveSettings(settings: AppSettings): void {
  try {
    localStorage.setItem(SETTINGS_KEY, JSON.stringify(settings));
  } catch {
    // ignore
  }
}

function toDayKey(date: Date): string {
  const y = date.getFullYear();
  const m = String(date.getMonth() + 1).padStart(2, '0');
  const d = String(date.getDate()).padStart(2, '0');
  return `${y}-${m}-${d}`;
}

export function calculateStreak(sessions: WorkoutSession[]): number {
  if (sessions.length === 0) return 0;
  const days = new Set(sessions.map((s) => toDayKey(new Date(s.date))));
  const today = new Date();
  const todayKey = toDayKey(today);
  const yesterday = new Date(today);
  yesterday.setDate(today.getDate() - 1);
  const yesterdayKey = toDayKey(yesterday);

  if (!days.has(todayKey) && !days.has(yesterdayKey)) {
    return 0;
  }

  let streak = 0;
  const cursor = new Date(days.has(todayKey) ? today : yesterday);
  while (days.has(toDayKey(cursor))) {
    streak += 1;
    cursor.setDate(cursor.getDate() - 1);
  }
  return streak;
}

export function getTodayReps(sessions: WorkoutSession[]): number {
  const todayKey = toDayKey(new Date());
  return sessions
    .filter((s) => toDayKey(new Date(s.date)) === todayKey)
    .reduce((acc, s) => acc + s.reps, 0);
}

export function getLast7DaysData(sessions: WorkoutSession[]) {
  const dayNames = ['Paz', 'Pzt', 'Sal', 'Çar', 'Per', 'Cum', 'Cmt'];
  const result: { dayKey: string; label: string; reps: number; avgScore: number }[] = [];

  for (let i = 6; i >= 0; i--) {
    const d = new Date();
    d.setDate(d.getDate() - i);
    const key = toDayKey(d);
    const daySessions = sessions.filter((s) => toDayKey(new Date(s.date)) === key);
    const reps = daySessions.reduce((acc, s) => acc + s.reps, 0);
    const avgScore =
      daySessions.length > 0
        ? Math.round(
            daySessions.reduce((acc, s) => acc + s.avgFormScore, 0) / daySessions.length
          )
        : 0;
    result.push({
      dayKey: key,
      label: dayNames[d.getDay()],
      reps,
      avgScore,
    });
  }

  return result;
}

export function getTopFormIssues(
  sessions: WorkoutSession[]
): { type: FormIssueType; count: number }[] {
  const counts: Partial<Record<FormIssueType, number>> = {};
  for (const s of sessions) {
    for (const [k, v] of Object.entries(s.issueCounts)) {
      const key = k as FormIssueType;
      counts[key] = (counts[key] ?? 0) + (v ?? 0);
    }
  }
  return (Object.entries(counts) as [FormIssueType, number][])
    .filter(([, count]) => count > 0)
    .sort((a, b) => b[1] - a[1])
    .map(([type, count]) => ({ type, count }));
}
