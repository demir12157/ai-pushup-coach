import { FORM_ISSUES } from '../data/variations';
import type {
  FormIssueType,
  FormSeverity,
  LivePoseState,
  PushupVariation,
  RepRecord,
} from '../types';

export interface NormalizedLandmark {
  x: number;
  y: number;
  z?: number;
  visibility?: number;
}

export interface AnalyzeResult {
  state: LivePoseState;
  repCompleted?: RepRecord;
  voiceFeedback?: string;
}

const EMA_ALPHA = 0.35;

export function calculateAngle(
  a: NormalizedLandmark,
  b: NormalizedLandmark,
  c: NormalizedLandmark
): number {
  const radians =
    Math.atan2(c.y - b.y, c.x - b.x) - Math.atan2(a.y - b.y, a.x - b.x);
  let angle = Math.abs((radians * 180) / Math.PI);
  if (angle > 180) {
    angle = 360 - angle;
  }
  return angle;
}

export class PushupPoseAnalyzer {
  private variation: PushupVariation;
  private phase: 'IDLE' | 'UP' | 'DOWN' = 'IDLE';
  private reps = 0;
  private repRecords: RepRecord[] = [];
  private issueCounts: Partial<Record<FormIssueType, number>> = {};

  // Smoothed angles
  private smoothedElbow: number | null = null;
  private smoothedBody: number | null = null;

  // Per-rep trackers
  private repStartTime = 0;
  private repMinElbow = 180;
  private repMaxBodyDev = 0;
  private repIssues = new Set<FormIssueType>();
  private attemptedDescent = false;
  private lastShallowAlertAt = 0;

  constructor(variation: PushupVariation) {
    this.variation = variation;
  }

  public reset(variation?: PushupVariation) {
    if (variation) {
      this.variation = variation;
    }
    this.phase = 'IDLE';
    this.reps = 0;
    this.repRecords = [];
    this.issueCounts = {};
    this.smoothedElbow = null;
    this.smoothedBody = null;
    this.repStartTime = 0;
    this.repMinElbow = 180;
    this.repMaxBodyDev = 0;
    this.repIssues.clear();
    this.attemptedDescent = false;
  }

  public getRepRecords(): RepRecord[] {
    return [...this.repRecords];
  }

  public getIssueCounts(): Partial<Record<FormIssueType, number>> {
    return { ...this.issueCounts };
  }

  public getAverageScore(): number {
    if (this.repRecords.length === 0) return 0;
    const sum = this.repRecords.reduce((acc, r) => acc + r.score, 0);
    return Math.round(sum / this.repRecords.length);
  }

