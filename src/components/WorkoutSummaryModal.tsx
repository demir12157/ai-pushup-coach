import React from 'react';
import {
  AlertTriangle,
  Award,
  CheckCircle2,
  Clock,
  Flame,
  Sparkles,
  X,
} from 'lucide-react';
import { FORM_ISSUES, getVariationById } from '../data/variations';
import type { FormIssueType, WorkoutSession } from '../types';

interface WorkoutSummaryModalProps {
  session: WorkoutSession;
  onClose: () => void;
}

export const WorkoutSummaryModal: React.FC<WorkoutSummaryModalProps> = ({
  session,
  onClose,
}) => {
  const variation = getVariationById(session.variationId);
  const issuesList = (
    Object.entries(session.issueCounts) as [FormIssueType, number][]
  )
    .filter(([, count]) => count > 0)
    .sort((a, b) => b[1] - a[1]);

  const scoreColor =
    session.avgFormScore >= 85
      ? 'text-emerald-400'
      : session.avgFormScore >= 70
      ? 'text-amber-400'
      : 'text-red-400';

  const scoreLabel =
    session.avgFormScore >= 90
      ? 'Kusursuza Yakın Form!'
      : session.avgFormScore >= 78
      ? 'İyi Form, Küçük Dokunuşlar Var'
      : 'Forma Odaklanalım';

  const formatDuration = (sec: number) => {
    const m = Math.floor(sec / 60);
    const s = sec % 60;
    return m > 0 ? `${m}dk ${s}sn` : `${s}sn`;
  };

  return (
    <div className="fixed inset-0 z-50 flex items-end sm:items-center justify-center bg-black/80 p-0 sm:p-4 backdrop-blur-sm">
      <div className="max-h-[92vh] w-full max-w-md overflow-y-auto rounded-t-3xl sm:rounded-3xl border border-slate-800 bg-[#0f1624] p-5 shadow-2xl">
        {/* Header */}
        <div className="flex items-start justify-between">
          <div>
            <span className="inline-flex items-center gap-1.5 rounded-full bg-emerald-500/15 px-3 py-1 text-xs font-semibold text-emerald-400">
              <Award className="h-3.5 w-3.5" />
              Antrenman Tamamlandı
            </span>
            <h2 className="mt-2 text-2xl font-bold text-white">{variation.name}</h2>
          </div>
          <button
            onClick={onClose}
            className="flex h-9 w-9 items-center justify-center rounded-full bg-slate-800 text-slate-400 hover:text-white"
          >
            <X className="h-5 w-5" />
          </button>
        </div>

        {/* Top Stats Grid */}
        <div className="mt-5 grid grid-cols-3 gap-2.5">
          <div className="rounded-2xl border border-slate-800 bg-slate-900/70 p-3.5 text-center">
            <div className="text-3xl font-black text-white tabular-nums">
              {session.reps}
            </div>
            <div className="mt-1 text-[11px] font-medium text-slate-400">Tekrar</div>
          </div>

          <div className="rounded-2xl border border-slate-800 bg-slate-900/70 p-3.5 text-center">
            <div className={`text-3xl font-black tabular-nums ${scoreColor}`}>
              {session.avgFormScore}
            </div>
            <div className="mt-1 text-[11px] font-medium text-slate-400">
              Form Puanı
            </div>
          </div>

          <div className="rounded-2xl border border-slate-800 bg-slate-900/70 p-3.5 text-center">
            <div className="flex items-center justify-center gap-1 text-xl font-bold text-sky-400 tabular-nums pt-1.5">
              <Clock className="h-4 w-4" />
              {formatDuration(session.durationSec)}
            </div>
            <div className="mt-1.5 text-[11px] font-medium text-slate-400">
              Süre • {session.calories} kcal
            </div>
          </div>
        </div>

        {/* Form Verdict */}
        <div className="mt-4 rounded-2xl border border-slate-800 bg-slate-900/50 p-4">
          <div className="flex items-center gap-2">
            <Sparkles className="h-4 w-4 text-emerald-400" />
            <span className="text-sm font-semibold text-white">{scoreLabel}</span>
          </div>

          {issuesList.length === 0 ? (
            <div className="mt-3 flex items-start gap-2.5 rounded-xl bg-emerald-500/10 p-3 text-sm text-emerald-200">
              <CheckCircle2 className="mt-0.5 h-4 w-4 shrink-0 text-emerald-400" />
              <p>
                Tebrikler! Bu sette belirgin bir form bozukluğu tespit edilmedi.
                Bir sonraki antrenmanda tekrar sayını artırabilir veya daha zor bir
                varyasyon deneyebilirsin.
              </p>
            </div>
          ) : (
            <div className="mt-3 space-y-3">
              <p className="text-xs text-slate-400">
                Kamera analizine göre geliştirmen gereken noktalar ve koç önerileri:
              </p>
              {issuesList.map(([issueType, count]) => {
                const meta = FORM_ISSUES[issueType];
                return (
                  <div
                    key={issueType}
                    className="rounded-xl border border-amber-500/25 bg-amber-500/10 p-3"
                  >
                    <div className="flex items-center justify-between">
                      <span className="flex items-center gap-1.5 text-sm font-semibold text-amber-200">
                        <AlertTriangle className="h-4 w-4 text-amber-400" />
                        {meta.label}
                      </span>
                      <span className="rounded-full bg-amber-500/20 px-2 py-0.5 text-xs font-bold text-amber-300 tabular-nums">
                        {count} kez
                      </span>
                    </div>
                    <p className="mt-1.5 text-xs leading-relaxed text-slate-300">
                      {meta.recommendation}
                    </p>
                  </div>
                );
              })}
            </div>
          )}
        </div>

        {/* Target Goal Badge */}
        {session.targetReps && session.reps >= session.targetReps && (
          <div className="mt-3 flex items-center gap-2 rounded-xl border border-emerald-500/30 bg-emerald-500/10 px-3.5 py-2.5 text-xs font-medium text-emerald-300">
            <Flame className="h-4 w-4 shrink-0" />
            {session.targetReps} tekrar hedefini başarıyla tamamladın!
          </div>
        )}

        <button
          onClick={onClose}
          className="mt-5 w-full rounded-2xl bg-emerald-500 py-3.5 text-sm font-bold text-slate-950 transition active:scale-98"
        >
          Harika, Kaydet & Kapat
        </button>
      </div>
    </div>
  );
};
