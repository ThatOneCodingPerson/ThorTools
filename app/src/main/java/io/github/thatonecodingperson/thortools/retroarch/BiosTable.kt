package io.github.thatonecodingperson.thortools.retroarch

import androidx.annotation.StringRes
import io.github.thatonecodingperson.thortools.R

/** A console whose RetroArch cores use BIOS files; [cores] are the cores' own names. */
enum class BiosConsole(@StringRes val title: Int, val cores: String, @StringRes val note: Int? = null) {
    PS1(R.string.raBiosPs1, "PCSX-ReARMed, Beetle PSX, SwanStation", R.string.raBiosPs1Note),
    PS2(R.string.raBiosPs2, "LRPS2", R.string.raBiosPs2Note),
    SEGA_CD(R.string.raBiosSegaCd, "Genesis Plus GX, PicoDrive", R.string.raBiosSegaCdNote),
    SEGA_BOOT(R.string.raBiosSegaBoot, "Genesis Plus GX", R.string.raBiosSegaBootNote),
    SATURN(R.string.raBiosSaturn, "Beetle Saturn, Kronos, Yabause, YabaSanshiro", R.string.raBiosSaturnNote),
    DREAMCAST(R.string.raBiosDreamcast, "Flycast", R.string.raBiosDreamcastNote),
    GBA(R.string.raBiosGba, "mGBA, gpSP, VBA-M", R.string.raBiosGbaNote),
    GB(R.string.raBiosGb, "mGBA, Gambatte, SameBoy, Gearboy, VBA-M", R.string.raBiosGbNote),
    NDS(R.string.raBiosNds, "melonDS DS, melonDS, DeSmuME", R.string.raBiosNdsNote),
    FDS(R.string.raBiosFds, "Mesen, Nestopia, FCEUmm"),
    PCE_CD(R.string.raBiosPceCd, "Beetle PCE FAST, Beetle SuperGrafx", R.string.raBiosPceCdNote),
    PC_FX(R.string.raBiosPcfx, "Beetle PC-FX"),
    NEO_GEO(R.string.raBiosNeoGeo, "FinalBurn Neo, Geolith", R.string.raBiosNeoGeoNote),
    NEO_GEO_CD(R.string.raBiosNeoGeoCd, "NeoCD", R.string.raBiosNeoGeoCdNote),
    N64DD(R.string.raBios64dd, "Mupen64Plus-Next, ParaLLEl N64", R.string.raBios64ddNote),
    LYNX(R.string.raBiosLynx, "Handy, Beetle Lynx"),
    THREE_DO(R.string.raBios3do, "Opera"),
    ATARI_7800(R.string.raBios7800, "ProSystem"),
    ATARI_5200(R.string.raBios5200, "Atari800"),
    INTELLIVISION(R.string.raBiosIntellivision, "FreeIntv"),
    POKEMON_MINI(R.string.raBiosPokemini, "PokeMini"),
}

/** How much the cores need a BIOS file. */
enum class BiosNeed(@StringRes val label: Int) {
    NEEDED(R.string.raBiosNeeded),

    /** Needed for the games of the file's region. */
    REGION(R.string.raBiosNeededRegion),
    OPTIONAL(R.string.raBiosOptional),
}

/** What a BIOS file is, in words. */
enum class BiosWhat(@StringRes val label: Int) {
    JAPAN(R.string.raBiosJapan),
    NORTH_AMERICA(R.string.raBiosNorthAmerica),
    EUROPE(R.string.raBiosEurope),
    NORTH_AMERICA_EUROPE(R.string.raBiosNorthAmericaEurope),
    REGION_FREE_PSP(R.string.raBiosRegionFreePsp),
    REGION_FREE_PS3(R.string.raBiosRegionFreePs3),
    OTHER_VERSION(R.string.raBiosOtherVersion),
    MS_BOOT(R.string.raBiosMsBoot),
    GG_BOOT(R.string.raBiosGgBoot),
    MD_BOOT(R.string.raBiosMdBoot),
    GB_BOOT(R.string.raBiosGbBoot),
    GBC_BOOT(R.string.raBiosGbcBoot),
    SGB_BOOT(R.string.raBiosSgbBoot),
    DSI(R.string.raBiosDsi),
    ARM7(R.string.raBiosArm7),
    ARM9(R.string.raBiosArm9),
    FIRMWARE(R.string.raBiosFirmware),
    NAND(R.string.raBiosNand),
    NAOMI(R.string.raBiosNaomi),
    ATOMISWAVE(R.string.raBiosAtomiswave),
    EXEC(R.string.raBiosExec),
    GROM(R.string.raBiosGrom),
}

