import type { FormIssueMeta, FormIssueType, PushupVariation, PushupVariationId } from '../types';

export const FORM_ISSUES: Record<FormIssueType, FormIssueMeta> = {
  hip_sag: {
    type: 'hip_sag',
    label: 'Bel Sarkması',
    liveCue: 'Kalçanı sık! Belin yere doğru sarkıyor',
    voiceCue: 'Belini düz tut, kalçanı sık',
    recommendation:
      'İniş ve kalkış boyunca karın (core) ve kalça kaslarını aktif şekilde sık. Eğer belin hâlâ sarkıyorsa önce Diz Üstü veya Eğimli şınavla core gücünü artır.',
  },
  hip_pike: {
    type: 'hip_pike',
    label: 'Kalça Çok Yukarıda',
    liveCue: 'Kalçanı biraz indir, vücudun düz çizgi olsun',
    voiceCue: 'Kalçanı indir, düz çizgi ol',
    recommendation:
      'Omuzdan bileğe kadar tek bir düz çizgi oluştur. Kalçayı yukarı kaldırmak göğüs yükünü azaltır ve omuza gereksiz baskı bindirir.',
  },
  shallow_depth: {
    type: 'shallow_depth',
    label: 'Yetersiz Derinlik',
    liveCue: 'Biraz daha aşağı in, dirseklerin 90° olsun',
    voiceCue: 'Biraz daha aşağı in',
    recommendation:
      'Tam hareket açıklığı (ROM) kas gelişimi için çok önemli. Göğsünü yere 3-5 cm kalana kadar kontrollü indir, dirsek açın 90 derecenin altına insin.',
  },
  incomplete_lockout: {
    type: 'incomplete_lockout',
    label: 'Kollar Tam Açılmadı',
    liveCue: 'Yukarıda kollarını tam düzleştir',
    voiceCue: 'Yukarıda kollarını tam aç',
    recommendation:
      'Her tekrarın tepesinde dirseklerini tam uzatıp kürek kemiklerini hafifçe dışa it (protraction). Yarım tekrarlar triceps gelişimini sınırlar.',
  },
  head_drop: {
    type: 'head_drop',
    label: 'Baş Aşağı Sarkıyor',
    liveCue: 'Başını dik tut, boynun omurganla hizalı olsun',
    voiceCue: 'Başını dik tut, karşıya bak',
    recommendation:
      'Yere bakarken başını düşürme; bakışlarını ellerinin yaklaşık 20-30 cm ilerisine sabitleyerek boynunu nötr pozisyonda tut.',
  },
  too_fast: {
    type: 'too_fast',
    label: 'Çok Hızlı Tempo',
    liveCue: 'Biraz daha yavaş ve kontrollü in',
    voiceCue: 'Daha yavaş ve kontrollü yap',
    recommendation:
      'İvme kullanmadan yaklaşık 1.5-2 saniyede inip 1 saniyede yukarı it. Kontrollü tempo kas gerginliğini artırır ve eklemleri korur.',
  },
};

