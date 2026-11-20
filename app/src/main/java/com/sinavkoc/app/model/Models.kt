package com.sinavkoc.app.model

enum class PushupVariationId {
    STANDARD,
    WIDE,
    DIAMOND,
    KNEE,
    INCLINE,
    DECLINE
}

enum class FormIssue(
    val label: String,
    val liveCue: String,
    val voiceCue: String,
    val recommendation: String
) {
    HIP_SAG(
        label = "Bel Sarkması",
        liveCue = "Kalçanı sık! Belin yere doğru sarkıyor",
        voiceCue = "Belini düz tut, kalçanı sık",
        recommendation = "İniş ve kalkış boyunca karın (core) ve kalça kaslarını aktif şekilde sık. Belin hâlâ sarkıyorsa Diz Üstü veya Eğimli şınavla core gücünü artır."
    ),
    HIP_PIKE(
        label = "Kalça Çok Yukarıda",
        liveCue = "Kalçanı biraz indir, vücudun düz çizgi olsun",
        voiceCue = "Kalçanı indir, düz çizgi ol",
        recommendation = "Omuzdan bileğe kadar tek bir düz çizgi oluştur. Kalçayı yukarı kaldırmak göğüs yükünü azaltır ve omuza gereksiz baskı bindirir."
    ),
    SHALLOW_DEPTH(
        label = "Yetersiz Derinlik",
        liveCue = "Biraz daha aşağı in, dirseklerin 90° olsun",
        voiceCue = "Biraz daha aşağı in",
        recommendation = "Tam hareket açıklığı (ROM) için göğsünü yere 3-5 cm kalana kadar kontrollü indir, dirsek açın 90 derecenin altına insin."
    ),
    HEAD_DROP(
        label = "Baş Aşağı Sarkıyor",
        liveCue = "Başını dik tut, boynun omurganla hizalı olsun",
        voiceCue = "Başını dik tut, karşıya bak",
        recommendation = "Bakışlarını ellerinin 20-30 cm ilerisine sabitleyerek boynunu nötr pozisyonda tut."
    ),
    TOO_FAST(
        label = "Çok Hızlı Tempo",
        liveCue = "Biraz daha yavaş ve kontrollü in",
        voiceCue = "Daha yavaş ve kontrollü yap",
        recommendation = "İvme kullanmadan yaklaşık 1.5-2 saniyede inip 1 saniyede yukarı it. Kontrollü tempo kas gelişimini artırır."
    )
}

enum class FormSeverity {
    GOOD, WARNING, ERROR
}

data class VariationThresholds(
    val downAngle: Float,
    val upAngle: Float,
    val minBodyAngle: Float,
    val useKneeLine: Boolean = false
)

data class PushupVariation(
    val id: PushupVariationId,
    val name: String,
    val shortName: String,
    val difficulty: String,
    val muscles: String,
    val description: String,
    val cameraTip: String,
    val caloriesPerRep: Float,
    val thresholds: VariationThresholds,
    val checklist: List<String>,
    val commonMistakes: List<String>
)

data class RepRecord(
    val repNumber: Int,
    val minElbowAngle: Int,
    val durationMs: Long,
    val score: Int,
    val issues: List<FormIssue>
)

data class WorkoutSession(
    val id: String,
    val timestampMs: Long,
    val variationId: PushupVariationId,
    val reps: Int,
    val targetReps: Int?,
    val durationSec: Int,
    val avgFormScore: Int,
    val calories: Float,
    val issueCounts: Map<FormIssue, Int>
)

data class PosePoint(
    val x: Float,
    val y: Float,
    val visibility: Float
)

data class LivePoseState(
    val phase: String = "IDLE",
    val reps: Int = 0,
    val elbowAngle: Int? = null,
    val bodyAngle: Int? = null,
    val depthPercent: Float = 0f,
    val severity: FormSeverity = FormSeverity.WARNING,
    val feedbackText: String = "Kameraya yan profilden tüm vücudun görünecek şekilde geç",
    val currentScore: Int = 100,
    val landmarks: List<PosePoint> = emptyList(),
    val activeSideRight: Boolean = false
)