/**
 * Files of a console listed together: only the ones found (in the user's folders or RetroArch's) are shown, and a group
 * that isn't [BiosNeed.OPTIONAL] with none of them shows as one missing line. [oneEnough]: the core needs only one of
 * them, any version.
 */
enum class BiosGroup(@StringRes val title: Int, val need: BiosNeed, val oneEnough: Boolean = false) {
    PS1_REGION_FREE(R.string.raBiosGroupRegionFree, BiosNeed.OPTIONAL),
    PS1_OTHER(R.string.raBiosGroupPs1Other, BiosNeed.OPTIONAL),
    PS2(R.string.raBiosGroupPs2, BiosNeed.NEEDED, oneEnough = true),
    PCE_OTHER(R.string.raBiosGroupPceOther, BiosNeed.OPTIONAL),
    DSI(R.string.raBiosGroupDsi, BiosNeed.OPTIONAL),
    NEO_GEO_CD(R.string.raBiosGroupNeoGeoCd, BiosNeed.OPTIONAL),
    THREE_DO(R.string.raBiosGroup3do, BiosNeed.NEEDED, oneEnough = true),
}

/**
 * A BIOS file as RetroArch's cores look for it: [path] inside RetroArch's system folder (with the subfolder a core
 * wants), its [size] and every [md5] documented for it. An empty [md5] means its contents vary (a firmware dump, an
 * arcade set), so only its name can tell. [anyName]: the core recognises it by its contents under any name in its
 * folder. [cores] when only some of the console's cores use it.
 */
data class BiosFile(
    val console: BiosConsole,
    val path: String,
    val need: BiosNeed,
    val size: Long? = null,
    val md5: Set<String> = emptySet(),
    val what: List<BiosWhat> = emptyList(),
    val detail: String? = null,
    val cores: String? = null,
    val group: BiosGroup? = null,
    val anyName: Boolean = false,
) {
    val name: String get() = path.substringAfterLast('/')

    val folder: String get() = path.substringBeforeLast('/', "")

    /** One key per file, whatever the case of its name. */
    val key: String get() = path.lowercase()
}

/**
 * Files a core takes under their own name when they have [size] and a name like [pattern] (LRPS2 any PS2 BIOS in its
 * folder, PCSX-ReARMed any `scph` file). Found ones that match no known hash are offered under their own name in
 * [folder], not checked.
 */
data class BiosLoose(
    val console: BiosConsole,
    val folder: String,
    val size: Long,
    val pattern: Regex,
    val group: BiosGroup,
    val cores: String? = null,
)

/**
 * The BIOS files Thor Tools knows: file names, subfolders, sizes and MD5 hashes as libretro documents them for
 * RetroArch's cores.
 */
object BiosTable {
    private const val KB = 1024L
    private const val MB = 1024L * KB

