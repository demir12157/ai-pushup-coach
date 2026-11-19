import React, { useState } from 'react';
import {
  AlertCircle,
  Camera,
  CheckCircle2,
  ChevronDown,
  ChevronUp,
  Play,
  Sparkles,
} from 'lucide-react';
import { FORM_ISSUES, PUSHUP_VARIATIONS } from '../data/variations';
import type { PushupVariationId, WorkoutSession } from '../types';
import { getTopFormIssues } from '../utils/storage';

interface CoachGuideViewProps {
  sessions: WorkoutSession[];
  onStartVariation: (id: PushupVariationId) => void;
}

export const CoachGuideView: React.FC<CoachGuideViewProps> = ({
  sessions,
  onStartVariation,
}) => {
  const [expandedId, setExpandedId] = useState<PushupVariationId | null>('standard');
  const topIssues = getTopFormIssues(sessions);

  return (
    <div className="space-y-5 pb-6">
      <div>
        <h1 className="text-2xl font-bold tracking-tight text-white">
          AI Form Koçu & Rehber
        </h1>
        <p className="mt-1 text-sm text-slate-400">
          Daha temiz tekrarlar ve sakatlıksız gelişim için ipuçları
        </p>
      </div>

      {/* Personalized AI Advice */}
      <div className="rounded-2xl border border-emerald-500/25 bg-gradient-to-br from-emerald-950/50 to-slate-900 p-4">
        <div className="flex items-center gap-2 text-emerald-400">
          <Sparkles className="h-4 w-4" />
          <span className="text-xs font-bold uppercase tracking-wider">
            Sana Özel Koç Önerisi
          </span>
        </div>
        {topIssues.length > 0 ? (
          <div className="mt-2 space-y-2">
            <p className="text-sm font-semibold text-white">
              Son antrenmanlarında en çok{' '}
              <span className="text-amber-300">
                {FORM_ISSUES[topIssues[0].type].label}
              </span>{' '}
              tespit edildi ({topIssues[0].count} kez).
            </p>
            <p className="text-xs leading-relaxed text-slate-300">
              {FORM_ISSUES[topIssues[0].type].recommendation}
            </p>
          </div>
        ) : (
          <p className="mt-2 text-xs leading-relaxed text-slate-300">
            İlk antrenmanını tamamladığında kamera analizine göre bel hizan, iniş
            derinliğin ve tempon hakkında sana özel geliştirme önerileri burada
            görünecek.
          </p>
        )}
      </div>

      {/* Camera Setup Tips */}
      <div className="rounded-2xl border border-slate-800 bg-slate-900/70 p-4">
        <div className="flex items-center gap-2">
          <Camera className="h-4 w-4 text-sky-400" />
          <h2 className="text-sm font-semibold text-white">
            En Doğru Kamera Konumu Nasıl Olmalı?
          </h2>
        </div>
        <ul className="mt-3 space-y-2 text-xs text-slate-300">
          <li className="flex items-start gap-2">
            <span className="mt-0.5 h-1.5 w-1.5 shrink-0 rounded-full bg-sky-400" />
            Telefonu yere yakın (20-40 cm) bir yere yasla ve <strong>yan profilinden</strong>{' '}
            1.5 – 2 metre uzağa koy.
          </li>
          <li className="flex items-start gap-2">
            <span className="mt-0.5 h-1.5 w-1.5 shrink-0 rounded-full bg-sky-400" />
            Omuz, dirsek, bilek, kalça ve diz/ayak bileğinin tamamı kadrajda olsun.
          </li>
          <li className="flex items-start gap-2">
            <span className="mt-0.5 h-1.5 w-1.5 shrink-0 rounded-full bg-sky-400" />
            İyi aydınlatılmış bir ortamda ve arka planla kontrast oluşturan kıyafetlerle
            takip doğruluğu maksimuma çıkar.
          </li>
        </ul>
      </div>

      {/* Variation Form Guides */}
      <div className="space-y-3">
        <h2 className="text-sm font-semibold text-white">
          Şınav Varyasyonları & Doğru Form
        </h2>

        {PUSHUP_VARIATIONS.map((v) => {
          const isOpen = expandedId === v.id;
          return (
            <div
              key={v.id}
              className="overflow-hidden rounded-2xl border border-slate-800 bg-slate-900/70"
            >
              <button
                onClick={() => setExpandedId(isOpen ? null : v.id)}
                className="flex w-full items-center justify-between p-4 text-left"
              >
                <div>
                  <div className="flex items-center gap-2">
                    <span className="text-sm font-bold text-white">{v.name}</span>
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
                  </div>
                  <div className="mt-1 text-xs text-slate-400">
                    {v.muscles.join(' • ')}
                  </div>
                </div>
                {isOpen ? (
                  <ChevronUp className="h-5 w-5 text-slate-400" />
                ) : (
                  <ChevronDown className="h-5 w-5 text-slate-400" />
                )}
              </button>

              {isOpen && (
                <div className="border-t border-slate-800/80 px-4 pb-4 pt-3 space-y-3">
                  <p className="text-xs leading-relaxed text-slate-300">
                    {v.description}
                  </p>

                  <div>
                    <div className="mb-1.5 text-xs font-semibold text-emerald-400">
                      Doğru Form Kontrol Listesi
                    </div>
                    <ul className="space-y-1.5">
                      {v.checklist.map((item, idx) => (
                        <li
                          key={idx}
                          className="flex items-start gap-2 text-xs text-slate-300"
                        >
                          <CheckCircle2 className="mt-0.5 h-3.5 w-3.5 shrink-0 text-emerald-400" />
                          <span>{item}</span>
                        </li>
                      ))}
                    </ul>
                  </div>

                  <div>
                    <div className="mb-1.5 text-xs font-semibold text-amber-400">
                      Kaçınılması Gereken Hatalar
                    </div>
                    <ul className="space-y-1.5">
                      {v.commonMistakes.map((item, idx) => (
                        <li
                          key={idx}
                          className="flex items-start gap-2 text-xs text-slate-300"
                        >
                          <AlertCircle className="mt-0.5 h-3.5 w-3.5 shrink-0 text-amber-400" />
                          <span>{item}</span>
                        </li>
                      ))}
                    </ul>
                  </div>

                  <button
                    onClick={() => onStartVariation(v.id)}
                    className="mt-2 flex w-full items-center justify-center gap-2 rounded-xl bg-emerald-500/15 border border-emerald-500/35 py-2.5 text-xs font-bold text-emerald-300 transition hover:bg-emerald-500/25 active:scale-98"
                  >
                    <Play className="h-3.5 w-3.5 fill-current" />
                    {v.name} Antrenmanını Başlat
                  </button>
                </div>
              )}
            </div>
          );
        })}
      </div>
    </div>
  );
};