export const PUSHUP_VARIATIONS: PushupVariation[] = [
  {
    id: 'standard',
    name: 'Klasik Şınav',
    shortName: 'Klasik',
    difficulty: 'Orta',
    muscles: ['Göğüs', 'Triceps', 'Ön Omuz', 'Core'],
    description: 'Eller omuz genişliğinin hemen dışında, vücut baştan topuğa düz çizgide yapılan temel şınav.',
    cameraTip: 'Telefonu yan profiline, yerden 20-40 cm yükseğe ve tüm vücudun görünecek şekilde koy.',
    caloriesPerRep: 0.45,
    thresholds: {
      downAngle: 95,
      upAngle: 155,
      minBodyAngle: 155,
      maxBodyDeviation: 24,
    },
    checklist: [
      'Eller omuzların hemen altında ve hafifçe dışında olsun.',
      'Dirsekleri gövdeden 45° açıyla geriye doğru bük (90° yana açma).',
      'Karın ve kalça kaslarını sıkıp vücudu plank çizgisinde tut.',
      'Göğsün yere yaklaşana kadar inip kollarını tam açarak yüksel.',
    ],
    commonMistakes: [
      'Dirsekleri kulak hizasına doğru T şeklinde açmak',
      'Yoruldukça belin aşağı sarkması',
      'Yarım inip hızlıca tekrar saymaya çalışmak',
    ],
  },
  {
    id: 'wide',
    name: 'Geniş Şınav (Wide)',
    shortName: 'Geniş',
    difficulty: 'Orta',
    muscles: ['Dış Göğüs', 'Ön Omuz'],
    description: 'Eller omuz genişliğinden daha açık konumlandırılır; göğüs kaslarını daha fazla izole eder.',
    cameraTip: 'Yan-çapraz (45°) veya tam yan açıdan tüm gövden ve kolun net görünecek şekilde yerleştir.',
    caloriesPerRep: 0.48,
    thresholds: {
      downAngle: 100,
      upAngle: 152,
      minBodyAngle: 154,
      maxBodyDeviation: 25,
    },
    checklist: [
      'Ellerini omuz genişliğinin yaklaşık 1.5 katı kadar aç.',
      'Parmak uçlarını hafifçe dışa doğru çevirerek omuz eklemini rahatlat.',
      'Omuzlarını kulaklarından uzak tut, boynunu sıkıştırma.',
      'Göğsünde gerginliği hissedene kadar kontrollü in.',
    ],
    commonMistakes: [
      'Elleri aşırı geniş koyup omuz eklemine yük bindirmek',
      'İnerken kalçayı yukarıda bırakmak',
    ],
  },
  {
    id: 'diamond',
    name: 'Elmas Şınav (Diamond)',
    shortName: 'Elmas',
    difficulty: 'İleri',
    muscles: ['Triceps', 'İç Göğüs', 'Core'],
    description: 'Başparmak ve işaret parmakları göğüs altında üçgen/elmas oluşturur. Triceps için en etkili varyasyon.',
    cameraTip: 'Yan profilden kameraya 1.5-2 metre uzaklıkta dur, dirsek bükülmesi net görünsün.',
    caloriesPerRep: 0.58,
    thresholds: {
      downAngle: 95,
      upAngle: 155,
      minBodyAngle: 155,
      maxBodyDeviation: 24,
    },
    checklist: [
      'Ellerini tam göğüs kafesinin (sternum) altında birleştir.',
      'Dirseklerini gövdene yakın tutarak geriye doğru bük.',
      'Göğsün ellerine yaklaşana kadar kontrollü in.',
      'Dengeyi korumak için ayaklarını hafifçe açabilirsin.',
    ],
    commonMistakes: [
      'Elleri yüz hizasında çok ileri koymak',
      'Dirsekleri dışa doğru fazla açmak',
      'Derinliği kısa kesmek',
    ],
  },
  {
    id: 'knee',
    name: 'Diz Üstü Şınav (Knee)',
    shortName: 'Diz Üstü',
    difficulty: 'Başlangıç',
    muscles: ['Göğüs', 'Triceps', 'Omuz'],
    description: 'Dizler yerde destek alarak yükü azaltır. Doğru şınav formunu oturtmak için harika bir başlangıç.',
    cameraTip: 'Yan profilden omuz, kalça ve dizlerinin tamamı kadraja girecek şekilde telefonu yerleştir.',
    caloriesPerRep: 0.3,
    thresholds: {
      downAngle: 95,
      upAngle: 152,
      minBodyAngle: 152,
      maxBodyDeviation: 26,
      useKneeLine: true,
    },
    checklist: [
      'Dizlerin altına yumuşak bir mat veya havlu koy.',
      'Omuzdan dize kadar düz bir çizgi oluştur (kalçanı geride bırakma).',
      'Göğsünü ellerinin arasına doğru indir.',
      'Nefes vererek kontrollü şekilde yukarı it.',
    ],
    commonMistakes: [
      'Kalçayı geriye itip dört ayak pozisyonunda şınav çekmek',
      'Başını aşağı düşürmek',
    ],
  },
  {
    id: 'incline',
    name: 'Eğimli Şınav (Incline)',
    shortName: 'Eğimli',
    difficulty: 'Başlangıç',
    muscles: ['Alt Göğüs', 'Triceps', 'Ön Omuz'],
    description: 'Eller yüksek bir yüzeyde (koltuk, sehpa, duvar) konumlanır. Alt göğsü hedefler ve yükü hafifletir.',
    cameraTip: 'Yükseltiyle birlikte tüm vücudun yan profilden görünecek şekilde kamerayı konumlandır.',
    caloriesPerRep: 0.32,
    thresholds: {
      downAngle: 95,
      upAngle: 152,
      minBodyAngle: 154,
      maxBodyDeviation: 25,
    },
    checklist: [
      'Ellerini kaymayan sağlam bir yükseltiye koy.',
      'Vücudunu baştan topuğa düz bir eğim halinde tut.',
      'Göğsünü yükseltinin kenarına yaklaştırıp geri it.',
      'Bel çukurunu koru, kalçanı sarkıtma.',
    ],
    commonMistakes: [
      'Sadece göğsü indirip kalçayı geride bırakmak',
      'Ellerin omuz hizasından çok yukarıda olması',
    ],
  },
  {
    id: 'decline',
    name: 'Ters Eğimli (Decline)',
    shortName: 'Ters Eğim',
    difficulty: 'İleri',
    muscles: ['Üst Göğüs', 'Ön Omuz', 'Triceps'],
    description: 'Ayaklar yüksek bir yüzeyde, eller yerde. Üst göğüs ve omuz kaslarını yoğun şekilde çalıştırır.',
    cameraTip: 'Yan profilden ayaklar yükseltide, eller yerde olacak şekilde tüm vücudu kadraja al.',
    caloriesPerRep: 0.62,
    thresholds: {
      downAngle: 95,
      upAngle: 155,
      minBodyAngle: 154,
      maxBodyDeviation: 25,
    },
    checklist: [
      'Ayaklarını sağlam bir sandalye veya kutu üzerine yerleştir.',
      'Core kaslarını ekstra sık — yerçekimi belini aşağı çekmeye çalışacaktır.',
      'Başını yere çarpmadan göğsünü ellerinin arasına indir.',
      'Omuzlarını sabit tutarak kontrollü yüksel.',
    ],
    commonMistakes: [
      'Yük arttığı için belin çukurlaşması (hip sag)',
      'Boynu geriye atıp yukarı bakmak',
    ],
  },
];

export function getVariationById(id: PushupVariationId): PushupVariation {
  return PUSHUP_VARIATIONS.find((v) => v.id === id) ?? PUSHUP_VARIATIONS[0];
}