    private val ps1Files = listOf(
        BiosFile(
            BiosConsole.PS1,
            "scph5500.bin",
            BiosNeed.REGION,
            512 * KB,
            setOf("8dd7d5296a650fac7319bce665a6a53c"),
            listOf(BiosWhat.JAPAN),
        ),
        BiosFile(
            BiosConsole.PS1,
            "scph5501.bin",
            BiosNeed.REGION,
            512 * KB,
            setOf("490f666e1afb15b7362b406ed1cea246"),
            listOf(BiosWhat.NORTH_AMERICA),
        ),
        BiosFile(
            BiosConsole.PS1,
            "scph5502.bin",
            BiosNeed.REGION,
            512 * KB,
            setOf("32736f17079d0b2b7024407c39bd3050"),
            listOf(BiosWhat.EUROPE),
        ),
        BiosFile(
            BiosConsole.PS1,
            "psxonpsp660.bin",
            BiosNeed.OPTIONAL,
            512 * KB,
            setOf("c53ca5908936d412331790f4426c6c33"),
            listOf(BiosWhat.REGION_FREE_PSP),
            group = BiosGroup.PS1_REGION_FREE,
        ),
        BiosFile(
            BiosConsole.PS1,
            "ps1_rom.bin",
            BiosNeed.OPTIONAL,
            512 * KB,
            setOf("81bbe60ba7a3d1cea1d48c14cbcc647b"),
            listOf(BiosWhat.REGION_FREE_PS3),
            group = BiosGroup.PS1_REGION_FREE,
        ),
        ps1Other("scph1001.bin", "924e392ed05558ffdb115408c263dccf", BiosWhat.NORTH_AMERICA, "v2.0"),
        ps1Other("scph7001.bin", "1e68c231d0896b7eadcad1d7d8e76129", BiosWhat.NORTH_AMERICA, "v4.1"),
        ps1Other("scph101.bin", "6e3735ff4c7dc899ee98981385f6f3d0", BiosWhat.NORTH_AMERICA, "v4.4"),
        ps1Other("scph1000.bin", "239665b1a3dade1b5a52c06338011044", BiosWhat.JAPAN, null),
    )

