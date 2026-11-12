package it.saranave.ciclo.data

/** Un'opzione dentro una voce (es. "Crampi" dentro "Dolori"). */
data class Opt(val key: String, val label: String, val onByDefault: Boolean = true, val group: String? = null)

/**
 * Una voce da tracciare.
 * single = si può scegliere una sola opzione.
 * leveled = ogni opzione ha un livello 1-3 (salvato come "chiave:livello").
 */
data class Cat(
    val key: String,
    val title: String,
    val emoji: String,
    val options: List<Opt>,
    val single: Boolean = false,
    val leveled: Boolean = false,
    val onByDefault: Boolean = true,
)

object Catalog {
    const val PERIOD = "period"
    const val SKIN = "skin_symptoms"
    const val SKIN_OK = "skin_ok"
    const val ZONES = "skin_zones"
    const val TAGS = "tags"
    const val NOTE = "note"
    const val WEIGHT = "weight"
    const val EVENT = "event"
    const val PHOTO = "photo"

    val periodLevels = listOf("light", "medium", "heavy", "very_heavy")

    val period = Cat(PERIOD, "Mestruazioni", "🩸", single = true, options = listOf(
        Opt("light", "Leggere"), Opt("medium", "Medie"), Opt("heavy", "Abbondanti"), Opt("very_heavy", "Molto abbondanti"),
    ))
    val spotting = Cat("spotting", "Spotting", "🟤", options = listOf(
        Opt("red", "Rosso"), Opt("brown", "Marrone"),
    ))
    val pain = Cat("pain", "Dolori", "⚡", options = listOf(
        Opt("period_cramps", "Crampi"), Opt("headache", "Mal di testa"), Opt("migraine", "Emicrania"),
        Opt("ovulation", "Ovulazione"), Opt("lower_back", "Schiena"), Opt("breast", "Seno"),
        Opt("armpit", "Ascella"), Opt("leg", "Gambe"), Opt("joint", "Articolazioni"), Opt("vulvar", "Vulvare"),
    ))
    val pms = Cat("pms", "PMS", "🌀", options = listOf(Opt("yes", "PMS")))
    val collection = Cat("collection", "Metodo", "🧺", options = listOf(
        Opt("pad", "Assorbente"), Opt("tampon", "Tampone"), Opt("cup", "Coppetta"),
        Opt("period_underwear", "Mutande mestruali"), Opt("liner", "Salvaslip"),
    ))
    val discharge = Cat("discharge", "Perdite", "💧", onByDefault = false, options = listOf(
        Opt("creamy", "Cremose"), Opt("sticky", "Appiccicose"), Opt("egg_white", "Albume"),
        Opt("watery", "Acquose"), Opt("atypical", "Insolite"),
    ))

    // ---- Pelle: sintomi con livello, divisi per rosacea / dermatite seborroica
    const val G_ROS = "Tipici della rosacea"
    const val G_DS = "Tipici della dermatite seborroica"
    const val G_BOTH = "Comuni"
    val skin = Cat(SKIN, "Sintomi pelle", "🌡️", leveled = true, options = listOf(
        Opt("redness", "Rossore di fondo", group = G_ROS),
        Opt("flushing", "Vampate / calore", group = G_ROS),
        Opt("capillaries", "Capillari visibili", group = G_ROS),
        Opt("papules", "Brufoletti (papule)", group = G_ROS),
        Opt("pustules", "Pustole", group = G_ROS),
        Opt("swelling", "Gonfiore", group = G_ROS),
        Opt("burning", "Bruciore / pizzicore", group = G_ROS),
        Opt("eyes", "Occhi secchi / irritati", group = G_ROS),
        Opt("eyelids", "Palpebre gonfie", group = G_ROS),
        Opt("greasy_scales", "Squame grasse / giallastre", group = G_DS),
        Opt("flaking", "Desquamazione", group = G_DS),
        Opt("itch", "Prurito", group = G_DS),
        Opt("dandruff", "Forfora / cute che si sfalda", group = G_DS),
        Opt("dryness", "Secchezza / tiraggio", group = G_BOTH),
        Opt("weeping", "Zone che trasudano", group = G_BOTH),
        Opt("oily", "Pelle lucida / grassa", group = G_BOTH),
    ))
    val rosaceaKeys = setOf("redness", "flushing", "capillaries", "papules", "pustules", "swelling", "burning", "eyes", "eyelids")
    val dsKeys = setOf("greasy_scales", "flaking", "itch", "dandruff")

