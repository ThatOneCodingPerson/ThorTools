package io.github.thatonecodingperson.thortools.retroarch

/**
 * The folder RetroArch names after each core in its cheats, saves, states and config folders. RetroArch uses the name
 * the core gives itself, which isn't always its info file's `corename` (`mesen2_libretro` calls itself `Mesen2`, its info
 * file says `MesenCE`), so the folders RetroArch made itself in its config, saves and states folders decide; the cheats
 * folder doesn't count, since Thor Tools makes folders there too. Storage is case-insensitive, so names are compared by
 * their letters and digits only.
 */
object CoreFolders {
    private const val LIBRETRO = "_libretro"

    /**
     * Each installed core's folder by its file stem. [made] are the folders RetroArch made, [played] the files in its
     * saves and states folders by folder, [history] the games it started. A folder named like the core (its `corename`,
     * else its file stem) is the one, unless none of the games history says the core started has a save or state there
     * while another folder has: then that one. A core that never ran gets its `corename`.
     */
    fun resolve(
        cores: List<CoreInfo>,
        made: Set<String>,
        played: Map<String, Set<String>>,
        history: List<HistoryEntry>,
    ): Map<String, String> {
        val byKey = made.sorted().associateBy { CheatSystems.key(it) }
        val named = cores.associate { core -> core.stem to named(core, byKey) }
        val claimed = named.entries.mapNotNull { (stem, folder) -> folder?.let { it to stem } }.toMap()
        return cores.associate { core ->
            val name = named[core.stem]
            val votes = evidence(core.stem, made, played, history, claimed)
            val folder = if (votes.isNotEmpty() && (name == null || name !in votes)) {
                votes.entries.sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key }).first().key
            } else {
                name ?: core.name
            }
            core.stem to folder
        }
    }

    /** The folder of the core with file stem [stem]: its resolved one, else one RetroArch made named like the stem. */
    fun forStem(stem: String, resolved: Map<String, String>, made: Set<String>): String? =
        resolved[stem] ?: made.sorted().firstOrNull { CheatSystems.key(it) == CheatSystems.key(stem.removeSuffix(LIBRETRO)) }

    private fun named(core: CoreInfo, byKey: Map<String, String>): String? =
        byKey[CheatSystems.key(core.name)] ?: byKey[CheatSystems.key(core.stem.removeSuffix(LIBRETRO))]

    /** How many of the games history says [stem] started have a save or state in each folder no other core is named after. */
    private fun evidence(
        stem: String,
        made: Set<String>,
        played: Map<String, Set<String>>,
        history: List<HistoryEntry>,
        claimed: Map<String, String>,
    ): Map<String, Int> {
        val bases = history.filter { it.core == stem }.map { RomNames.base(it.contentName) }.distinct()
        if (bases.isEmpty()) return emptyMap()
        return made.filter { folder -> claimed[folder].let { it == null || it == stem } }.associateWith { folder ->
            val files = played[folder].orEmpty()
            bases.count { base -> files.any { it.startsWith("$base.", ignoreCase = true) } }
        }.filterValues { it > 0 }
    }
}