    private val ps2Files = listOf(
        ps2("ps2-0100jd-20000117.bin", "32f2e4d5ff5ee11072a6bc45530f5765"),
        ps2("ps2-0100j-20000117.bin", "acf4730ceb38ac9d8c7d8e21f2614600"),
        ps2("ps2-0101jd-20000217.bin", "acf9968c8f596d2b15f42272082513d1"),
        ps2("ps2-0101j-20000217.bin", "b1459d7446c69e3e97e6ace3ae23dd1c"),
        ps2("ps2-0101xd-20000224.bin", "d3f1853a16c2ec18f3cd1ae655213308"),
        ps2("ps2-0110ad-20000727.bin", "63e6fd9b3c72e0d7b920e80cf76645cd"),
        ps2("ps2-0110a-20000727.bin", "a20c97c02210f16678ca3010127caf36"),
        ps2("ps2-0120a-20000902.bin", "8db2fbbac7413bf3e7154c1e0715e565"),
        ps2("ps2-0120ed-20000902.bin", "91c87cb2f2eb6ce529a2360f80ce2457"),
        ps2("ps2-0120ed-20000902-20030110.bin", "3016b3dd42148a67e2c048595ca4d7ce"),
        ps2("ps2-0120e-20000902.bin", "b7fa11e87d51752a98b38e3e691cbf17"),
        ps2("ps2-0120j-20001027-185015.bin", "f63bc530bd7ad7c026fcd6f7bd0d9525"),
        ps2("ps2-0120j-20001027-191435.bin", "cee06bd68c333fc5768244eae77e4495"),
        ps2("ps2-0150ad-20001228-20030520.bin", "0bf988e9c7aaa4c051805b0fa6eb3387"),
        ps2("ps2-0150a-20001228.bin", "8accc3c49ac45f5ae2c5db0adc854633"),
        ps2("ps2-0150ed-20001228-20030520.bin", "6f9a6feb749f0533aaae2cc45090b0ed"),
        ps2("ps2-0150e-20001228.bin", "838544f12de9b0abc90811279ee223c8"),
        ps2("ps2-0150jd-20010118.bin", "bb6bbc850458fff08af30e969ffd0175"),
        ps2("ps2-0150j-20010118.bin", "815ac991d8bc3b364696bead3457de7d"),
        ps2("ps2-0160a-20010427.bin", "b107b5710042abe887c0f6175f6e94bb"),
        ps2("ps2-0160j-20010427.bin", "ab55cceea548303c22c72570cfd4dd71"),
        ps2("ps2-0160a-20010704.bin", "18bcaadb9ff74ed3add26cdf709fff2e"),
        ps2("ps2-0160e-20010704.bin", "491209dd815ceee9de02dbbc408c06d6"),
        ps2("ps2-0160a-20011004.bin", "7200a03d51cacc4c14fcdfdbc4898431"),
        ps2("ps2-0160e-20011004.bin", "8359638e857c8bc18c3c18ac17d9cc3c"),
        ps2("ps2-0160h-20010730.bin", "352d2ff9b3f68be7e6fa7e6dd8389346"),
        ps2("ps2-0160a-20020207.bin", "d5ce2c7d119f563ce04bc04dbc3a323e"),
        ps2("ps2-0160e-20020319.bin", "0d2228e6fd4fb639c9c39d077a9ec10c"),
        ps2("ps2-0160j-20020426.bin", "72da56fccb8fcd77bba16d1b6f479914"),
        ps2("ps2-0160e-20020426.bin", "5b1f47fbeb277c6be2fccdd6344ff2fd"),
        ps2("ps2-0160h-20020426.bin", "315a4003535dfda689752cb25f24785c"),
        ps2("ps2-0170j-20030206.bin", "312ad4816c232a9606e56f946bc0678a"),
        ps2("ps2-0170ed-20030227.bin", "666018ffec65c5c7e04796081295c6c7"),
        ps2("ps2-0170e-20030227.bin", "6e69920fa6eef8522a1d688a11e41bc6"),
        ps2("ps2-0170ad-20030325.bin", "eb960de68f0c0f7f9fa083e9f79d0360"),
        ps2("ps2-0170a-20030325.bin", "8aa12ce243210128c5074552d3b86251"),
        ps2("ps2-0180cd-20030224.bin", "240d4c5ddd4b54069bdc4a3cd2faf99d"),
        ps2("ps2-0180j-20031028.bin", "1c6cd089e6c83da618fbf2a081eb4888"),
        ps2("ps2-0190j-20030623.bin", "463d87789c555a4a7604e97d7db545d1"),
        ps2("ps2-0190a-20030623.bin", "35461cecaa51712b300b2d6798825048"),
        ps2("ps2-0190e-20030623.bin", "bd6415094e1ce9e05daabe85de807666"),
        ps2("ps2-0190h-20030623.bin", "2e70ad008d4ec8549aada8002fdf42fb"),
        ps2("ps2-0190r-20030623.bin", "b53d51edc7fc086685e31b811dc32aad"),
        ps2("ps2-0190c-20030623.bin", "1b6e631b536247756287b916f9396872"),
        ps2("ps2-0190j-20030822.bin", "00da1b177096cfd2532c8fa22b43e667"),
        ps2("ps2-0190e-20030822.bin", "afde410bd026c16be605a1ae4bd651fd"),
        ps2("ps2-0190a-20040329.bin", "81f4336c1de607dd0865011c0447052e"),
        ps2("ps2-0200j-20040614.bin", "0eee5d1c779aa50e94edd168b4ebf42e"),
        ps2("ps2-0200a-20040614.bin", "d333558cc14561c1fdc334c75d5f37b7"),
        ps2("ps2-0200e-20040614.bin", "dc752f160044f2ed5fc1f4964db2a095"),
        ps2("ps2-0200ed-20040614.bin", "63ead1d74893bf7f36880af81f68a82d"),
        ps2("ps2-0200h-20040614.bin", "3e3e030c0f600442fa05b94f87a1e238"),
        ps2("ps2-0210j-20040917.bin", "1ad977bb539fc9448a08ab276a836bbc"),
        ps2("ps2-0220j-20050620.bin", "eb4f40fcf4911ede39c1bbfe91e7a89a"),
        ps2("ps2-0220ad-20050620.bin", "9959ad7a8685cad66206e7752ca23f8b"),
        ps2("ps2-0220a-20050620.bin", "929a14baca1776b00869f983aa6e14d2"),
        ps2("ps2-0220e-20050620.bin", "573f7d4a430c32b3cc0fd0c41e104bbd"),
        ps2("ps2-0220h-20050620.bin", "df63a604e8bff5b0599bd1a6c2721bd0"),
        ps2("ps2-0220j-20060210.bin", "5b1ba4bb914406fae75ab8e38901684d"),
        ps2("ps2-0220a-20060210.bin", "cb801b7920a7d536ba07b6534d2433ca"),
        ps2("ps2-0220e-20060210.bin", "af60e6d1a939019d55e5b330d24b1c25"),
        ps2("ps2-0220h-20060210.bin", "549a66d0c698635ca9fa3ab012da7129"),
        ps2("ps2-0220j-20060905.bin", "5de9d0d730ff1e7ad122806335332524"),
        ps2("ps2-0220ad-20060905.bin", "21fe4cad111f7dc0f9af29477057f88d"),
        ps2("ps2-0220a-20060905.bin", "40c11c063b3b9409aa5e4058e984e30c"),
        ps2("ps2-0220e-20060905.bin", "80bbb237a6af9c611df43b16b930b683"),
        ps2("ps2-0220h-20060905.bin", "c37bce95d32b2be480f87dd32704e664"),
        ps2("ps2-0230j-20080220.bin", "80ac46fa7e77b8ab4366e86948e54f83"),
        ps2("ps2-0230a-20080220.bin", "21038400dc633070a78ad53090c53017"),
        ps2("ps2-0230e-20080220.bin", "dc69f0643a3030aaa4797501b483d6c4"),
        ps2("ps2-0230h-20080220.bin", "30d56e79d89fbddf10938fa67fe3f34e"),
        ps2("ps2-0250e-20100415.bin", "93ea3bcee4252627919175ff1b16a1d9"),
        ps2("ps2-0250j-20100415.bin", "d3e81e95db25f5a86a7b7474550a2155"),
    )

