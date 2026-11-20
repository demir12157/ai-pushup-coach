package com.sinavkoc.app.audio

import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.speech.tts.TextToSpeech
import java.util.Locale

class VoiceCoach(context: Context) : TextToSpeech.OnInitListener {
    private var tts: TextToSpeech? = TextToSpeech(context.applicationContext, this)
    private var ready = false
    private var enabled = true
    private var lastCueAt = 0L
    private var lastCueText = ""
    private val tone = runCatching { ToneGenerator(AudioManager.STREAM_MUSIC, 80) }.getOrNull()

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val tr = Locale("tr", "TR")
            val res = tts?.setLanguage(tr)
            ready = res != TextToSpeech.LANG_MISSING_DATA && res != TextToSpeech.LANG_NOT_SUPPORTED
            if (!ready) {
                tts?.setLanguage(Locale.getDefault())
                ready = true
            }
            tts?.setSpeechRate(1.08f)
        }
    }

    fun setEnabled(value: Boolean) {
        enabled = value
        if (!value) {
            tts?.stop()
        }
    }

    fun playRepBeep(goodForm: Boolean) {
        if (!enabled) return
        tone?.startTone(
            if (goodForm) ToneGenerator.TONE_PROP_BEEP else ToneGenerator.TONE_PROP_NACK,
            100
        )
    }

    fun speak(text: String, priority: Boolean = false) {
        if (!enabled || !ready) return
        val now = System.currentTimeMillis()
        if (!priority) {
            if (now - lastCueAt < 3800L) return
            if (text == lastCueText && now - lastCueAt < 6500L) return
        }
        val mode = if (priority) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD
        tts?.speak(text, mode, null, "cue_$now")
        lastCueAt = now
        lastCueText = text
    }

    fun announceRep(repCount: Int, targetReps: Int?, goodForm: Boolean) {
        playRepBeep(goodForm)
        if (!enabled) return
        when {
            targetReps != null && repCount == targetReps ->
                speak("$repCount! Hedefine ulaştın, harika iş!", priority = true)
            repCount % 10 == 0 ->
                speak("$repCount! Süper gidiyorsun, devam!", priority = true)
            repCount % 5 == 0 ->
                speak("$repCount, harika!", priority = true)
            else ->
                speak("$repCount", priority = true)
        }
    }

    fun shutdown() {
        tts?.stop()
        tts?.shutdown()
        tts = null
        tone?.release()
    }
}