  public analyze(landmarks: NormalizedLandmark[] | null, now = Date.now()): AnalyzeResult {
    if (!landmarks || landmarks.length < 29) {
      return {
        state: {
          phase: this.phase,
          reps: this.reps,
          elbowAngle: this.smoothedElbow ? Math.round(this.smoothedElbow) : null,
          bodyAngle: this.smoothedBody ? Math.round(this.smoothedBody) : null,
          depthPercent: 0,
          formSeverity: 'warning',
          feedbackText: 'Kameraya yan profilden tüm vücudun görünecek şekilde geç',
          activeIssues: [],
          poseDetected: false,
          activeSide: null,
          currentScore: this.getAverageScore() || 100,
        },
      };
    }

    // Pick better visible side
    const leftIndices = [11, 13, 15, 23];
    const rightIndices = [12, 14, 16, 24];
    const leftVis =
      leftIndices.reduce((acc, i) => acc + (landmarks[i]?.visibility ?? 0.5), 0) /
      leftIndices.length;
    const rightVis =
      rightIndices.reduce((acc, i) => acc + (landmarks[i]?.visibility ?? 0.5), 0) /
      rightIndices.length;

    const side: 'left' | 'right' = leftVis >= rightVis ? 'left' : 'right';
    const bestVis = Math.max(leftVis, rightVis);

    if (bestVis < 0.35) {
      return {
        state: {
          phase: this.phase,
          reps: this.reps,
          elbowAngle: null,
          bodyAngle: null,
          depthPercent: 0,
          formSeverity: 'warning',
          feedbackText: 'Vücudun net görünmüyor, kameradan biraz uzaklaş',
          activeIssues: [],
          poseDetected: false,
          activeSide: null,
          currentScore: this.getAverageScore() || 100,
        },
      };
    }

    const shoulder = landmarks[side === 'left' ? 11 : 12];
    const elbow = landmarks[side === 'left' ? 13 : 14];
    const wrist = landmarks[side === 'left' ? 15 : 16];
    const hip = landmarks[side === 'left' ? 23 : 24];
    const knee = landmarks[side === 'left' ? 25 : 26];
    const ankle = landmarks[side === 'left' ? 27 : 28];
    const ear = landmarks[side === 'left' ? 7 : 8];

    // Determine lower anchor (knee for knee pushups or if ankle isn't visible)
    const useKnee =
      this.variation.thresholds.useKneeLine || (ankle.visibility ?? 0) < 0.35;
    const lower = useKnee ? knee : ankle;

    // Raw angles
    const rawElbow = calculateAngle(shoulder, elbow, wrist);
    const rawBody = calculateAngle(shoulder, hip, lower);

    // Smooth angles
    this.smoothedElbow =
      this.smoothedElbow === null
        ? rawElbow
        : this.smoothedElbow * (1 - EMA_ALPHA) + rawElbow * EMA_ALPHA;
    this.smoothedBody =
      this.smoothedBody === null
        ? rawBody
        : this.smoothedBody * (1 - EMA_ALPHA) + rawBody * EMA_ALPHA;

    const elbowAngle = this.smoothedElbow;
    const bodyAngle = this.smoothedBody;

    // Check if user is in pushup posture (not standing straight upright)
    const dx = Math.abs(shoulder.x - hip.x);
    const dy = Math.abs(shoulder.y - hip.y);
    const maxVerticalRatio = this.variation.id === 'incline' ? 2.4 : 1.5;
    const isPushupPosture = dx > 0.04 && dy / (dx + 0.001) < maxVerticalRatio;

    if (!isPushupPosture && this.phase === 'IDLE') {
      return {
        state: {
          phase: 'IDLE',
          reps: this.reps,
          elbowAngle: Math.round(elbowAngle),
          bodyAngle: Math.round(bodyAngle),
          depthPercent: 0,
          formSeverity: 'warning',
          feedbackText: 'Şınav pozisyonuna geç (plank duruşu)',
          activeIssues: [],
          poseDetected: true,
          activeSide: side,
          currentScore: this.getAverageScore() || 100,
        },
      };
    }

    // Form analysis
    const activeIssues: FormIssueType[] = [];
    const { downAngle, upAngle, minBodyAngle } = this.variation.thresholds;
    const bodyDeviation = Math.max(0, 180 - bodyAngle);

    // Check hip sag vs hip pike using line from shoulder to lower
    if (bodyAngle < minBodyAngle) {
      const spanX = lower.x - shoulder.x;
      if (Math.abs(spanX) > 0.05) {
        const t = Math.min(1, Math.max(0, (hip.x - shoulder.x) / spanX));
        const expectedHipY = shoulder.y + t * (lower.y - shoulder.y);
        if (hip.y > expectedHipY + 0.015) {
          activeIssues.push('hip_sag');
        } else if (hip.y < expectedHipY - 0.015) {
          activeIssues.push('hip_pike');
        }
      }
    }

    // Check head drop (ear drops below shoulder-hip line significantly)
    if ((ear.visibility ?? 0) > 0.4) {
      const torsoLen = Math.hypot(shoulder.x - hip.x, shoulder.y - hip.y);
      if (torsoLen > 0.08 && ear.y > shoulder.y + torsoLen * 0.28) {
        activeIssues.push('head_drop');
      }
    }

    // Depth percent (0% at upAngle, 100% at downAngle)
    const depthPercent = Math.round(
      Math.min(100, Math.max(0, ((upAngle - elbowAngle) / (upAngle - downAngle)) * 100))
    );

    let repCompleted: RepRecord | undefined;
    let voiceFeedback: string | undefined;
    let feedbackText = 'Harika form! Devam et';
    let formSeverity: FormSeverity = 'good';

    // Track per-rep stats while active
    if (this.phase === 'UP' || this.phase === 'DOWN') {
      if (elbowAngle < this.repMinElbow) {
        this.repMinElbow = elbowAngle;
      }
      if (bodyDeviation > this.repMaxBodyDev) {
        this.repMaxBodyDev = bodyDeviation;
      }
      activeIssues.forEach((issue) => this.repIssues.add(issue));
    }

    // State transitions
    if (this.phase === 'IDLE') {
      if (elbowAngle >= upAngle - 12 && isPushupPosture) {
        this.phase = 'UP';
        this.repStartTime = now;
        this.repMinElbow = elbowAngle;
        this.repMaxBodyDev = 0;
        this.repIssues.clear();
        feedbackText = 'Hazır! Kontrollü şekilde aşağı in';
      } else {
        feedbackText = 'Kollarını düzleştirip başlangıç pozisyonuna geç';
        formSeverity = 'warning';
      }
    } else if (this.phase === 'UP') {
      if (elbowAngle < upAngle - 22) {
        if (!this.attemptedDescent) {
          this.attemptedDescent = true;
          this.repStartTime = now;
        }
      }

      if (elbowAngle <= downAngle) {
        this.phase = 'DOWN';
        feedbackText = 'Güzel derinlik! Şimdi yukarı it';
      } else if (
        this.attemptedDescent &&
        this.repMinElbow < upAngle - 25 &&
        this.repMinElbow > downAngle + 8 &&
        elbowAngle >= upAngle - 4
      ) {
        // User went part-way down and came back up without hitting depth
        this.attemptedDescent = false;
        this.repMinElbow = 180;
        if (now - this.lastShallowAlertAt > 3000) {
          this.lastShallowAlertAt = now;
          this.issueCounts.shallow_depth = (this.issueCounts.shallow_depth ?? 0) + 1;
          activeIssues.push('shallow_depth');
          voiceFeedback = FORM_ISSUES.shallow_depth.voiceCue;
        }
      }
    } else if (this.phase === 'DOWN') {
      if (elbowAngle >= upAngle) {
        // Completed a valid rep!
        this.reps += 1;
        const durationMs = this.repStartTime > 0 ? now - this.repStartTime : 1500;
        if (durationMs < 750) {
          this.repIssues.add('too_fast');
        }

        const repIssueList = Array.from(this.repIssues);
        repIssueList.forEach((issue) => {
          this.issueCounts[issue] = (this.issueCounts[issue] ?? 0) + 1;
        });

        let score = 100;
        if (this.repIssues.has('hip_sag')) score -= 20;
        if (this.repIssues.has('hip_pike')) score -= 16;
        if (this.repIssues.has('head_drop')) score -= 10;
        if (this.repIssues.has('too_fast')) score -= 12;
        if (this.repMinElbow > downAngle - 4) score -= 5;
        score = Math.max(40, Math.min(100, score));

        repCompleted = {
          repNumber: this.reps,
          minElbowAngle: Math.round(this.repMinElbow),
          maxBodyDeviation: Math.round(this.repMaxBodyDev),
          durationMs,
          score,
          issues: repIssueList,
        };
        this.repRecords.push(repCompleted);

        // Reset for next rep
        this.phase = 'UP';
        this.repStartTime = now;
        this.repMinElbow = 180;
        this.repMaxBodyDev = 0;
        this.repIssues.clear();
        this.attemptedDescent = false;
      }
    }

    // Prioritize form feedback message
    if (activeIssues.length > 0) {
      const primary = activeIssues[0];
      feedbackText = FORM_ISSUES[primary].liveCue;
      formSeverity = primary === 'hip_sag' || primary === 'hip_pike' ? 'error' : 'warning';
      if (!voiceFeedback) {
        voiceFeedback = FORM_ISSUES[primary].voiceCue;
      }
    } else if (this.phase === 'DOWN') {
      feedbackText = 'Süper derinlik! Şimdi güçlü it';
      formSeverity = 'good';
    } else if (this.phase === 'UP' && this.reps > 0) {
      feedbackText = 'Formun çok iyi, tempoyu koru!';
      formSeverity = 'good';
    }

    return {
      state: {
        phase: this.phase,
        reps: this.reps,
        elbowAngle: Math.round(elbowAngle),
        bodyAngle: Math.round(bodyAngle),
        depthPercent,
        formSeverity,
        feedbackText,
        activeIssues,
        poseDetected: true,
        activeSide: side,
        currentScore: this.getAverageScore() || 100,
      },
      repCompleted,
      voiceFeedback,
    };
  }
}

