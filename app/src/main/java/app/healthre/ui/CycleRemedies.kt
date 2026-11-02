package app.healthre.ui

internal data class Remedy(val title: String, val detail: String, val evidence: String)

internal data class RemedyGroup(val title: String, val items: List<Remedy>, val seeDoctor: String)

private const val STRONG = "Well supported"
private const val SOME = "Some evidence"
private const val LIMITED = "Limited evidence"

/** General self-care guidance (NHS / NICHD style). Not medical advice. */
internal val remedyGroups = listOf(
    RemedyGroup(
        "Cramps",
        listOf(
            Remedy("Heat", "A heat pad or hot-water bottle (wrapped in a cloth) on your lower belly or back for 15-20 minutes.", STRONG),
            Remedy("Pain relief", "Over-the-counter anti-inflammatories such as ibuprofen work best taken at the first sign of pain, with food. Follow the label and your doctor's advice.", STRONG),
            Remedy("Gentle movement", "Walking, stretching or yoga can ease cramps and lift mood.", SOME),
            Remedy("Warm drinks and rest", "Ginger or chamomile tea, a warm bath, and enough sleep.", LIMITED),
            Remedy("Magnesium or omega-3", "Some small studies show benefit, but results are mixed. Ask a pharmacist first.", LIMITED),
        ),
        "Pain that is severe, getting worse over time, or stops you doing normal things.",
    ),
    RemedyGroup(
        "Heavy flow",
        listOf(
            Remedy("Iron-rich food", "Lentils, beans, leafy greens, red meat, fortified cereal. Pair with vitamin C to absorb more.", SOME),
            Remedy("Stay hydrated and rest", "Heavy bleeding can leave you tired and light-headed.", SOME),
            Remedy("Track it", "Note how often you change a pad or tampon and any clots. It helps your doctor.", STRONG),
            Remedy("Anti-inflammatories", "Can reduce flow a little as well as pain. Not suitable for everyone.", SOME),
        ),
        "Soaking a pad or tampon every hour for 2+ hours, clots larger than a coin, bleeding over 7 days, dizziness or breathlessness.",
    ),
    RemedyGroup(
        "PMS and mood",
        listOf(
            Remedy("Regular exercise", "Aerobic exercise and yoga are linked to fewer PMS symptoms.", STRONG),
            Remedy("Steady meals", "Smaller, frequent meals with whole grains. Less salt, caffeine and alcohol can reduce bloating and irritability.", SOME),
            Remedy("Sleep routine", "Aim for consistent sleep and wind-down time in the week before your period.", SOME),
            Remedy("Calcium and vitamin B6", "May help some people. Stay within recommended doses.", LIMITED),
            Remedy("Stress relief", "Breathing exercises, journaling or talking to someone you trust.", SOME),
        ),
        "Mood changes that feel overwhelming, thoughts of self-harm, or symptoms that disrupt work or relationships every month.",
    ),
    RemedyGroup(
        "Irregular cycles",
        listOf(
            Remedy("Keep logging", "Three or more cycles of data make predictions and patterns much clearer.", STRONG),
            Remedy("Healthy weight and regular activity", "Large weight changes, very low body fat and intense training can all disrupt cycles.", SOME),
            Remedy("Manage stress and sleep", "Ongoing stress and poor sleep can delay ovulation.", SOME),
            Remedy("Review medicines", "Some medicines and contraception change bleeding patterns.", SOME),
        ),
        "Cycles shorter than 21 or longer than 35 days, no period for 3 months (and not pregnant), bleeding between periods or after sex, or sudden change from your usual pattern.",
    ),
)

internal val symptomOptions = listOf(
    "Cramps", "Headache", "Bloating", "Fatigue", "Mood swings", "Acne", "Nausea", "Back pain", "Tender breasts", "Cravings",
)

internal val flowOptions = listOf("None", "Spotting", "Light", "Medium", "Heavy")

internal const val CYCLE_DISCLAIMER = "Predictions are estimates and these tips are general information, not medical advice. See a doctor about anything that worries you."