    private val sega = listOf(
        BiosFile(
            BiosConsole.SEGA_CD,
            "bios_CD_U.bin",
            BiosNeed.REGION,
            128 * KB,
            setOf("854b9150240a198070150e4566ae1290", "2efd74e3232ff260e371b99f84024f7f"),
            listOf(BiosWhat.NORTH_AMERICA),
        ),
        BiosFile(
            BiosConsole.SEGA_CD,
            "bios_CD_E.bin",
            BiosNeed.REGION,
            128 * KB,
            setOf("e66fa1dc5820d254611fdcdba0662372"),
            listOf(BiosWhat.EUROPE),
        ),
        BiosFile(
            BiosConsole.SEGA_CD,
            "bios_CD_J.bin",
            BiosNeed.REGION,
            128 * KB,
            setOf("278a9397d192149e84e820ac621a8edd"),
            listOf(BiosWhat.JAPAN),
        ),
        segaBoot("bios_U.sms", 8 * KB, setOf("840481177270d5642a14ca71ee72844c"), BiosWhat.MS_BOOT, BiosWhat.NORTH_AMERICA),
        segaBoot("bios_E.sms", 8 * KB, setOf("840481177270d5642a14ca71ee72844c"), BiosWhat.MS_BOOT, BiosWhat.EUROPE),
        segaBoot("bios_J.sms", 8 * KB, setOf("24a519c53f67b00640d0048ef7089105"), BiosWhat.MS_BOOT, BiosWhat.JAPAN),
        segaBoot("bios.gg", 1 * KB, setOf("672e104c3be3a238301aceffc3b23fd6"), BiosWhat.GG_BOOT),
        segaBoot("bios_MD.bin", 2 * KB, setOf("45e298905a08f9cfb38fd504cd6dbc84", "d3293ebaaa7f4eb2a6766b68a0fb4609"), BiosWhat.MD_BOOT),
        BiosFile(
            BiosConsole.SATURN,
            "sega_101.bin",
            BiosNeed.REGION,
            512 * KB,
            setOf("85ec9ca47d8f6807718151cbcca8b964"),
            listOf(BiosWhat.JAPAN),
            cores = "Beetle Saturn",
        ),
        BiosFile(
            BiosConsole.SATURN,
            "mpr-17933.bin",
            BiosNeed.REGION,
            512 * KB,
            setOf("3240872c70984b6cbfda1586cab68dbe"),
            listOf(BiosWhat.NORTH_AMERICA_EUROPE),
            cores = "Beetle Saturn",
        ),
        BiosFile(
            BiosConsole.SATURN,
            "saturn_bios.bin",
            BiosNeed.OPTIONAL,
            512 * KB,
            setOf("af5828fdff51384f99b3c4926be27762"),
            cores = "Yabause, YabaSanshiro",
        ),
        BiosFile(
            BiosConsole.SATURN,
            "kronos/saturn_bios.bin",
            BiosNeed.NEEDED,
            512 * KB,
            setOf("af5828fdff51384f99b3c4926be27762"),
            cores = "Kronos",
        ),
        BiosFile(BiosConsole.DREAMCAST, "dc/dc_boot.bin", BiosNeed.OPTIONAL, 2 * MB, setOf("e10c53c2f8b90bab96ead2d368858623")),
        BiosFile(BiosConsole.DREAMCAST, "dc/naomi.zip", BiosNeed.OPTIONAL, what = listOf(BiosWhat.NAOMI)),
        BiosFile(BiosConsole.DREAMCAST, "dc/awbios.zip", BiosNeed.OPTIONAL, what = listOf(BiosWhat.ATOMISWAVE)),
    )

