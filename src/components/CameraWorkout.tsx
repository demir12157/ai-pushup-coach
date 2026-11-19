import React, { useCallback, useEffect, useRef, useState } from 'react';
import { FilesetResolver, PoseLandmarker } from '@mediapipe/tasks-vision';
import {
  AlertTriangle,
  Camera,
  CheckCircle2,
  Eye,
  EyeOff,
  Flame,
  Play,
  RefreshCw,
  Square,
  Volume2,
  VolumeX,
  X,
} from 'lucide-react';
import type {
  AppSettings,
  LivePoseState,
  PushupVariation,
  WorkoutSession,
} from '../types';
import {
  drawPoseOverlay,
  PushupPoseAnalyzer,
} from '../utils/poseAnalyzer';
import type { NormalizedLandmark } from '../utils/poseAnalyzer';
import { voiceCoach } from '../utils/voiceCoach';

interface CameraWorkoutProps {
  variation: PushupVariation;
  targetReps: number | null;
  settings: AppSettings;
  onUpdateSettings: (patch: Partial<AppSettings>) => void;
  onFinish: (session: WorkoutSession | null) => void;
}

const INITIAL_STATE: LivePoseState = {
  phase: 'IDLE',
  reps: 0,
  elbowAngle: null,
  bodyAngle: null,
  depthPercent: 0,
  formSeverity: 'warning',
  feedbackText: 'AI modeli ve kamera hazırlanıyor...',
  activeIssues: [],
  poseDetected: false,
  activeSide: null,
  currentScore: 100,
};

