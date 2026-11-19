use serde::{Deserialize, Serialize};

#[derive(Debug, Clone, Copy, PartialEq, Eq, Hash, Serialize, Deserialize)]
pub enum VariationId {
    Standard,
    Wide,
    Diamond,
    Knee,
    Incline,
    Decline,
}

#[derive(Debug, Clone, Copy, PartialEq, Eq, Hash, Serialize, Deserialize)]
pub enum FormIssue {
    HipSag,
    HipPike,
    ShallowDepth,
    HeadDrop,
    TooFast,
}

impl FormIssue {
    pub fn label(self) -> &'static str {
        match self {
            FormIssue::HipSag => "Bel Sarkmasi",
            FormIssue::HipPike => "Kalca Cok Yukarida",
            FormIssue::ShallowDepth => "Yetersiz Derinlik",
            FormIssue::HeadDrop => "Bas Asagi Sarkiyor",
            FormIssue::TooFast => "Cok Hizli Tempo",
        }
    }

    pub fn live_cue(self) -> &'static str {
        match self {
            FormIssue::HipSag => "Kalcani sik! Belin yere dogru sarkiyor",
            FormIssue::HipPike => "Kalcani biraz indir, vucudun duz cizgi olsun",
            FormIssue::ShallowDepth => "Biraz daha asagi in, dirseklerin 90 derece olsun",
            FormIssue::HeadDrop => "Basini dik tut, boynun omurganla hizali olsun",
            FormIssue::TooFast => "Biraz daha yavas ve kontrollu in",
        }
    }

    pub fn recommendation(self) -> &'static str {
        match self {
            FormIssue::HipSag => "Inis ve kalkis boyunca karin ve kalca kaslarini sik. Belin hala sarkiyorsa Diz Ustu veya Egimli sinavla core gucunu artir.",
            FormIssue::HipPike => "Omuzdan bilege kadar tek bir duz cizgi olustur. Kalcayi yukari kaldirmak gogus yukunu azaltir.",
            FormIssue::ShallowDepth => "Tam hareket acikligi (ROM) icin gogsunu yere yaklastir ve dirsek acini 90 derecenin altina indir.",
            FormIssue::HeadDrop => "Bakislarini ellerinin 20-30 cm ilerisine sabitleyerek boynunu notr pozisyonda tut.",
            FormIssue::TooFast => "Ivme kullanmadan 1.5-2 saniyede inip 1 saniyede yukari it; kontrollu tempo kas gelisimini artirir.",
        }
    }
}

#[derive(Debug, Clone, Copy)]
pub struct VariationThresholds {
    pub down_angle: f32,
    pub up_angle: f32,
    pub min_body_angle: f32,
    pub use_knee_line: bool,
}

#[derive(Debug, Clone)]
pub struct PushupVariation {
    pub id: VariationId,
    pub name: &'static str,
    pub short_name: &'static str,
    pub difficulty: &'static str,
    pub muscles: &'static str,
    pub description: &'static str,
    pub camera_tip: &'static str,
    pub kcal_per_rep: f32,
    pub thresholds: VariationThresholds,
    pub checklist: &'static [&'static str],
}