    private val nintendo = listOf(
        BiosFile(BiosConsole.GBA, "gba_bios.bin", BiosNeed.OPTIONAL, 16 * KB, setOf("a860e8c0b6d573d191e4ec7db1b1e4f6")),
        gb("gb_bios.bin", 256, "32fbbd84168d3482956eb3c5051637f5", BiosWhat.GB_BOOT, "mGBA, Gambatte, VBA-M"),
        gb("gbc_bios.bin", 2304, "dbfce9db9deaa2567f6a84fde55f9680", BiosWhat.GBC_BOOT, "mGBA, Gambatte, VBA-M"),
        gb("dmg_boot.bin", 256, "32fbbd84168d3482956eb3c5051637f5", BiosWhat.GB_BOOT, "SameBoy, Gearboy"),
        gb("cgb_boot.bin", 2304, "dbfce9db9deaa2567f6a84fde55f9680", BiosWhat.GBC_BOOT, "SameBoy, Gearboy"),
        gb("sgb_bios.bin", 256, "d574d4f9c12f305074798f54c091a8b4", BiosWhat.SGB_BOOT, "mGBA"),
        BiosFile(
            BiosConsole.NDS,
            "bios7.bin",
            BiosNeed.OPTIONAL,
            16 * KB,
            setOf("df692a80a5b1bc90728bc3dfc76cd948"),
            listOf(BiosWhat.ARM7),
        ),
        BiosFile(BiosConsole.NDS, "bios9.bin", BiosNeed.OPTIONAL, 4 * KB, setOf("a392174eb3e572fed6447e956bde4b25"), listOf(BiosWhat.ARM9)),
        BiosFile(BiosConsole.NDS, "firmware.bin", BiosNeed.OPTIONAL, what = listOf(BiosWhat.FIRMWARE)),
        dsi("dsi_bios7.bin", 64 * KB, "559dae4ea78eb9d67702c56c1d791e81", BiosWhat.ARM7),
        dsi("dsi_bios9.bin", 64 * KB, "87b665fce118f76251271c3732532777", BiosWhat.ARM9),
        dsi("dsi_firmware.bin", null, null, BiosWhat.FIRMWARE),
        dsi("dsi_nand.bin", null, null, BiosWhat.NAND),
        BiosFile(BiosConsole.FDS, "disksys.rom", BiosNeed.NEEDED, 8 * KB, setOf("ca30b50f880eb660a320674ed365ef7a")),
        BiosFile(
            BiosConsole.N64DD,
            "Mupen64plus/IPL.n64",
            BiosNeed.OPTIONAL,
            4 * MB,
            setOf("8d3d9f294b6e174bc7b1d2fd1c727530"),
            cores = "Mupen64Plus-Next",
        ),
        BiosFile(
            BiosConsole.N64DD,
            "64DD_IPL.bin",
            BiosNeed.OPTIONAL,
            4 * MB,
            setOf("8d3d9f294b6e174bc7b1d2fd1c727530"),
            cores = "ParaLLEl N64",
        ),
        BiosFile(BiosConsole.POKEMON_MINI, "bios.min", BiosNeed.OPTIONAL, 4 * KB, setOf("1e4fb124a3a886865acb574f388c803d")),
    )

