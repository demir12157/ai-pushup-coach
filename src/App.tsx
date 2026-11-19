import { useState } from 'react';
import {
  Camera,
  Check,
  Dumbbell,
  Eye,
  Flame,
  Sparkles,
  Target,
  TrendingUp,
  Volume2,
  VolumeX,
} from 'lucide-react';
import { CameraWorkout } from './components/CameraWorkout';
import { CoachGuideView } from './components/CoachGuideView';
import { ProgressView } from './components/ProgressView';
import { WorkoutSummaryModal } from './components/WorkoutSummaryModal';
import { getVariationById, PUSHUP_VARIATIONS } from './data/variations';
import type { AppSettings, PushupVariationId, WorkoutSession } from './types';
import {
  calculateStreak,
  deleteSession,
  getTodayReps,
  loadSessions,
  loadSettings,
  saveSession,
  saveSettings,
} from './utils/storage';

type Tab = 'workout' | 'progress' | 'coach';

const TARGET_OPTIONS: { label: string; value: number | null }[] = [
  { label: 'Serbest', value: null },
  { label: '10', value: 10 },
  { label: '20', value: 20 },
  { label: '30', value: 30 },
  { label: '50', value: 50 },
];

export function App() {
  const [activeTab, setActiveTab] = useState<Tab>('workout');
  const [selectedVariationId, setSelectedVariationId] =
    useState<PushupVariationId>('standard');
  const [targetReps, setTargetReps] = useState<number | null>(20);
  const [isWorkoutActive, setIsWorkoutActive] = useState(false);

  const [sessions, setSessions] = useState<WorkoutSession[]>(() => loadSessions());
  const [settings, setSettings] = useState<AppSettings>(() => loadSettings());
  const [summarySession, setSummarySession] = useState<WorkoutSession | null>(null);

  const selectedVariation = getVariationById(selectedVariationId);
  const streak = calculateStreak(sessions);
  const todayReps = getTodayReps(sessions);

  const handleUpdateSettings = (patch: Partial<AppSettings>) => {
    setSettings((prev) => {
      const next = { ...prev, ...patch };
      saveSettings(next);
      return next;
    });
  };

  const handleWorkoutFinish = (session: WorkoutSession | null) => {
    setIsWorkoutActive(false);
    if (session) {
      const updated = saveSession(session);
      setSessions(updated);
      setSummarySession(session);
    }
  };

  const handleDeleteSession = (id: string) => {
    setSessions(deleteSession(id));
  };

  const handleStartFromGuide = (id: PushupVariationId) => {
    setSelectedVariationId(id);
    setActiveTab('workout');
    setIsWorkoutActive(true);
  };

  return (
    <div className="min-h-full bg-[#090d16] text-slate-100">
      <div className="mx-auto flex min-h-screen max-w-md flex-col">
        {/* Top App Bar */}
        <header className="safe-top sticky top-0 z-30 flex items-center justify-between border-b border-slate-800/70 bg-[#090d16]/85 px-4 py-3 backdrop-blur-lg">
          <div className="flex items-center gap-2.5">
            <div className="flex h-9 w-9 items-center justify-center rounded-xl bg-gradient-to-br from-emerald-400 to-teal-600 text-slate-950 shadow-md shadow-emerald-500/20">
              <Dumbbell className="h-5 w-5" />
            </div>
            <div>
              <div className="text-base font-extrabold tracking-tight text-white">
                ŞınavKoç
              </div>
              <div className="text-[10px] font-medium text-emerald-400">
                AI Kamera & Form Analizi
              </div>
            </div>
          </div>

          <div className="flex items-center gap-2">
            <div className="flex items-center gap-1 rounded-full border border-orange-500/25 bg-orange-500/10 px-2.5 py-1 text-xs font-bold text-orange-300 tabular-nums">
              <Flame className="h-3.5 w-3.5 text-orange-400" />
              {streak} gün
            </div>
            <button
              onClick={() =>
                handleUpdateSettings({ voiceEnabled: !settings.voiceEnabled })
              }
              className={`flex h-8 w-8 items-center justify-center rounded-full border transition ${
                settings.voiceEnabled
                  ? 'border-emerald-500/35 bg-emerald-500/15 text-emerald-400'
                  : 'border-slate-700 bg-slate-900 text-slate-400'
              }`}
              title={settings.voiceEnabled ? 'Sesli Koç Açık' : 'Sesli Koç Kapalı'}
            >
              {settings.voiceEnabled ? (
                <Volume2 className="h-4 w-4" />
              ) : (
                <VolumeX className="h-4 w-4" />
              )}
            </button>
          </div>
        </header>

        {/* Main Content */}
        <main className="flex-1 px-4 pt-4 pb-24">
          {activeTab === 'workout' && (
            <div className="space-y-5">
              {/* Today Quick Banner */}
              <div className="flex items-center justify-between rounded-2xl border border-slate-800 bg-gradient-to-r from-slate-900 via-[#0e1726] to-emerald-950/30 px-4 py-3">
                <div>
                  <div className="text-xs font-medium text-slate-400">
                    Bugünkü Toplam
                  </div>
                  <div className="mt-0.5 text-lg font-extrabold text-white tabular-nums">
                    {todayReps}{' '}
                    <span className="text-xs font-medium text-slate-400">
                      / {settings.dailyGoal} şınav
                    </span>
                  </div>
                </div>
                <button
                  onClick={() => setActiveTab('progress')}
                  className="rounded-xl bg-slate-800/90 px-3 py-1.5 text-xs font-semibold text-emerald-400 transition hover:bg-slate-800"
                >
                  Gelişimi Gör →
                </button>
              </div>

              {/* Variation Picker */}
              <div>
                <div className="mb-3 flex items-center justify-between">
                  <h2 className="text-sm font-bold text-white">
                    1. Şınav Varyasyonu Seç
                  </h2>
                  <span className="text-xs text-slate-400">6 varyasyon</span>
                </div>

                <div className="grid grid-cols-2 gap-2.5">
                  {PUSHUP_VARIATIONS.map((v) => {
                    const selected = v.id === selectedVariationId;
                    return (
                      <button
                        key={v.id}
                        onClick={() => setSelectedVariationId(v.id)}
                        className={`relative flex flex-col items-start rounded-2xl border p-3.5 text-left transition active:scale-98 ${
                          selected
                            ? 'border-emerald-400/80 bg-emerald-500/12 shadow-lg shadow-emerald-500/10'
                            : 'border-slate-800 bg-slate-900/70 hover:border-slate-700'
                        }`}
                      >
                        {selected && (
                          <span className="absolute right-2.5 top-2.5 flex h-5 w-5 items-center justify-center rounded-full bg-emerald-400 text-slate-950">
                            <Check className="h-3.5 w-3.5 stroke-[3]" />
                          </span>
                        )}
                        <span
                          className={`rounded-full px-2 py-0.5 text-[10px] font-semibold ${
                            v.difficulty === 'Başlangıç'
                              ? 'bg-emerald-500/15 text-emerald-400'
                              : v.difficulty === 'Orta'
                              ? 'bg-sky-500/15 text-sky-400'
                              : 'bg-purple-500/15 text-purple-300'
                          }`}
                        >
                          {v.difficulty}
                        </span>
                        <div className="mt-2 text-sm font-bold text-white">
                          {v.shortName}
                        </div>
                        <div className="mt-0.5 line-clamp-1 text-[11px] text-slate-400">
                          {v.muscles.slice(0, 2).join(', ')}
                        </div>
                      </button>
                    );
                  })}
                </div>
              </div>

              {/* Selected Variation Info & Camera Tip */}
              <div className="rounded-2xl border border-slate-800 bg-slate-900/60 p-4">
                <div className="flex items-center justify-between">
                  <h3 className="text-sm font-bold text-white">
                    {selectedVariation.name}
                  </h3>
                  <span className="text-[11px] font-medium text-emerald-400">
                    ~{selectedVariation.caloriesPerRep} kcal/tekrar
                  </span>
                </div>
                <p className="mt-1.5 text-xs leading-relaxed text-slate-400">
                  {selectedVariation.description}
                </p>
                <div className="mt-3 flex items-start gap-2 rounded-xl bg-sky-500/10 border border-sky-500/20 p-2.5 text-xs text-sky-200">
                  <Camera className="mt-0.5 h-4 w-4 shrink-0 text-sky-400" />
                  <span>{selectedVariation.cameraTip}</span>
                </div>
              </div>

              {/* Target Reps */}
              <div>
                <div className="mb-2.5 flex items-center gap-1.5">
                  <Target className="h-4 w-4 text-emerald-400" />
                  <h2 className="text-sm font-bold text-white">
                    2. Set Hedefi Belirle
                  </h2>
                </div>
                <div className="grid grid-cols-5 gap-2">
                  {TARGET_OPTIONS.map((opt) => {
                    const active = targetReps === opt.value;
                    return (
                      <button
                        key={opt.label}
                        onClick={() => setTargetReps(opt.value)}
                        className={`rounded-xl border py-2.5 text-xs font-bold transition active:scale-95 ${
                          active
                            ? 'border-emerald-400 bg-emerald-500 text-slate-950'
                            : 'border-slate-800 bg-slate-900/70 text-slate-300 hover:border-slate-700'
                        }`}
                      >
                        {opt.label}
                      </button>
                    );
                  })}
                </div>
              </div>

              {/* Quick Toggles */}
              <div className="grid grid-cols-2 gap-2.5">
                <button
                  onClick={() =>
                    handleUpdateSettings({ voiceEnabled: !settings.voiceEnabled })
                  }
                  className={`flex items-center justify-center gap-2 rounded-xl border py-2.5 text-xs font-semibold transition ${
                    settings.voiceEnabled
                      ? 'border-emerald-500/40 bg-emerald-500/10 text-emerald-300'
                      : 'border-slate-800 bg-slate-900/70 text-slate-400'
                  }`}
                >
                  {settings.voiceEnabled ? (
                    <Volume2 className="h-4 w-4" />
                  ) : (
                    <VolumeX className="h-4 w-4" />
                  )}
                  Sesli Koç: {settings.voiceEnabled ? 'Açık' : 'Kapalı'}
                </button>

                <button
                  onClick={() =>
                    handleUpdateSettings({ showSkeleton: !settings.showSkeleton })
                  }
                  className={`flex items-center justify-center gap-2 rounded-xl border py-2.5 text-xs font-semibold transition ${
                    settings.showSkeleton
                      ? 'border-sky-500/40 bg-sky-500/10 text-sky-300'
                      : 'border-slate-800 bg-slate-900/70 text-slate-400'
                  }`}
                >
                  <Eye className="h-4 w-4" />
                  İskelet: {settings.showSkeleton ? 'Açık' : 'Kapalı'}
                </button>
              </div>

              {/* Start CTA */}
              <button
                onClick={() => setIsWorkoutActive(true)}
                className="flex w-full items-center justify-center gap-2.5 rounded-2xl bg-gradient-to-r from-emerald-500 to-teal-400 py-4 text-base font-extrabold text-slate-950 shadow-xl shadow-emerald-500/25 transition hover:brightness-105 active:scale-98"
              >
                <Camera className="h-5 w-5" />
                Kamerayı Aç & Şınava Başla
              </button>
            </div>
          )}

          {activeTab === 'progress' && (
            <ProgressView
              sessions={sessions}
              settings={settings}
              onUpdateSettings={handleUpdateSettings}
              onSelectSession={setSummarySession}
              onDeleteSession={handleDeleteSession}
            />
          )}

          {activeTab === 'coach' && (
            <CoachGuideView
              sessions={sessions}
              onStartVariation={handleStartFromGuide}
            />
          )}
        </main>

        {/* Bottom Navigation Bar */}
        <nav className="safe-bottom fixed inset-x-0 bottom-0 z-30 mx-auto max-w-md border-t border-slate-800/80 bg-[#0b101d]/95 px-4 pt-2 backdrop-blur-lg">
          <div className="grid grid-cols-3 gap-1">
            <button
              onClick={() => setActiveTab('workout')}
              className={`flex flex-col items-center gap-1 rounded-xl py-1.5 text-[11px] font-semibold transition ${
                activeTab === 'workout'
                  ? 'text-emerald-400'
                  : 'text-slate-400 hover:text-slate-200'
              }`}
            >
              <Camera className="h-5 w-5" />
              Antrenman
            </button>

            <button
              onClick={() => setActiveTab('progress')}
              className={`flex flex-col items-center gap-1 rounded-xl py-1.5 text-[11px] font-semibold transition ${
                activeTab === 'progress'
                  ? 'text-emerald-400'
                  : 'text-slate-400 hover:text-slate-200'
              }`}
            >
              <TrendingUp className="h-5 w-5" />
              Gelişim
            </button>

            <button
              onClick={() => setActiveTab('coach')}
              className={`flex flex-col items-center gap-1 rounded-xl py-1.5 text-[11px] font-semibold transition ${
                activeTab === 'coach'
                  ? 'text-emerald-400'
                  : 'text-slate-400 hover:text-slate-200'
              }`}
            >
              <Sparkles className="h-5 w-5" />
              Koç & Rehber
            </button>
          </div>
        </nav>
      </div>

      {/* Live Camera Workout Fullscreen */}
      {isWorkoutActive && (
        <CameraWorkout
          variation={selectedVariation}
          targetReps={targetReps}
          settings={settings}
          onUpdateSettings={handleUpdateSettings}
          onFinish={handleWorkoutFinish}
        />
      )}

      {/* Post-Workout Summary Modal */}
      {summarySession && (
        <WorkoutSummaryModal
          session={summarySession}
          onClose={() => setSummarySession(null)}
        />
      )}
    </div>
  );
}

export default App;
