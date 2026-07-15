package se.nymberg.matverktyg

import kotlin.math.atan

/**
 * Datadrivna regelpaneler för vattenpass-fliken. Varje regel bär sin KÄLLA
 * (publika branschregler — GVK/BBV/BKR — och riktvärden; egna formuleringar,
 * ingen skyddad text). Bindande krav står alltid i tillverkarens anvisning
 * och gällande branschregler.
 */
data class RuleSource(val label: String)

data class Rule(
    val name: String,
    val minDeg: Float? = null,
    val maxDeg: Float? = null,
    val note: String,
    val source: RuleSource
)

data class RulePanel(
    val id: String,          // nyckel i prefs
    val title: String,
    val subtitle: String,
    val rules: List<Rule>
)

fun degFromMmPerM(mmPerM: Float): Float =
    Math.toDegrees(atan(mmPerM / 1000.0)).toFloat()

private val SRC_GVK = RuleSource("GVK Säkra Våtrum 2021:1 §5.8")
private val SRC_GVK26 = RuleSource("GVK Säkra Våtrum 2021:1 §5.8 · GVK 2026: max 1:35")
private val SRC_BBV = RuleSource("BBV 21:1 (BKR) · GVK 2021:1")
private val SRC_AMA_TOL = RuleSource("Riktvärde buktighet (jfr AMA Hus tab 44.C, AMA-nytt 1/2022)")
private val SRC_ROOF = RuleSource("Riktvärde — tillverkarens monteringsanvisning gäller")

val RULE_PANELS: List<RulePanel> = listOf(
    RulePanel(
        id = "roof",
        title = "Tak — taktäckning",
        subtitle = "Mät takets lutning (platt eller på kant). Grönt = täckningen klarar lutningen.",
        rules = listOf(
            Rule("Takpapp / tätskikt (tvålags)", minDeg = 3f, note = "Råspont. Lägre kräver extra noggrannhet.", source = SRC_ROOF),
            Rule("Bandtäckt plåt (dubbelfals)", minDeg = 6f, note = "Under 6° krävs extra tätning.", source = SRC_ROOF),
            Rule("Profilerad plåt (TRP/pannplåt)", minDeg = 14f, note = "8–14° beroende på profil och överlapp.", source = SRC_ROOF),
            Rule("Betongpannor", minDeg = 14f, note = "≈ 1:4.", source = SRC_ROOF),
            Rule("Tegelpannor, falsat", minDeg = 14f, note = "≈ 1:4.", source = SRC_ROOF),
            Rule("Tegelpannor, ofalsat (vingtegel)", minDeg = 22f, note = "≈ 1:2,5. Öppnare skarvar.", source = SRC_ROOF),
            Rule("Asfaltshingel", minDeg = 15f, note = "Tillverkarberoende, ofta 15–18°.", source = SRC_ROOF)
        )
    ),
    RulePanel(
        id = "wetroom",
        title = "Våtrum — fall mot brunn",
        subtitle = "Lägg telefonen (eller rätskivan med telefonen på) i fallriktningen mot brunnen.",
        rules = listOf(
            Rule(
                "Duschzon / plats för bad", minDeg = degFromMmPerM(6.67f), maxDeg = degFromMmPerM(20f),
                note = "Fall 1:150–1:50 (7–20 mm/m). GVK 2026 tillåter max 1:35.", source = SRC_GVK26
            ),
            Rule(
                "Övrig golvyta i våtrum", minDeg = degFromMmPerM(2f), maxDeg = degFromMmPerM(10f),
                note = "Fall 1:500–1:100 (2–10 mm/m).", source = SRC_GVK
            ),
            Rule(
                "Bakfall", maxDeg = 0.05f,
                note = "Bakfall får inte förekomma — mät med fallriktningen; nära 0° här betyder risk för bakfall.",
                source = SRC_BBV
            )
        )
    ),
    RulePanel(
        id = "tolerance",
        title = "Toleranser — golv & vägg",
        subtitle = "Lägg telefonen på rätskiva 2 m. Lutning över gränsen = utanför riktvärdet.",
        rules = listOf(
            Rule(
                "Buktighet golv, rätskiva 2 m", maxDeg = degFromMmPerM(1.5f),
                note = "±3 mm per 2 m (≈1,5 mm/m).", source = SRC_AMA_TOL
            ),
            Rule(
                "Buktighet vägg, rätskiva 2 m", maxDeg = degFromMmPerM(1.5f),
                note = "±3 mm per 2 m (≈1,5 mm/m).", source = SRC_AMA_TOL
            )
        )
    )
)