pub const VARIATIONS: &[PushupVariation] = &[
    PushupVariation {
        id: VariationId::Standard,
        name: "Klasik Sinav (Standard)",
        short_name: "Klasik",
        difficulty: "Orta",
        muscles: "Gogus, Triceps, On Omuz, Core",
        description: "Eller omuz genisliginin hemen disinda, vucut bastan topuga duz cizgide yapilan temel sinav.",
        camera_tip: "Kamerayi yan profiline, yere yakin ve tum vucudun gorunecek sekilde yerlestir.",
        kcal_per_rep: 0.45,
        thresholds: VariationThresholds {
            down_angle: 95.0,
            up_angle: 155.0,
            min_body_angle: 155.0,
            use_knee_line: false,
        },
        checklist: &[
            "Eller omuzlarin hemen altinda ve hafifce disinda olsun.",
            "Dirsekleri govdeden 45 derece aciyla geriye dogru buk.",
            "Karin ve kalca kaslarini sikip plank cizgisini koru.",
            "Gogsun yere yaklasana kadar inip kollarini tam acarak yuksel.",
        ],
    },
    PushupVariation {
        id: VariationId::Wide,
        name: "Genis Sinav (Wide)",
        short_name: "Genis",
        difficulty: "Orta",
        muscles: "Dis Gogus, On Omuz",
        description: "Eller omuz genisliginden acik konumlandirilir; gogus kaslarini daha fazla izole eder.",
        camera_tip: "Yan veya 45 derece capraz acidan tum govden ve kolun net gorunsun.",
        kcal_per_rep: 0.48,
        thresholds: VariationThresholds {
            down_angle: 100.0,
            up_angle: 152.0,
            min_body_angle: 154.0,
            use_knee_line: false,
        },
        checklist: &[
            "Ellerini omuz genisliginin yaklasik 1.5 kati kadar ac.",
            "Parmak uclarini hafifce disa cevirerek omuzu rahatlat.",
            "Omuzlarini kulaklarindan uzak tut.",
        ],
    },
    PushupVariation {
        id: VariationId::Diamond,
        name: "Elmas Sinav (Diamond)",
        short_name: "Elmas",
        difficulty: "Ileri",
        muscles: "Triceps, Ic Gogus, Core",
        description: "Basparmak ve isaret parmaklari gogus altinda elmas olusturur. Triceps icin en etkili varyasyon.",
        camera_tip: "Yan profilden kameraya 1.5-2 metre uzaklikta dur, dirsek bukulmesi net gorunsun.",
        kcal_per_rep: 0.58,
        thresholds: VariationThresholds {
            down_angle: 95.0,
            up_angle: 155.0,
            min_body_angle: 155.0,
            use_knee_line: false,
        },
        checklist: &[
            "Ellerini tam gogus kafesinin altinda birlestir.",
            "Dirseklerini govdene yakin tutarak geriye dogru buk.",
            "Gogsun ellerine yaklasana kadar kontrollu in.",
        ],
    },
    PushupVariation {
        id: VariationId::Knee,
        name: "Diz Ustu Sinav (Knee)",
        short_name: "Diz Ustu",
        difficulty: "Baslangic",
        muscles: "Gogus, Triceps, Omuz",
        description: "Dizler yerde destek alarak yuku azaltir. Dogru sinav formunu oturtmak icin harika baslangic.",
        camera_tip: "Yan profilden omuz, kalca ve dizlerinin tamami kadraja girsin.",
        kcal_per_rep: 0.30,
        thresholds: VariationThresholds {
            down_angle: 95.0,
            up_angle: 152.0,
            min_body_angle: 152.0,
            use_knee_line: true,
        },
        checklist: &[
            "Omuzdan dize kadar duz bir cizgi olustur.",
            "Kalcani geride birakma, gogsunu ellerinin arasina indir.",
            "Nefes vererek kontrollu yukari it.",
        ],
    },
    PushupVariation {
        id: VariationId::Incline,
        name: "Egimli Sinav (Incline)",
        short_name: "Egimli",
        difficulty: "Baslangic",
        muscles: "Alt Gogus, Triceps, On Omuz",
        description: "Eller yuksek bir yuzeyde konumlanir. Alt gogsu hedefler ve yuku hafifletir.",
        camera_tip: "Yukseltiyle birlikte tum vucudun yan profilden gorunsun.",
        kcal_per_rep: 0.32,
        thresholds: VariationThresholds {
            down_angle: 95.0,
            up_angle: 152.0,
            min_body_angle: 154.0,
            use_knee_line: false,
        },
        checklist: &[
            "Ellerini kaymayan saglam bir yukseltiye koy.",
            "Vucudunu bastan topuga duz bir egim halinde tut.",
            "Bel cukurunu koru, kalcani sarkitma.",
        ],
    },
    PushupVariation {
        id: VariationId::Decline,
        name: "Ters Egimli (Decline)",
        short_name: "Ters Egim",
        difficulty: "Ileri",
        muscles: "Ust Gogus, On Omuz, Triceps",
        description: "Ayaklar yuksek bir yuzeyde, eller yerde. Ust gogus ve omuz kaslarini yogun calistirir.",
        camera_tip: "Yan profilden ayaklar yukseltide, eller yerde olacak sekilde konumlan.",
        kcal_per_rep: 0.62,
        thresholds: VariationThresholds {
            down_angle: 95.0,
            up_angle: 155.0,
            min_body_angle: 154.0,
            use_knee_line: false,
        },
        checklist: &[
            "Ayaklarini saglam bir sandalye veya kutuya yerlestir.",
            "Core kaslarini ekstra sik — yercekimi belini asagi ceker.",
            "Gogsunu ellerinin arasina kontrollu indir.",
        ],
    },
];

pub fn get_variation(id: VariationId) -> &'static PushupVariation {
    VARIATIONS
        .iter()
        .find(|v| v.id == id)
        .unwrap_or(&VARIATIONS[0])
}