    private val others = listOf(
        BiosFile(
            BiosConsole.PCE_CD,
            "syscard3.pce",
            BiosNeed.NEEDED,
            256 * KB,
            setOf("38179df8f4ac870017db21ebcbf53114"),
            detail = "Super CD-ROM² System 3",
        ),
        pce("syscard2.pce", 256 * KB, "3cdd6614a918616bfc41c862e889dd79", "CD-ROM² System 2"),
        pce("syscard1.pce", 256 * KB, "2b7ccb3d86baa18f6402c176f3065082", "CD-ROM² System 1"),
        pce("gexpress.pce", 32 * KB, "6d2cb14fc3e1f65ceb135633d1694122", "Game Express CD Card"),
        BiosFile(BiosConsole.PC_FX, "pcfx.rom", BiosNeed.NEEDED, 1 * MB, setOf("08e36edbea28a017f79f8d4f7ff9b6d7")),
        BiosFile(BiosConsole.NEO_GEO, "fbneo/neogeo.zip", BiosNeed.NEEDED, cores = "FinalBurn Neo"),
        BiosFile(BiosConsole.NEO_GEO, "neogeo.zip", BiosNeed.NEEDED, cores = "Geolith"),
        neoGeoCd("neocd_f.rom", "8834880c33164ccbe6476b559f3e37de", "Front Loader"),
        neoGeoCd("neocd_sf.rom", "043d76d5f0ef836500700c34faef774d", "Front Loader, SMKDAN"),
        neoGeoCd("front-sp1.bin", "5c2366f25ff92d71788468ca492ebeca", "Front Loader, MAME"),
        neoGeoCd("neocd_t.rom", "de3cf45d227ad44645b22aa83b49f450", "Top Loader"),
        neoGeoCd("neocd_st.rom", "f6325a33c6d63ea4b9162a3fa8c32727", "Top Loader, SMKDAN"),
        neoGeoCd("top-sp1.bin", "122aee210324c72e8a11116e6ef9c0d0", "Top Loader, MAME"),
        neoGeoCd("neocd_z.rom", "11526d58d4c524daef7d5d677dc6b004", "CDZ"),
        neoGeoCd("neocd_sz.rom", "971ee8a36fb72da57aed01758f0a37f5", "CDZ, SMKDAN"),
        neoGeoCd("neocd.bin", "f39572af7584cb5b3f70ae8cc848aba2", "CDZ, MAME"),
        neoGeoCd("uni-bioscd.rom", "08ca8b2dba6662e8024f9e789711c6fc", "Universe BIOS CD 3.3"),
        BiosFile(BiosConsole.LYNX, "lynxboot.img", BiosNeed.NEEDED, 512, setOf("fcd403db69f54290b51035d82f835e7b")),
        threeDo("panafz1.bin", 1 * MB, "f47264dd47fe30f73ab3c010015c155b", "Panasonic FZ-1"),
        threeDo("panafz10.bin", 1 * MB, "51f2f43ae2f3508a14d9f56597e2d3ce", "Panasonic FZ-10"),
        threeDo("panafz10-norsa.bin", 1 * MB, "1477bda80dc33731a65468c1f5bcbee9", "Panasonic FZ-10, RSA patch"),
        threeDo("panafz10e-anvil.bin", 1 * MB, "a48e6746bd7edec0f40cff078f0bb19f", "Panasonic FZ-10-E Anvil"),
        threeDo("panafz10e-anvil-norsa.bin", 1 * MB, "cf11bbb5a16d7af9875cca9de9a15e09", "Panasonic FZ-10-E Anvil, RSA patch"),
        threeDo("panafz1j.bin", 1 * MB, "a496cfdded3da562759be3561317b605", "Panasonic FZ-1J"),
        threeDo("panafz1j-norsa.bin", 1 * MB, "f6c71de7470d16abe4f71b1444883dc8", "Panasonic FZ-1J, RSA patch"),
        threeDo("goldstar.bin", 1 * MB, "8639fd5e549bd6238cfee79e3e749114", "Goldstar GDO-101M"),
        threeDo("sanyotry.bin", 1 * MB, "35fa1a1ebaaeea286dc5cd15487c13ea", "Sanyo IMP-21J TRY"),
        threeDo("3do_arcade_saot.bin", 512 * KB, "8970fc987ab89a7f64da9f8a8c4333ff", "Shootout At Old Tucson"),
        BiosFile(
            BiosConsole.ATARI_7800,
            "7800 BIOS (U).rom",
            BiosNeed.OPTIONAL,
            4 * KB,
            setOf("0763f1ffb006ddbe32e52d497ee848ae"),
            listOf(BiosWhat.NORTH_AMERICA),
        ),
        BiosFile(BiosConsole.ATARI_5200, "5200.rom", BiosNeed.NEEDED, 2 * KB, setOf("281f20ea4320404ec820fb7ec0693b38")),
        BiosFile(
            BiosConsole.INTELLIVISION,
            "exec.bin",
            BiosNeed.NEEDED,
            8 * KB,
            setOf("62e761035cb657903761800f4437b8af"),
            listOf(BiosWhat.EXEC),
        ),
        BiosFile(
            BiosConsole.INTELLIVISION,
            "grom.bin",
            BiosNeed.NEEDED,
            2 * KB,
            setOf("0cd5946c6473e42e8e4c2137785e427f"),
            listOf(BiosWhat.GROM),
        ),
    )