// Skeleton connections for upper + full body pushup view
const SKELETON_CONNECTIONS: [number, number][] = [
  [11, 13],
  [13, 15],
  [12, 14],
  [14, 16],
  [11, 12],
  [11, 23],
  [12, 24],
  [23, 24],
  [23, 25],
  [25, 27],
  [24, 26],
  [26, 28],
];

export function drawPoseOverlay(
  ctx: CanvasRenderingContext2D,
  landmarks: NormalizedLandmark[],
  width: number,
  height: number,
  state: LivePoseState,
  mirrored: boolean
) {
  ctx.clearRect(0, 0, width, height);
  if (!landmarks || landmarks.length < 29) return;

  const toX = (normX: number) => (mirrored ? (1 - normX) * width : normX * width);
  const toY = (normY: number) => normY * height;

  const strokeColor =
    state.formSeverity === 'error'
      ? '#ef4444'
      : state.formSeverity === 'warning'
      ? '#f59e0b'
      : '#10b981';

  // Draw skeleton lines
  ctx.lineWidth = 4;
  ctx.lineCap = 'round';
  ctx.strokeStyle = strokeColor;
  ctx.shadowColor = strokeColor;
  ctx.shadowBlur = 10;

  for (const [startIdx, endIdx] of SKELETON_CONNECTIONS) {
    const a = landmarks[startIdx];
    const b = landmarks[endIdx];
    if (!a || !b) continue;
    if ((a.visibility ?? 0.5) < 0.3 || (b.visibility ?? 0.5) < 0.3) continue;

    ctx.beginPath();
    ctx.moveTo(toX(a.x), toY(a.y));
    ctx.lineTo(toX(b.x), toY(b.y));
    ctx.stroke();
  }

  ctx.shadowBlur = 0;

  // Draw key joint dots
  const activeKeyPoints =
    state.activeSide === 'right' ? [12, 14, 16, 24, 26, 28] : [11, 13, 15, 23, 25, 27];

  for (const idx of activeKeyPoints) {
    const lm = landmarks[idx];
    if (!lm || (lm.visibility ?? 0.5) < 0.3) continue;

    const x = toX(lm.x);
    const y = toY(lm.y);
    ctx.beginPath();
    ctx.arc(x, y, 6, 0, Math.PI * 2);
    ctx.fillStyle = '#ffffff';
    ctx.fill();
    ctx.lineWidth = 2.5;
    ctx.strokeStyle = strokeColor;
    ctx.stroke();
  }

  // Draw angle badges at active elbow and hip
  const elbowIdx = state.activeSide === 'right' ? 14 : 13;
  const hipIdx = state.activeSide === 'right' ? 24 : 23;

  const drawAngleBadge = (idx: number, value: number | null, label: string) => {
    if (value === null) return;
    const lm = landmarks[idx];
    if (!lm || (lm.visibility ?? 0.5) < 0.35) return;

    const x = toX(lm.x) + 12;
    const y = toY(lm.y) - 10;
    const text = `${label} ${value}°`;
    ctx.font = '600 12px Inter, system-ui, sans-serif';
    const textWidth = ctx.measureText(text).width;

    ctx.fillStyle = 'rgba(9, 13, 22, 0.8)';
    ctx.beginPath();
    ctx.roundRect(x - 6, y - 14, textWidth + 12, 20, 6);
    ctx.fill();
    ctx.strokeStyle = strokeColor;
    ctx.lineWidth = 1.5;
    ctx.stroke();

    ctx.fillStyle = '#f8fafc';
    ctx.fillText(text, x, y);
  };

  drawAngleBadge(elbowIdx, state.elbowAngle, 'Dirsek');
  drawAngleBadge(hipIdx, state.bodyAngle, 'Gövde');
}
