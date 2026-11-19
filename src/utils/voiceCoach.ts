class VoiceCoach {
  private enabled = true;
  private lastFeedbackAt = 0;
  private lastSpokenText = '';
  private audioCtx: AudioContext | null = null;
  private trVoice: SpeechSynthesisVoice | null = null;

  constructor() {
    if (typeof window !== 'undefined' && 'speechSynthesis' in window) {
      const loadVoices = () => {
        const voices = window.speechSynthesis.getVoices();
        this.trVoice =
          voices.find((v) => v.lang.toLowerCase().startsWith('tr')) ?? null;
      };
      loadVoices();
      window.speechSynthesis.onvoiceschanged = loadVoices;
    }
  }

  public setEnabled(enabled: boolean) {
    this.enabled = enabled;
    if (!enabled && typeof window !== 'undefined' && 'speechSynthesis' in window) {
      window.speechSynthesis.cancel();
    }
  }

  private getAudioContext(): AudioContext | null {
    if (typeof window === 'undefined') return null;
    if (!this.audioCtx) {
      const Ctx =
        window.AudioContext ||
        (window as unknown as { webkitAudioContext: typeof AudioContext }).webkitAudioContext;
      if (Ctx) {
        this.audioCtx = new Ctx();
      }
    }
    if (this.audioCtx && this.audioCtx.state === 'suspended') {
      this.audioCtx.resume().catch(() => {});
    }
    return this.audioCtx;
  }

  public playRepBeep(goodForm = true) {
    if (!this.enabled) return;
    const ctx = this.getAudioContext();
    if (!ctx) return;

    const now = ctx.currentTime;
    const osc = ctx.createOscillator();
    const gain = ctx.createGain();
    osc.type = 'sine';
    osc.frequency.setValueAtTime(goodForm ? 660 : 440, now);
    osc.frequency.exponentialRampToValueAtTime(goodForm ? 880 : 390, now + 0.09);

    gain.gain.setValueAtTime(0.14, now);
    gain.gain.exponentialRampToValueAtTime(0.001, now + 0.14);

    osc.connect(gain);
    gain.connect(ctx.destination);
    osc.start(now);
    osc.stop(now + 0.15);
  }

  public playCompleteChime() {
    if (!this.enabled) return;
    const ctx = this.getAudioContext();
    if (!ctx) return;

    const notes = [523.25, 659.25, 783.99, 1046.5];
    notes.forEach((freq, i) => {
      const now = ctx.currentTime + i * 0.1;
      const osc = ctx.createOscillator();
      const gain = ctx.createGain();
      osc.type = 'triangle';
      osc.frequency.setValueAtTime(freq, now);
      gain.gain.setValueAtTime(0.14, now);
      gain.gain.exponentialRampToValueAtTime(0.001, now + 0.25);
      osc.connect(gain);
      gain.connect(ctx.destination);
      osc.start(now);
      osc.stop(now + 0.26);
    });
  }

  public speak(text: string, options?: { priority?: boolean; cooldownMs?: number }) {
    if (!this.enabled) return;
    if (typeof window === 'undefined' || !('speechSynthesis' in window)) return;

    const now = Date.now();
    const cooldown = options?.cooldownMs ?? 3800;

    if (!options?.priority) {
      if (now - this.lastFeedbackAt < cooldown) return;
      if (text === this.lastSpokenText && now - this.lastFeedbackAt < cooldown * 1.8) return;
    }

    try {
      if (options?.priority) {
        window.speechSynthesis.cancel();
      } else if (window.speechSynthesis.speaking) {
        return;
      }

      const utterance = new SpeechSynthesisUtterance(text);
      utterance.lang = 'tr-TR';
      if (this.trVoice) {
        utterance.voice = this.trVoice;
      }
      utterance.rate = 1.08;
      utterance.pitch = 1.0;
      utterance.volume = 1.0;

      window.speechSynthesis.speak(utterance);
      this.lastFeedbackAt = now;
      this.lastSpokenText = text;
    } catch {
      // Ignore speech synthesis errors on unsupported browsers
    }
  }

  public announceRep(repCount: number, targetReps: number | null, goodForm: boolean) {
    this.playRepBeep(goodForm);
    if (!this.enabled) return;

    if (targetReps && repCount === targetReps) {
      this.playCompleteChime();
      this.speak(`${repCount}! Hedefine ulaştın, harika iş!`, { priority: true });
      return;
    }

    if (repCount % 10 === 0) {
      this.speak(`${repCount}! Süper gidiyorsun, devam!`, { priority: true });
    } else if (repCount % 5 === 0) {
      this.speak(`${repCount}, harika!`, { priority: true });
    } else {
      this.speak(`${repCount}`, { priority: true });
    }
  }

  public stop() {
    if (typeof window !== 'undefined' && 'speechSynthesis' in window) {
      window.speechSynthesis.cancel();
    }
  }
}

export const voiceCoach = new VoiceCoach();
