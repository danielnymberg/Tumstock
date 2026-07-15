package se.nymberg.matverktyg

/**
 * Taktäckningar och deras ungefärliga minsta taklutning enligt svenska
 * branschvärden/tillverkaranvisningar. Detta är RIKTVÄRDEN för fältbruk —
 * det bindande kravet står alltid i tillverkarens monteringsanvisning och
 * gällande branschregler (t.ex. AMA Hus, tätskiktsgarantier). Se disclaimern
 * i appen.
 */
data class Roofing(
    val name: String,
    val minDeg: Float,
    val note: String
)

val ROOFINGS: List<Roofing> = listOf(
    Roofing("Takpapp / tätskikt (tvålags)", 3f, "Råspont. Lägre kräver extra noggrannhet."),
    Roofing("Bandtäckt plåt (dubbelfals)", 6f, "Under 6° krävs extra tätning/klistrad underlagstäckning."),
    Roofing("Profilerad plåt (TRP/pannplåt)", 14f, "8–14° beroende på profilhöjd och överlapp."),
    Roofing("Betongpannor", 14f, "≈ 1:4."),
    Roofing("Tegelpannor, falsat", 14f, "≈ 1:4."),
    Roofing("Tegelpannor, ofalsat (vingtegel)", 22f, "≈ 1:2,5. Öppnare skarvar."),
    Roofing("Asfaltshingel", 15f, "Tillverkarberoende, ofta 15–18°.")
)