    val zones = Cat(ZONES, "Zone", "📍", options = listOf(
        Opt("cheeks", "Guance", group = "Più da rosacea"), Opt("nose", "Naso", group = "Più da rosacea"),
        Opt("chin", "Mento", group = "Più da rosacea"), Opt("forehead", "Fronte", group = "Più da rosacea"),
        Opt("eyebrows", "Sopracciglia", group = "Più da dermatite"), Opt("nose_sides", "Lati del naso", group = "Più da dermatite"),
        Opt("hairline", "Attaccatura capelli", group = "Più da dermatite"), Opt("ears", "Dietro le orecchie", group = "Più da dermatite"),
        Opt("scalp", "Cuoio capelluto", group = "Più da dermatite"), Opt("chest", "Petto", group = "Più da dermatite"),
        Opt("around_eyes", "Contorno occhi", group = "Altre"), Opt("neck", "Collo", group = "Altre"),
    ))
    val triggers = Cat("triggers", "Possibili trigger", "🔥", options = listOf(
        Opt("stress", "Stress"), Opt("sun_heat", "Sole / caldo"), Opt("cold_wind", "Freddo / vento"),
        Opt("alcohol", "Alcol"), Opt("spicy_hot", "Piccante / cibi caldi"), Opt("sport", "Sport / sudore"),
        Opt("little_sleep", "Poco sonno"), Opt("new_product", "Prodotto nuovo"), Opt("plants", "Piante / fiori 🌼"),
        Opt("makeup", "Trucco"), Opt("mask", "Mascherina / attrito", onByDefault = false), Opt("hot_shower", "Doccia calda"),
        Opt("sugar", "Dolci", onByDefault = false), Opt("dairy", "Latticini", onByDefault = false),
    ))
    val therapy = Cat("therapy", "Terapia pelle", "💊", options = listOf(
        Opt("metronidazole", "Metronidazolo"), Opt("efracea", "Efracea"),
        Opt("ceramol", "Crema Ceramol"), Opt("ivermectin", "Ivermectina", onByDefault = false),
        Opt("ketoconazole", "Ketoconazolo", onByDefault = false),
    ))

    // ---- Come sto
    val feelings = Cat("feelings", "Umore", "😊", options = listOf(
        Opt("happy", "Felice"), Opt("calm", "Serena"), Opt("sensitive", "Sensibile"), Opt("sad", "Triste"),
        Opt("anxious", "Ansiosa"), Opt("irritable", "Irritabile"), Opt("angry", "Arrabbiata"),
        Opt("mood_swings", "Sbalzi d'umore"), Opt("grateful", "Grata", onByDefault = false),
        Opt("euphoric", "Euforica", onByDefault = false), Opt("apathetic", "Apatica", onByDefault = false),
        Opt("lonely", "Sola", onByDefault = false), Opt("overwhelmed", "Sopraffatta", onByDefault = false),
    ))
    val energy = Cat("energy", "Energia", "🔋", options = listOf(
        Opt("fully_energized", "Carichissima"), Opt("energetic", "Energica"), Opt("tired", "Stanca"), Opt("exhausted", "Esausta"),
    ))
    val mind = Cat("mind", "Mente", "🧠", options = listOf(
        Opt("focused", "Concentrata"), Opt("distracted", "Distratta"), Opt("motivated", "Motivata"),
        Opt("unmotivated", "Demotivata"), Opt("productive", "Produttiva"), Opt("unproductive", "Improduttiva"),
        Opt("calm", "Calma"), Opt("stressed", "Stressata"), Opt("creative", "Creativa"),
        Opt("foggy", "Annebbiata", onByDefault = false),
    ))
    val social = Cat("social_life", "Vita sociale", "👥", options = listOf(
        Opt("sociable", "Socievole"), Opt("withdrawn", "Chiusa"), Opt("supportive", "Di supporto"),
        Opt("argumentative", "Polemica"), Opt("conflict", "Litigio", onByDefault = false),
    ))
    val sleep = Cat("sleep", "Sonno", "😴", options = listOf(
        Opt("good", "Dormito bene"), Opt("little", "Poco"), Opt("interrupted", "Interrotto"),
        Opt("insomnia", "Insonnia"), Opt("a_lot", "Tanto"), Opt("nightmares", "Incubi / sogni agitati", onByDefault = false),
    ))

