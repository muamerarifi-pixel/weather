package app.moti.kosova

class City(val id: String, val name: String, val lat: Double, val lon: Double)

object Cities {

    /** The seven largest cities first, then every other municipality alphabetically. */
    val all: List<City> = listOf(
        City("prishtine", "Prishtinë", 42.6629, 21.1655),
        City("prizren", "Prizren", 42.2139, 20.7397),
        City("peje", "Pejë", 42.6593, 20.2887),
        City("gjakove", "Gjakovë", 42.3803, 20.4308),
        City("mitrovice", "Mitrovicë", 42.8914, 20.8660),
        City("ferizaj", "Ferizaj", 42.3702, 21.1553),
        City("gjilan", "Gjilan", 42.4635, 21.4694),
        City("decan", "Deçan", 42.5402, 20.2879),
        City("dragash", "Dragash", 42.0617, 20.6522),
        City("drenas", "Drenas", 42.6245, 20.8933),
        City("fushe-kosove", "Fushë Kosovë", 42.6639, 21.0961),
        City("gracanice", "Graçanicë", 42.6000, 21.1939),
        City("hani-i-elezit", "Hani i Elezit", 42.1489, 21.2967),
        City("istog", "Istog", 42.7808, 20.4875),
        City("junik", "Junik", 42.4761, 20.2775),
        City("kacanik", "Kaçanik", 42.2319, 21.2594),
        City("kamenice", "Kamenicë", 42.5781, 21.5803),
        City("kline", "Klinë", 42.6217, 20.5778),
        City("kllokot", "Kllokot", 42.3683, 21.3758),
        City("leposaviq", "Leposaviq", 43.1039, 20.8028),
        City("lipjan", "Lipjan", 42.5217, 21.1258),
        City("malisheve", "Malishevë", 42.4822, 20.7458),
        City("mamushe", "Mamushë", 42.3247, 20.7253),
        City("mitrovice-veri", "Mitrovicë e Veriut", 42.8960, 20.8650),
        City("novoberde", "Novobërdë", 42.6070, 21.4396),
        City("obiliq", "Obiliq", 42.6869, 21.0703),
        City("partesh", "Partesh", 42.4022, 21.4339),
        City("podujeve", "Podujevë", 42.9106, 21.1931),
        City("rahovec", "Rahovec", 42.3993, 20.6547),
        City("ranillug", "Ranillug", 42.4919, 21.5986),
        City("shterpce", "Shtërpcë", 42.2397, 21.0272),
        City("shtime", "Shtime", 42.4331, 21.0397),
        City("skenderaj", "Skënderaj", 42.7381, 20.7897),
        City("suhareke", "Suharekë", 42.3580, 20.8250),
        City("viti", "Viti", 42.3214, 21.3583),
        City("vushtrri", "Vushtrri", 42.8231, 20.9675),
        City("zubin-potok", "Zubin Potok", 42.9139, 20.6897),
        City("zvecan", "Zveçan", 42.9075, 20.8403),
    )

    val default: City get() = all[0]

    fun byId(id: String?): City? = if (id == null) null else all.firstOrNull { it.id == id }

    /** Lower-case and drop Albanian diacritics so "prishtine" finds "Prishtinë". */
    fun norm(s: String): String = s.lowercase()
        .replace('ë', 'e')
        .replace('ç', 'c')
}
