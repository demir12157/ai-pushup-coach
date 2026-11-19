export type PushupVariationId =
  | 'standard'
  | 'wide'
  | 'diamond'
  | 'knee'
  | 'incline'
  | 'decline';

export type FormIssueType =
  | 'hip_sag'
  | 'hip_pike'
  | 'shallow_depth'
  | 'incomplete_lockout'
  | 'head_drop'
  | 'too_fast';

export type FormSeverity = 'good' | 'warning' | 'error';

export interface FormIssueMeta {
  type: FormIssueType;
  label: string;
  liveCue: string;
  voiceCue: string;
  recommendation: string;
}

export interface PushupVariation {
  id: PushupVariationId;
  name: string;
  shortName: string;
  difficulty: 'Başlangıç' | 'Orta' | 'İleri';
  muscles: string[];
  description: string;
  cameraTip: string;
  caloriesPerRep: number;
  // Pose analyzer thresholds (in degrees)
  thresholds: {
    downAngle: number; // Elbow angle below this = bottom reached
    upAngle: number; // Elbow angle above this = top lockout
    minBodyAngle: number; // Below this = hip sag or pike depending on sign
    maxBodyDeviation: number; // Allowed deviation from 180° straight line
    useKneeLine?: boolean; // True for knee pushups (shoulder-hip-knee)
  };
  checklist: string[];
  commonMistakes: string[];
}

export interface RepRecord {
  repNumber: number;
  minElbowAngle: number;
  maxBodyDeviation: number;
  durationMs: number;
  score: number;
  issues: FormIssueType[];
}

export interface WorkoutSession {
  id: string;
  date: string; // ISO string
  variationId: PushupVariationId;
  reps: number;
  targetReps: number | null;
  durationSec: number;
  avgFormScore: number;
  calories: number;
  issueCounts: Partial<Record<FormIssueType, number>>;
  repRecords: RepRecord[];
}

export interface LivePoseState {
  phase: 'IDLE' | 'UP' | 'DOWN';
  reps: number;
  elbowAngle: number | null;
  bodyAngle: number | null;
  depthPercent: number; // 0 (top) to 100 (bottom)
  formSeverity: FormSeverity;
  feedbackText: string;
  activeIssues: FormIssueType[];
  poseDetected: boolean;
  activeSide: 'left' | 'right' | null;
  currentScore: number;
}

export interface AppSettings {
  voiceEnabled: boolean;
  showSkeleton: boolean;
  facingMode: 'user' | 'environment';
  dailyGoal: number;
}