    // ---- Corpo e altro
    val hair = Cat("hair", "Capelli", "💇‍♀️", options = listOf(
        Opt("good_hair", "Capelli ok"), Opt("bad_hair", "Capelli no"), Opt("dry_scalp", "Cute secca"),
        Opt("oily_scalp", "Cute grassa"), Opt("hair_loss", "Caduta", onByDefault = false),
    ))
    val craving = Cat("craving", "Voglie", "🍫", options = listOf(
        Opt("sweet", "Dolce"), Opt("salty", "Salato"), Opt("carbs", "Carboidrati"), Opt("chocolate", "Cioccolato"),
    ))
    val digestion = Cat("stool", "Pancia", "🫄", options = listOf(
        Opt("ok", "Ok"), Opt("bloated", "Gonfia"), Opt("diarrhea", "Diarrea"), Opt("constipation", "Stitichezza"),
        Opt("nausea", "Nausea"), Opt("heartburn", "Acidità", onByDefault = false),
    ))
    val sex = Cat("sex_life", "Sesso", "❤️", options = listOf(
        Opt("high_sex_drive", "Libido alta"), Opt("low_sex_drive", "Libido bassa"),
        Opt("protected", "Protetto"), Opt("unprotected", "Non protetto"),
    ))
    val meds = Cat("meds", "Farmaci e integratori", "💊", options = listOf(
        Opt("painkiller", "Antidolorifico"), Opt("supradin", "Supradin"), Opt("antihistamine", "Antistaminico"),
        Opt("iron", "Ferro", onByDefault = false), Opt("magnesium", "Magnesio", onByDefault = false),
    ))
    val exercise = Cat("exercise", "Movimento", "🚶‍♀️", onByDefault = false, options = listOf(
        Opt("walk", "Camminata"), Opt("yoga", "Yoga"), Opt("run", "Corsa"), Opt("gym", "Palestra"),
        Opt("swim", "Nuoto"), Opt("bike", "Bici"),
    ))

    val groups: List<Pair<String, List<Cat>>> = listOf(
        "Ciclo" to listOf(period, spotting, pain, pms, collection, discharge),
        "Pelle" to listOf(skin, zones, triggers, therapy),
        "Come sto" to listOf(feelings, energy, mind, social, sleep),
        "Corpo" to listOf(hair, craving, digestion, sex, meds, exercise),
    )
    val all: List<Cat> = groups.flatMap { it.second }

    fun cat(key: String): Cat? = all.firstOrNull { it.key == key }

    fun label(catKey: String, value: String): String {
        val (k, _) = splitLevel(value)
        return cat(catKey)?.options?.firstOrNull { it.key == k }?.label ?: k
    }

    /** "papules:2" -> ("papules", 2). Valori senza livello -> livello 1. */
    fun splitLevel(v: String): Pair<String, Int> {
        val i = v.lastIndexOf(':')
        if (i <= 0) return v to 1
        val lvl = v.substring(i + 1).toIntOrNull() ?: return v to 1
        return v.substring(0, i) to lvl
    }

    val levelNames = listOf("", "lieve", "medio", "forte")
}