    /** Every known file, console by console. */
    val files: List<BiosFile> = (ps1Files + ps2Files + sega + nintendo + others).sortedBy { it.console.ordinal }

    val loose: List<BiosLoose> = listOf(
        BiosLoose(BiosConsole.PS1, "", 512 * KB, Regex("^scph.*\\.bin$", RegexOption.IGNORE_CASE), BiosGroup.PS1_OTHER, "PCSX-ReARMed"),
        BiosLoose(BiosConsole.PS2, "pcsx2/bios", 4 * MB, Regex("^(scph|ps2).*\\.bin$", RegexOption.IGNORE_CASE), BiosGroup.PS2),
    )

    private fun ps1Other(name: String, md5: String, region: BiosWhat, version: String?) = BiosFile(
        BiosConsole.PS1,
        name,
        BiosNeed.OPTIONAL,
        512 * KB,
        setOf(md5),
        listOf(region),
        detail = version,
        cores = "PCSX-ReARMed",
        group = BiosGroup.PS1_OTHER,
    )

    private fun ps2(name: String, md5: String) =
        BiosFile(BiosConsole.PS2, "pcsx2/bios/$name", BiosNeed.NEEDED, 4 * MB, setOf(md5), group = BiosGroup.PS2, anyName = true)

    private fun segaBoot(name: String, size: Long, md5: Set<String>, vararg what: BiosWhat) =
        BiosFile(BiosConsole.SEGA_BOOT, name, BiosNeed.OPTIONAL, size, md5, what.toList())

    private fun gb(name: String, size: Long, md5: String, what: BiosWhat, cores: String) =
        BiosFile(BiosConsole.GB, name, BiosNeed.OPTIONAL, size, setOf(md5), listOf(what), cores = cores)

    private fun dsi(name: String, size: Long?, md5: String?, what: BiosWhat) = BiosFile(
        BiosConsole.NDS,
        name,
        BiosNeed.OPTIONAL,
        size,
        setOfNotNull(md5),
        listOf(BiosWhat.DSI, what),
        cores = "melonDS DS, melonDS",
        group = BiosGroup.DSI,
    )

    private fun pce(name: String, size: Long, md5: String, model: String) =
        BiosFile(BiosConsole.PCE_CD, name, BiosNeed.OPTIONAL, size, setOf(md5), detail = model, group = BiosGroup.PCE_OTHER)

    private fun neoGeoCd(name: String, md5: String, model: String) = BiosFile(
        BiosConsole.NEO_GEO_CD,
        "neocd/$name",
        BiosNeed.OPTIONAL,
        512 * KB,
        setOf(md5),
        detail = model,
        group = BiosGroup.NEO_GEO_CD,
        anyName = true,
    )

    private fun threeDo(name: String, size: Long, md5: String, model: String) =
        BiosFile(BiosConsole.THREE_DO, name, BiosNeed.NEEDED, size, setOf(md5), detail = model, group = BiosGroup.THREE_DO)
}