export const CameraWorkout: React.FC<CameraWorkoutProps> = ({
  variation,
  targetReps,
  settings,
  onUpdateSettings,
  onFinish,
}) => {
  const videoRef = useRef<HTMLVideoElement | null>(null);
  const canvasRef = useRef<HTMLCanvasElement | null>(null);
  const landmarkerRef = useRef<PoseLandmarker | null>(null);
  const analyzerRef = useRef<PushupPoseAnalyzer>(new PushupPoseAnalyzer(variation));
  const streamRef = useRef<MediaStream | null>(null);
  const rafRef = useRef<number | null>(null);
  const lastVideoTimeRef = useRef<number>(-1);
  const startTimeRef = useRef<number>(Date.now());

  const [liveState, setLiveState] = useState<LivePoseState>(INITIAL_STATE);
  const [elapsedSec, setElapsedSec] = useState(0);
  const [status, setStatus] = useState<'loading' | 'ready' | 'error' | 'demo'>('loading');
  const [errorMsg, setErrorMsg] = useState<string>('');
  const [repPulse, setRepPulse] = useState(false);

  // Keep refs to settings for RAF loop
  const showSkeletonRef = useRef(settings.showSkeleton);
  const facingModeRef = useRef(settings.facingMode);
  useEffect(() => {
    showSkeletonRef.current = settings.showSkeleton;
  }, [settings.showSkeleton]);
  useEffect(() => {
    facingModeRef.current = settings.facingMode;
  }, [settings.facingMode]);
  useEffect(() => {
    voiceCoach.setEnabled(settings.voiceEnabled);
  }, [settings.voiceEnabled]);

  // Timer
  useEffect(() => {
    if (status !== 'ready' && status !== 'demo') return;
    startTimeRef.current = Date.now();
    const id = window.setInterval(() => {
      setElapsedSec(Math.floor((Date.now() - startTimeRef.current) / 1000));
    }, 500);
    return () => window.clearInterval(id);
  }, [status]);

  const stopCamera = useCallback(() => {
    if (rafRef.current) {
      cancelAnimationFrame(rafRef.current);
      rafRef.current = null;
    }
    if (streamRef.current) {
      streamRef.current.getTracks().forEach((t) => t.stop());
      streamRef.current = null;
    }
  }, []);

  // Initialize MediaPipe PoseLandmarker + Camera
  useEffect(() => {
    let cancelled = false;
    analyzerRef.current.reset(variation);
    setLiveState({
      ...INITIAL_STATE,
      feedbackText: 'AI Pose modeli yükleniyor...',
    });

    const init = async () => {
      try {
        setStatus('loading');

        // 1. Load PoseLandmarker if not already loaded
        if (!landmarkerRef.current) {
          const vision = await FilesetResolver.forVisionTasks('/wasm');
          try {
            landmarkerRef.current = await PoseLandmarker.createFromOptions(vision, {
              baseOptions: {
                modelAssetPath:
                  'https://storage.googleapis.com/mediapipe-models/pose_landmarker/pose_landmarker_lite/float16/1/pose_landmarker_lite.task',
                delegate: 'GPU',
              },
              runningMode: 'VIDEO',
              numPoses: 1,
              minPoseDetectionConfidence: 0.5,
              minPosePresenceConfidence: 0.5,
              minTrackingConfidence: 0.5,
            });
          } catch {
            // Fallback to CPU delegate if WebGL GPU fails
            landmarkerRef.current = await PoseLandmarker.createFromOptions(vision, {
              baseOptions: {
                modelAssetPath:
                  'https://storage.googleapis.com/mediapipe-models/pose_landmarker/pose_landmarker_lite/float16/1/pose_landmarker_lite.task',
                delegate: 'CPU',
              },
              runningMode: 'VIDEO',
              numPoses: 1,
            });
          }
        }

        if (cancelled) return;

        // 2. Request Camera
        if (!navigator.mediaDevices || !navigator.mediaDevices.getUserMedia) {
          throw new Error(
            'Tarayıcın kamera erişimini desteklemiyor veya güvenli bağlantı (HTTPS / localhost) gerekiyor.'
          );
        }

        const stream = await navigator.mediaDevices.getUserMedia({
          video: {
            facingMode: settings.facingMode,
            width: { ideal: 1280 },
            height: { ideal: 720 },
          },
          audio: false,
        });

        if (cancelled) {
          stream.getTracks().forEach((t) => t.stop());
          return;
        }

        streamRef.current = stream;
        const video = videoRef.current;
        if (video) {
          video.srcObject = stream;
          await video.play();
        }

        setStatus('ready');
        voiceCoach.speak(`${variation.name} başladı. Yan profilden pozisyon al.`, {
          priority: true,
        });

        // 3. Detection Loop
        const loop = () => {
          if (cancelled) return;
          const vid = videoRef.current;
          const canvas = canvasRef.current;
          const landmarker = landmarkerRef.current;

          if (vid && canvas && landmarker && vid.readyState >= 2) {
            if (canvas.width !== vid.videoWidth || canvas.height !== vid.videoHeight) {
              canvas.width = vid.videoWidth;
              canvas.height = vid.videoHeight;
            }

            if (vid.currentTime !== lastVideoTimeRef.current) {
              lastVideoTimeRef.current = vid.currentTime;
              const results = landmarker.detectForVideo(vid, performance.now());
              const landmarks = (results.landmarks?.[0] ?? null) as
                | NormalizedLandmark[]
                | null;

              const analysis = analyzerRef.current.analyze(landmarks);
              setLiveState(analysis.state);

              if (analysis.repCompleted) {
                setRepPulse(true);
                window.setTimeout(() => setRepPulse(false), 320);
                voiceCoach.announceRep(
                  analysis.state.reps,
                  targetReps,
                  analysis.repCompleted.issues.length === 0
                );
              } else if (analysis.voiceFeedback) {
                voiceCoach.speak(analysis.voiceFeedback);
              }

              const ctx = canvas.getContext('2d');
              if (ctx) {
                if (showSkeletonRef.current && landmarks) {
                  drawPoseOverlay(
                    ctx,
                    landmarks,
                    canvas.width,
                    canvas.height,
                    analysis.state,
                    facingModeRef.current === 'user'
                  );
                } else {
                  ctx.clearRect(0, 0, canvas.width, canvas.height);
                }
              }
            }
          }

          rafRef.current = requestAnimationFrame(loop);
        };

        rafRef.current = requestAnimationFrame(loop);
      } catch (err) {
        if (cancelled) return;
        const message =
          err instanceof Error
            ? err.message
            : 'Kameraya erişilemedi. Lütfen tarayıcı izinlerini kontrol et.';
        setErrorMsg(message);
        setStatus('error');
      }
    };

    init();

    return () => {
      cancelled = true;
      stopCamera();
      voiceCoach.stop();
    };
  }, [variation, settings.facingMode, targetReps, stopCamera]);

  // Demo simulation mode (for testing without a camera)
  const startDemoMode = () => {
    stopCamera();
    analyzerRef.current.reset(variation);
    setStatus('demo');
    voiceCoach.speak('Simülasyon modu başladı. Örnek şınav analizi gösteriliyor.', {
      priority: true,
    });
  };

  useEffect(() => {
    if (status !== 'demo') return;
    let tick = 0;
    const interval = window.setInterval(() => {
      tick += 1;
      // Oscillate elbow bend in synthetic landmarks
      const cycle = (tick % 40) / 40; // 0..1
      const depth = Math.sin(cycle * Math.PI); // 0 -> 1 -> 0
      const sag = tick > 60 && tick < 100 ? 0.09 : 0; // simulate a hip sag warning on rep 2

      const shoulder = { x: 0.35, y: 0.45 + depth * 0.16, visibility: 0.99 };
      const elbow = {
        x: 0.33 - depth * 0.08,
        y: 0.58 + depth * 0.06,
        visibility: 0.99,
      };
      const wrist = { x: 0.35, y: 0.72, visibility: 0.99 };
      const hip = {
        x: 0.56,
        y: 0.52 + depth * 0.1 + sag,
        visibility: 0.99,
      };
      const knee = { x: 0.72, y: 0.62 + depth * 0.04, visibility: 0.99 };
      const ankle = { x: 0.85, y: 0.71, visibility: 0.99 };
      const ear = { x: 0.27, y: 0.41 + depth * 0.15, visibility: 0.99 };

      const synthetic: NormalizedLandmark[] = Array.from({ length: 33 }, () => ({
        x: 0.5,
        y: 0.5,
        visibility: 0.1,
      }));
      synthetic[7] = ear;
      synthetic[11] = shoulder;
      synthetic[13] = elbow;
      synthetic[15] = wrist;
      synthetic[23] = hip;
      synthetic[25] = knee;
      synthetic[27] = ankle;

      const analysis = analyzerRef.current.analyze(synthetic);
      setLiveState(analysis.state);

      if (analysis.repCompleted) {
        setRepPulse(true);
        window.setTimeout(() => setRepPulse(false), 320);
        voiceCoach.announceRep(
          analysis.state.reps,
          targetReps,
          analysis.repCompleted.issues.length === 0
        );
      } else if (analysis.voiceFeedback) {
        voiceCoach.speak(analysis.voiceFeedback);
      }

      const canvas = canvasRef.current;
      if (canvas) {
        if (canvas.width !== 640 || canvas.height !== 480) {
          canvas.width = 640;
          canvas.height = 480;
        }
        const ctx = canvas.getContext('2d');
        if (ctx) {
          drawPoseOverlay(ctx, synthetic, canvas.width, canvas.height, analysis.state, false);
        }
      }
    }, 65);

    return () => window.clearInterval(interval);
  }, [status, targetReps]);

  const handleFinishWorkout = () => {
    stopCamera();
    voiceCoach.stop();
    const repRecords = analyzerRef.current.getRepRecords();
    if (repRecords.length === 0) {
      onFinish(null);
      return;
    }

    const session: WorkoutSession = {
      id: `${Date.now()}-${Math.random().toString(36).slice(2, 7)}`,
      date: new Date().toISOString(),
      variationId: variation.id,
      reps: repRecords.length,
      targetReps,
      durationSec: Math.max(1, elapsedSec),
      avgFormScore: analyzerRef.current.getAverageScore(),
      calories: Math.round(repRecords.length * variation.caloriesPerRep * 10) / 10,
      issueCounts: analyzerRef.current.getIssueCounts(),
      repRecords,
    };

    voiceCoach.playCompleteChime();
    onFinish(session);
  };

  const formatTime = (sec: number) => {
    const m = Math.floor(sec / 60);
    const s = sec % 60;
    return `${String(m).padStart(2, '0')}:${String(s).padStart(2, '0')}`;
  };

  const severityStyles =
    liveState.formSeverity === 'error'
      ? 'bg-red-500/20 border-red-500/60 text-red-200'
      : liveState.formSeverity === 'warning'
      ? 'bg-amber-500/20 border-amber-500/60 text-amber-100'
      : 'bg-emerald-500/20 border-emerald-500/60 text-emerald-100';

  return (
    <div className="fixed inset-0 z-50 flex flex-col bg-[#060911]">
      {/* Video + Canvas Layer */}
      <div className="relative flex-1 overflow-hidden">
        <video
          ref={videoRef}
          playsInline
          muted
          className={`h-full w-full object-cover ${
            settings.facingMode === 'user' ? '-scale-x-100' : ''
          } ${status === 'demo' ? 'hidden' : ''}`}
        />

        {status === 'demo' && (
          <div className="absolute inset-0 flex flex-col items-center justify-center bg-gradient-to-b from-slate-900 via-[#0b1222] to-slate-950">
            <div className="mb-2 rounded-full border border-sky-500/40 bg-sky-500/15 px-3 py-1 text-xs font-medium text-sky-300">
              Simülasyon Modu Aktif (Örnek İskelet)
            </div>
          </div>
        )}

        <canvas
          ref={canvasRef}
          className="pointer-events-none absolute inset-0 h-full w-full object-cover"
        />

        {/* Top Bar Overlay */}
        <div className="safe-top absolute inset-x-0 top-0 flex items-center justify-between bg-gradient-to-b from-black/80 via-black/40 to-transparent px-4 pb-6 pt-3">
          <div className="flex items-center gap-2.5">
            <button
              onClick={() => {
                stopCamera();
                voiceCoach.stop();
                onFinish(null);
              }}
              className="flex h-10 w-10 items-center justify-center rounded-full bg-slate-900/75 text-slate-200 backdrop-blur-md active:scale-95"
              aria-label="Kapat"
            >
              <X className="h-5 w-5" />
            </button>
            <div>
              <div className="text-sm font-semibold text-white">{variation.name}</div>
              <div className="flex items-center gap-2 text-xs text-slate-300 tabular-nums">
                <span>{formatTime(elapsedSec)}</span>
                <span>•</span>
                <span className="text-emerald-400">Form %{liveState.currentScore}</span>
              </div>
            </div>
          </div>

          <div className="flex items-center gap-2">
            <button
              onClick={() =>
                onUpdateSettings({ voiceEnabled: !settings.voiceEnabled })
              }
              className={`flex h-10 w-10 items-center justify-center rounded-full backdrop-blur-md transition active:scale-95 ${
                settings.voiceEnabled
                  ? 'bg-emerald-500/25 text-emerald-300 border border-emerald-500/40'
                  : 'bg-slate-900/75 text-slate-400'
              }`}
              title="Sesli Koç"
            >
              {settings.voiceEnabled ? (
                <Volume2 className="h-5 w-5" />
              ) : (
                <VolumeX className="h-5 w-5" />
              )}
            </button>

            <button
              onClick={() =>
                onUpdateSettings({ showSkeleton: !settings.showSkeleton })
              }
              className={`flex h-10 w-10 items-center justify-center rounded-full backdrop-blur-md transition active:scale-95 ${
                settings.showSkeleton
                  ? 'bg-sky-500/25 text-sky-300 border border-sky-500/40'
                  : 'bg-slate-900/75 text-slate-400'
              }`}
              title="İskelet Çizimi"
            >
              {settings.showSkeleton ? (
                <Eye className="h-5 w-5" />
              ) : (
                <EyeOff className="h-5 w-5" />
              )}
            </button>

            {status === 'ready' && (
              <button
                onClick={() =>
                  onUpdateSettings({
                    facingMode:
                      settings.facingMode === 'user' ? 'environment' : 'user',
                  })
                }
                className="flex h-10 w-10 items-center justify-center rounded-full bg-slate-900/75 text-slate-200 backdrop-blur-md active:scale-95"
                title="Kamerayı Çevir"
              >
                <RefreshCw className="h-5 w-5" />
              </button>
            )}
          </div>
        </div>

        {/* Loading Overlay */}
        {status === 'loading' && (
          <div className="absolute inset-0 flex flex-col items-center justify-center bg-slate-950/90 px-6 text-center backdrop-blur-sm">
            <div className="mb-4 h-12 w-12 animate-spin rounded-full border-4 border-emerald-500/30 border-t-emerald-400" />
            <h3 className="text-lg font-semibold text-white">AI Kamera Hazırlanıyor</h3>
            <p className="mt-2 max-w-xs text-sm text-slate-400">
              MediaPipe vücut takip modeli yükleniyor. İlk açılışta birkaç saniye sürebilir.
            </p>
          </div>
        )}

        {/* Camera Error Fallback */}
        {status === 'error' && (
          <div className="absolute inset-0 flex flex-col items-center justify-center bg-slate-950/95 px-6 text-center">
            <div className="mb-4 flex h-16 w-16 items-center justify-center rounded-2xl bg-red-500/15 text-red-400">
              <Camera className="h-8 w-8" />
            </div>
            <h3 className="text-lg font-bold text-white">Kamera Açılamadı</h3>
            <p className="mt-2 max-w-sm text-sm text-slate-400">{errorMsg}</p>
            <div className="mt-6 flex w-full max-w-xs flex-col gap-3">
              <button
                onClick={startDemoMode}
                className="flex w-full items-center justify-center gap-2 rounded-xl bg-emerald-500 px-4 py-3.5 text-sm font-semibold text-slate-950 transition active:scale-98"
              >
                <Play className="h-4 w-4 fill-current" />
                Simülasyon Moduyla Test Et
              </button>
              <button
                onClick={() => onFinish(null)}
                className="w-full rounded-xl border border-slate-700 bg-slate-900 px-4 py-3 text-sm font-medium text-slate-300"
              >
                Geri Dön
              </button>
            </div>
          </div>
        )}

        {/* Right Side Vertical Depth Gauge */}
        {(status === 'ready' || status === 'demo') && (
          <div className="absolute right-4 top-1/2 -translate-y-1/2 flex flex-col items-center gap-2">
            <span className="text-[10px] font-semibold uppercase tracking-wider text-slate-300 drop-shadow">
              Derinlik
            </span>
            <div className="relative h-36 w-3 overflow-hidden rounded-full bg-slate-900/75 border border-white/15 backdrop-blur-sm">
              <div
                className="absolute bottom-0 inset-x-0 rounded-full bg-gradient-to-t from-emerald-400 to-sky-400 transition-all duration-100"
                style={{ height: `${liveState.depthPercent}%` }}
              />
            </div>
            <span className="text-xs font-bold text-white tabular-nums drop-shadow">
              {liveState.elbowAngle !== null ? `${liveState.elbowAngle}°` : '--'}
            </span>
          </div>
        )}

        {/* Bottom HUD Overlay */}
        {(status === 'ready' || status === 'demo') && (
          <div className="safe-bottom absolute inset-x-0 bottom-0 bg-gradient-to-t from-black/90 via-black/65 to-transparent px-4 pb-5 pt-10">
            {/* Live Coaching Feedback Banner */}
            <div
              className={`mb-4 flex items-center gap-2.5 rounded-2xl border px-4 py-3 backdrop-blur-md transition-colors ${severityStyles}`}
            >
              {liveState.formSeverity === 'good' ? (
                <CheckCircle2 className="h-5 w-5 shrink-0 text-emerald-400" />
              ) : (
                <AlertTriangle
                  className={`h-5 w-5 shrink-0 ${
                    liveState.formSeverity === 'error'
                      ? 'text-red-400'
                      : 'text-amber-400'
                  }`}
                />
              )}
              <p className="text-sm font-medium leading-snug">
                {liveState.feedbackText}
              </p>
            </div>

            {/* Counter + Action Row */}
            <div className="flex items-center justify-between gap-4">
              <div className="flex items-baseline gap-2.5">
                <div
                  className={`text-6xl font-black tracking-tight text-white tabular-nums transition-transform duration-200 ${
                    repPulse ? 'scale-125 text-emerald-400' : 'scale-100'
                  }`}
                >
                  {liveState.reps}
                </div>
                <div className="flex flex-col">
                  <span className="text-xs font-semibold uppercase tracking-wider text-slate-400">
                    {targetReps ? `/ ${targetReps} HEDEF` : 'TEKRAR'}
                  </span>
                  <span className="mt-0.5 flex items-center gap-1 text-xs font-medium text-emerald-400">
                    <Flame className="h-3.5 w-3.5" />
                    {Math.round(liveState.reps * variation.caloriesPerRep * 10) / 10} kcal
                  </span>
                </div>
              </div>

              <button
                onClick={handleFinishWorkout}
                className="flex items-center gap-2 rounded-2xl bg-emerald-500 px-6 py-4 text-sm font-bold text-slate-950 shadow-lg shadow-emerald-500/25 transition active:scale-95"
              >
                <Square className="h-4 w-4 fill-current" />
                Bitir & Kaydet
              </button>
            </div>
          </div>
        )}
      </div>
    </div>
  );
};
