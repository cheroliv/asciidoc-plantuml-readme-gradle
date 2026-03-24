package readme

import java.io.File

/**
 * Représente un fichier README_truth{_lang}.adoc — source de vérité unique.
 *
 * Conventions :
 *   README_truth.adoc      → lang = null  → générera README.adoc
 *   README_truth_fr.adoc   → lang = "fr"  → générera README_fr.adoc
 *   README_truth_de.adoc   → lang = "de"  → générera README_de.adoc
 */
data class AdocSourceFile(val file: File) {

    companion object {

        private val SOURCE_PATTERN = Regex("""^(.+)_truth(?:_([a-z]{2}))?$""")

        fun isSourceOfTruth(file: File): Boolean =
            file.extension == "adoc"
                    && SOURCE_PATTERN.containsMatchIn(file.nameWithoutExtension)

        fun scanDir(dir: File): List<AdocSourceFile> =
            dir.listFiles()
                ?.filter { isSourceOfTruth(it) }
                ?.map    { AdocSourceFile(it) }
                ?: emptyList()
    }

    private val match = SOURCE_PATTERN
        .find(file.nameWithoutExtension)
        ?: error("${file.name} n'est pas un fichier _truth.adoc valide")

    val baseName: String = match.groupValues[1]
    val lang: String?    = match.groupValues[2].ifEmpty { null }

    fun effectiveLang(default: String): String = lang ?: default

    fun generatedFileName(): String =
        if (lang != null) "${baseName}_${lang}.adoc"
        else              "${baseName}.adoc"

    fun generatedFile(): File = File(file.parentFile, generatedFileName())
}