object PushupCatalog {
    val variations: List<PushupVariation> = listOf(
        PushupVariation(
            id = PushupVariationId.STANDARD,
            name = "Klasik Şınav",
            shortName = "Klasik",
            difficulty = "Orta",
            muscles = "Göğüs, Triceps, Ön Omuz",
            description = "Eller omuz genişliğinin hemen dışında, vücut baştan topuğa düz çizgide yapılan temel şınav.",
            cameraTip = "Telefonu yan profiline, yerden 20-40 cm yükseğe ve tüm vücudun görünecek şekilde yasla.",
            caloriesPerRep = 0.45f,
            thresholds = VariationThresholds(95f, 155f, 155f),
            checklist = listOf(
                "Eller omuzların hemen altında ve hafifçe dışında olsun.",
                "Dirsekleri gövdeden 45° açıyla geriye doğru bük.",
                "Karın ve kalça kaslarını sıkıp plank çizgisini koru.",
                "Göğsün yere yaklaşana kadar inip kollarını tam aç."
            ),
            commonMistakes = listOf(
                "Dirsekleri kulak hizasına doğru T şeklinde açmak",
                "Yoruldukça belin aşağı sarkması",
                "Yarım inip hızlı tekrar saymaya çalışmak"
            )
        ),
        PushupVariation(
            id = PushupVariationId.WIDE,
            name = "Geniş Şınav (Wide)",
            shortName = "Geniş",
            difficulty = "Orta",
            muscles = "Dış Göğüs, Ön Omuz",
            description = "Eller omuz genişliğinden açık konumlandırılır; göğüs kaslarını daha fazla izole eder.",
            cameraTip = "Yan veya 45° çapraz açıdan tüm gövden ve kolun net görünecek şekilde yerleştir.",
            caloriesPerRep = 0.48f,
            thresholds = VariationThresholds(100f, 152f, 154f),
            checklist = listOf(
                "Ellerini omuz genişliğinin yaklaşık 1.5 katı kadar aç.",
                "Parmak uçlarını hafifçe dışa çevirerek omuzu rahatlat.",
                "Omuzlarını kulaklarından uzak tut, boynunu sıkıştırma."
            ),
            commonMistakes = listOf(
                "Elleri aşırı geniş koyup omuz eklemine yük bindirmek",
                "İnerken kalçayı yukarıda bırakmak"
            )
        ),
        PushupVariation(
            id = PushupVariationId.DIAMOND,
            name = "Elmas Şınav (Diamond)",
            shortName = "Elmas",
            difficulty = "İleri",
            muscles = "Triceps, İç Göğüs, Core",
            description = "Başparmak ve işaret parmakları göğüs altında elmas oluşturur. Triceps için en etkili varyasyon.",
            cameraTip = "Yan profilden kameraya 1.5-2 metre uzaklıkta dur, dirsek bükülmesi net görünsün.",
            caloriesPerRep = 0.58f,
            thresholds = VariationThresholds(95f, 155f, 155f),
            checklist = listOf(
                "Ellerini tam göğüs kafesinin altında birleştir.",
                "Dirseklerini gövdene yakın tutarak geriye doğru bük.",
                "Göğsün ellerine yaklaşana kadar kontrollü in."
            ),
            commonMistakes = listOf(
                "Elleri yüz hizasında çok ileri koymak",
                "Dirsekleri dışa doğru fazla açmak"
            )
        ),
        PushupVariation(
            id = PushupVariationId.KNEE,
            name = "Diz Üstü Şınav (Knee)",
            shortName = "Diz Üstü",
            difficulty = "Başlangıç",
            muscles = "Göğüs, Triceps, Omuz",
            description = "Dizler yerde destek alarak yükü azaltır. Doğru şınav formunu oturtmak için harika başlangıç.",
            cameraTip = "Yan profilden omuz, kalça ve dizlerinin tamamı kadraja girsin.",
            caloriesPerRep = 0.30f,
            thresholds = VariationThresholds(95f, 152f, 152f, useKneeLine = true),
            checklist = listOf(
                "Omuzdan dize kadar düz bir çizgi oluştur.",
                "Kalçanı geride bırakma, göğsünü ellerinin arasına indir.",
                "Nefes vererek kontrollü şekilde yukarı it."
            ),
            commonMistakes = listOf(
                "Kalçayı geriye itip dört ayak pozisyonunda şınav çekmek",
                "Başını aşağı düşürmek"
            )
        ),
        PushupVariation(
            id = PushupVariationId.INCLINE,
            name = "Eğimli Şınav (Incline)",
            shortName = "Eğimli",
            difficulty = "Başlangıç",
            muscles = "Alt Göğüs, Triceps",
            description = "Eller yüksek bir yüzeyde (koltuk, sehpa) konumlanır. Alt göğsü hedefler ve yükü hafifletir.",
            cameraTip = "Yükseltiyle birlikte tüm vücudun yan profilden görünecek şekilde kamerayı yerleştir.",
            caloriesPerRep = 0.32f,
            thresholds = VariationThresholds(95f, 152f, 154f),
            checklist = listOf(
                "Ellerini kaymayan sağlam bir yükseltiye koy.",
                "Vücudunu baştan topuğa düz bir eğim halinde tut.",
                "Bel çukurunu koru, kalçanı sarkıtma."
            ),
            commonMistakes = listOf(
                "Sadece göğsü indirip kalçayı geride bırakmak",
                "Ellerin omuz hizasından çok yukarıda olması"
            )
        ),
        PushupVariation(
            id = PushupVariationId.DECLINE,
            name = "Ters Eğimli (Decline)",
            shortName = "Ters Eğim",
            difficulty = "İleri",
            muscles = "Üst Göğüs, Ön Omuz",
            description = "Ayaklar yüksek bir yüzeyde, eller yerde. Üst göğüs ve omuz kaslarını yoğun çalıştırır.",
            cameraTip = "Yan profilden ayaklar yükseltide, eller yerde olacak şekilde konumlan.",
            caloriesPerRep = 0.62f,
            thresholds = VariationThresholds(95f, 155f, 154f),
            checklist = listOf(
                "Ayaklarını sağlam bir sandalye veya kutu üzerine yerleştir.",
                "Core kaslarını ekstra sık — yerçekimi belini aşağı çekmeye çalışır.",
                "Başını yere çarpmadan göğsünü ellerinin arasına indir."
            ),
            commonMistakes = listOf(
                "Yük arttığı için belin çukurlaşması",
                "Boynu geriye atıp yukarı bakmak"
            )
        )
    )

    fun get(id: PushupVariationId): PushupVariation =
        variations.firstOrNull { it.id == id } ?: variations.first()
}
