import React from 'react';
import {
  AlertTriangle,
  Award,
  BarChart3,
  Calendar,
  Flame,
  Target,
  Trash2,
  TrendingUp,
} from 'lucide-react';
import { FORM_ISSUES, getVariationById, PUSHUP_VARIATIONS } from '../data/variations';
import type { AppSettings, WorkoutSession } from '../types';
import {
  calculateStreak,
  getLast7DaysData,
  getTodayReps,
  getTopFormIssues,
} from '../utils/storage';

interface ProgressViewProps {
  sessions: WorkoutSession[];
  settings: AppSettings;
  onUpdateSettings: (patch: Partial<AppSettings>) => void;
  onSelectSession: (session: WorkoutSession) => void;
  onDeleteSession: (id: string) => void;
}

export const ProgressView: React.FC<ProgressViewProps> = ({
  sessions,
  settings,
  onUpdateSettings,
  onSelectSession,
  onDeleteSession,
}) => {
  const totalReps = sessions.reduce((acc, s) => acc + s.reps, 0);
  const streak = calculateStreak(sessions);
  const todayReps = getTodayReps(sessions);
  const bestSet = sessions.reduce((max, s) => Math.max(max, s.reps), 0);
  const avgScore =
    sessions.length > 0
      ? Math.round(
          sessions.reduce((acc, s) => acc + s.avgFormScore, 0) / sessions.length
        )
      : 0;

  const weekData = getLast7DaysData(sessions);
  const maxDayReps = Math.max(10, ...weekData.map((d) => d.reps));
  const topIssues = getTopFormIssues(sessions).slice(0, 3);

  const dailyProgress = Math.min(
    100,
    Math.round((todayReps / Math.max(1, settings.dailyGoal)) * 100)
  );

  // Variation totals
  const variationStats = PUSHUP_VARIATIONS.map((v) => {
    const reps = sessions
      .filter((s) => s.variationId === v.id)
      .reduce((acc, s) => acc + s.reps, 0);
    return { variation: v, reps };
  })
    .filter((item) => item.reps > 0)
    .sort((a, b) => b.reps - a.reps);

  const formatDate = (iso: string) => {
    const d = new Date(iso);
    return d.toLocaleDateString('tr-TR', {
      day: 'numeric',
      month: 'short',
      hour: '2-digit',
      minute: '2-digit',
    });
  };

  return (
    <div className="space-y-5 pb-6">
      <div>
        <h1 className="text-2xl font-bold tracking-tight text-white">Gelişimin</h1>
        <p className="mt-1 text-sm text-slate-400">
          Antrenman istatistiklerin, haftalık trendin ve form analizin
        </p>
      </div>

      {/* Daily Goal Card */}
      <div className="rounded-2xl border border-slate-800 bg-gradient-to-br from-slate-900 to-[#0d1829] p-4">
        <div className="flex items-center justify-between">
          <div className="flex items-center gap-2">
            <Target className="h-4 w-4 text-emerald-400" />
            <span className="text-sm font-semibold text-white">Günlük Hedef</span>
          </div>
          <div className="flex items-center gap-1.5">
            {[20, 30, 50, 100].map((goal) => (
              <button
                key={goal}
                onClick={() => onUpdateSettings({ dailyGoal: goal })}
                className={`rounded-lg px-2 py-0.5 text-xs font-semibold transition ${
                  settings.dailyGoal === goal
                    ? 'bg-emerald-500 text-slate-950'
                    : 'bg-slate-800 text-slate-400 hover:text-slate-200'
                }`}
              >
                {goal}
              </button>
            ))}
          </div>
        </div>

        <div className="mt-3 flex items-baseline justify-between">
          <div className="text-2xl font-black text-white tabular-nums">
            {todayReps}{' '}
            <span className="text-sm font-medium text-slate-400">
              / {settings.dailyGoal} şınav
            </span>
          </div>
          <span className="text-sm font-bold text-emerald-400 tabular-nums">
            %{dailyProgress}
          </span>
        </div>
        <div className="mt-2 h-2.5 w-full overflow-hidden rounded-full bg-slate-800">
          <div
            className="h-full rounded-full bg-gradient-to-r from-emerald-500 to-teal-300 transition-all duration-500"
            style={{ width: `${dailyProgress}%` }}
          />
        </div>
      </div>

      {/* 4 Stat Cards */}
      <div className="grid grid-cols-2 gap-3">
        <div className="rounded-2xl border border-slate-800 bg-slate-900/70 p-4">
          <div className="flex items-center gap-2 text-xs font-medium text-slate-400">
            <TrendingUp className="h-4 w-4 text-emerald-400" />
            Toplam Şınav
          </div>
          <div className="mt-2 text-2xl font-black text-white tabular-nums">
            {totalReps}
          </div>
        </div>

        <div className="rounded-2xl border border-slate-800 bg-slate-900/70 p-4">
          <div className="flex items-center gap-2 text-xs font-medium text-slate-400">
            <Flame className="h-4 w-4 text-orange-400" />
            Günlük Seri
          </div>
          <div className="mt-2 text-2xl font-black text-white tabular-nums">
            {streak} <span className="text-xs font-medium text-slate-400">gün</span>
          </div>
        </div>

        <div className="rounded-2xl border border-slate-800 bg-slate-900/70 p-4">
          <div className="flex items-center gap-2 text-xs font-medium text-slate-400">
            <Award className="h-4 w-4 text-sky-400" />
            Ort. Form Puanı
          </div>
          <div className="mt-2 text-2xl font-black text-white tabular-nums">
            {sessions.length > 0 ? `%${avgScore}` : '--'}
          </div>
        </div>

        <div className="rounded-2xl border border-slate-800 bg-slate-900/70 p-4">
          <div className="flex items-center gap-2 text-xs font-medium text-slate-400">
            <BarChart3 className="h-4 w-4 text-purple-400" />
            En İyi Set
          </div>
          <div className="mt-2 text-2xl font-black text-white tabular-nums">
            {bestSet > 0 ? bestSet : '--'}
          </div>
        </div>
      </div>

      {/* 7-Day Chart */}
      <div className="rounded-2xl border border-slate-800 bg-slate-900/70 p-4">
        <div className="mb-4 flex items-center justify-between">
          <h2 className="text-sm font-semibold text-white">Son 7 Gün</h2>
          <span className="text-xs text-slate-400">Günlük şınav sayısı</span>
        </div>
        <div className="flex h-36 items-end justify-between gap-2 pt-4">
          {weekData.map((day) => {
            const heightPct = day.reps > 0 ? Math.max(12, (day.reps / maxDayReps) * 100) : 4;
            return (
              <div
                key={day.dayKey}
                className="flex flex-1 flex-col items-center gap-1.5"
              >
                <span className="text-[11px] font-semibold text-slate-300 tabular-nums">
                  {day.reps > 0 ? day.reps : ''}
                </span>
                <div className="relative flex h-24 w-full items-end justify-center">
                  <div
                    className={`w-full max-w-[28px] rounded-t-lg transition-all duration-500 ${
                      day.reps > 0
                        ? 'bg-gradient-to-t from-emerald-600 to-emerald-400'
                        : 'bg-slate-800/80'
                    }`}
                    style={{ height: `${heightPct}%` }}
                  />
                </div>
                <span className="text-[11px] font-medium text-slate-400">
                  {day.label}
                </span>
              </div>
            );
          })}
        </div>
      </div>

      {/* Recurring Form Issues & Recommendations */}
      {topIssues.length > 0 && (
        <div className="rounded-2xl border border-amber-500/20 bg-slate-900/70 p-4">
          <div className="mb-3 flex items-center gap-2">
            <AlertTriangle className="h-4 w-4 text-amber-400" />
            <h2 className="text-sm font-semibold text-white">
              En Sık Görülen Form Hataların
            </h2>
          </div>
          <div className="space-y-2.5">
            {topIssues.map(({ type, count }) => {
              const meta = FORM_ISSUES[type];
              return (
                <div
                  key={type}
                  className="rounded-xl border border-slate-800 bg-slate-950/60 p-3"
                >
                  <div className="flex items-center justify-between">
                    <span className="text-xs font-bold text-amber-300">
                      {meta.label}
                    </span>
                    <span className="text-[11px] font-semibold text-slate-400 tabular-nums">
                      Toplam {count} kez
                    </span>
                  </div>
                  <p className="mt-1 text-xs leading-relaxed text-slate-300">
                    {meta.recommendation}
                  </p>
                </div>
              );
            })}
          </div>
        </div>
      )}

      {/* Variation Breakdown */}
      {variationStats.length > 0 && (
        <div className="rounded-2xl border border-slate-800 bg-slate-900/70 p-4">
          <h2 className="mb-3 text-sm font-semibold text-white">Varyasyon Dağılımı</h2>
          <div className="space-y-2.5">
            {variationStats.map(({ variation, reps }) => {
              const pct = Math.round((reps / Math.max(1, totalReps)) * 100);
              return (
                <div key={variation.id}>
                  <div className="mb-1 flex items-center justify-between text-xs">
                    <span className="font-medium text-slate-200">{variation.name}</span>
                    <span className="font-semibold text-slate-400 tabular-nums">
                      {reps} tekrar (%{pct})
                    </span>
                  </div>
                  <div className="h-2 w-full overflow-hidden rounded-full bg-slate-800">
                    <div
                      className="h-full rounded-full bg-sky-400"
                      style={{ width: `${pct}%` }}
                    />
                  </div>
                </div>
              );
            })}
          </div>
        </div>
      )}

      {/* Session History */}
      <div>
        <div className="mb-3 flex items-center gap-2">
          <Calendar className="h-4 w-4 text-slate-400" />
          <h2 className="text-sm font-semibold text-white">Antrenman Geçmişi</h2>
        </div>

        {sessions.length === 0 ? (
          <div className="rounded-2xl border border-dashed border-slate-800 bg-slate-900/40 p-8 text-center">
            <p className="text-sm font-medium text-slate-300">
              Henüz kayıtlı antrenman yok
            </p>
            <p className="mt-1 text-xs text-slate-500">
              Antrenman sekmesinden bir şınav varyasyonu seçip ilk setini kaydet!
            </p>
          </div>
        ) : (
          <div className="space-y-2.5">
            {sessions.map((s) => {
              const v = getVariationById(s.variationId);
              return (
                <div
                  key={s.id}
                  onClick={() => onSelectSession(s)}
                  className="flex cursor-pointer items-center justify-between rounded-2xl border border-slate-800 bg-slate-900/70 p-3.5 transition hover:border-slate-700 active:scale-99"
                >
                  <div>
                    <div className="flex items-center gap-2">
                      <span className="text-sm font-semibold text-white">
                        {v.name}
                      </span>
                      <span
                        className={`rounded-full px-2 py-0.5 text-[10px] font-bold ${
                          s.avgFormScore >= 85
                            ? 'bg-emerald-500/15 text-emerald-400'
                            : s.avgFormScore >= 70
                            ? 'bg-amber-500/15 text-amber-300'
                            : 'bg-red-500/15 text-red-400'
                        }`}
                      >
                        Form %{s.avgFormScore}
                      </span>
                    </div>
                    <div className="mt-1 text-xs text-slate-400">
                      {formatDate(s.date)} • {s.durationSec}sn
                    </div>
                  </div>

                  <div className="flex items-center gap-3">
                    <div className="text-right">
                      <div className="text-xl font-black text-white tabular-nums">
                        {s.reps}
                      </div>
                      <div className="text-[10px] uppercase text-slate-400">tekrar</div>
                    </div>
                    <button
                      onClick={(e) => {
                        e.stopPropagation();
                        onDeleteSession(s.id);
                      }}
                      className="flex h-8 w-8 items-center justify-center rounded-lg text-slate-500 hover:bg-red-500/15 hover:text-red-400"
                      aria-label="Sil"
                    >
                      <Trash2 className="h-4 w-4" />
                    </button>
                  </div>
                </div>
              );
            })}
          </div>
        )}
      </div>
    </div>
  );
};
