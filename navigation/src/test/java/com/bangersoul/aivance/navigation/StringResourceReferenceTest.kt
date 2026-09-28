package com.bangersoul.aivance.navigation

import com.google.common.truth.Truth.assertWithMessage
import org.junit.Test
import java.io.File

/**
 * Guards the workspace modules' string resources against silent rot.
 *
 * An unreferenced `<string>` is invisible to both the compiler and the runtime, so dead
 * labels accumulate unnoticed. The subtractions themselves caused most of it: the AUDIT
 * §3.2 workspace prune, the providers merge, the Export row and the About Resources card
 * each deleted UI without deleting the strings that labelled it, and Prep Studio left its
 * whole session vocabulary behind in `feature/interview` when those screens moved out of
 * `navigation`.
 *
 * Two invariants are enforced per guarded module, both against the **whole repository** —
 * a module's string may legitimately be referenced from `app`, and a locale overlay in one
 * module can legitimately translate a string another module declares:
 *
 *  1. every string declared in the module's default locale is referenced by at least one
 *     `R.string.<name>` / `@string/<name>` site (or a literal `getIdentifier`);
 *  2. every name translated by one of the module's locale overlays (res/values-*) is declared
 *     by *some* default locale in the project, so a translation cannot outlive its string.
 *
 * The remedy for a failure is always to delete the dead resource, never to allowlist it.
 */
class StringResourceReferenceTest {

    @Test
    fun everyDeclaredStringIsReferenced() {
        val sources = repoRoot().sourceFiles().map { it.readText() }.toList()
        assertWithMessage("the source scan found almost nothing — it is probably looking in the wrong place")
            .that(sources.size)
            .isAtLeast(50)

        val referenced = referencedNames(sources)
        val orphans = mutableListOf<String>()
        for (module in GUARDED_MODULES) {
            val declared = declaredNamesIn(defaultLocaleFile(module))
            assertWithMessage("$module declares no strings — this guard would pass vacuously for it")
                .that(declared)
                .isNotEmpty()
            assertWithMessage(
                "canary failed for $module: the scan cannot see its R.string references, " +
                    "so every resource would look orphaned"
            ).that(declared.any { it in referenced }).isTrue()

            declared.filterNot { it in referenced }.forEach { orphans += "$module  $it" }
        }

        assertWithMessage(
            "String resources declared in a default locale but referenced nowhere in the " +
                "repository. Delete them, or wire them up:\n" +
                orphans.sorted().joinToString("\n") { "  - $it" }
        ).that(orphans).isEmpty()
    }

    @Test
    fun everyLocaleOverlayTargetsAStringSomeDefaultLocaleDeclares() {
        val declaredBySomeDefaultLocale = declaredByEveryDefaultLocale()

        val stale = mutableListOf<String>()
        var translated = 0
        for (module in GUARDED_MODULES) {
            for (name in overlayNames(module)) {
                translated++
                if (name !in declaredBySomeDefaultLocale) stale += "$module  $name"
            }
        }
        assertWithMessage("no locale overlays found — this guard would pass vacuously")
            .that(translated)
            .isAtLeast(1)

        assertWithMessage(
            "Translations in a locale overlay whose string no default locale declares. " +
                "Nothing can ever render them — delete them:\n" +
                stale.sorted().joinToString("\n") { "  - $it" }
        ).that(stale).isEmpty()
    }

    // ---------------------------------------------------------------- location

    private fun repoRoot(): File {
        val start = System.getProperty("user.dir")
            ?: throw AssertionError("The 'user.dir' system property is not set")
        var dir: File? = File(start).absoluteFile
        while (dir != null) {
            if (File(dir, "settings.gradle.kts").isFile) return dir
            dir = dir.parentFile
        }
        throw AssertionError(
            "Could not locate the repository root: no settings.gradle.kts at or above $start"
        )
    }

    private fun defaultLocaleFile(module: String): File =
        File(repoRoot(), "$module/src/main/res/values/strings.xml")

    // ---------------------------------------------------------------- discovery

    private fun declaredByEveryDefaultLocale(): Set<String> =
        repoRoot().resourceFiles { relativePath -> relativePath.contains("/res/values/") }
            .flatMap { file -> declaredNamesIn(file).asSequence() }
            .toSet()

    private fun overlayNames(module: String): Set<String> =
        File(repoRoot(), module)
            .resourceFiles { relativePath -> relativePath.contains("/res/values-") }
            .flatMap { file -> declaredNamesIn(file).asSequence() }
            .toSet()

    private fun declaredNamesIn(file: File): List<String> =
        DECLARATION.findAll(file.readText()).map { it.groupValues[1] }.toList()

    private fun referencedNames(sources: List<String>): Set<String> {
        val referenced = mutableSetOf<String>()
        for (source in sources) {
            QUALIFIED_REFERENCE.findAll(source).forEach { referenced += it.groupValues[1] }
            DYNAMIC_REFERENCE.findAll(source).forEach { referenced += it.groupValues[1] }
        }
        return referenced
    }

    // ---------------------------------------------------------------- file walking

    private fun File.sourceFiles(): Sequence<File> =
        walkSourceTree(keep = { true })

    private fun File.resourceFiles(keepRelativePath: (String) -> Boolean): Sequence<File> =
        walkSourceTree(keep = { file ->
            file.extension == "xml" && keepRelativePath(file.relativePathFrom(this))
        })

    private fun File.walkSourceTree(keep: (File) -> Boolean): Sequence<File> =
        walkTopDown()
            .onEnter { it == this || (it.name !in IGNORED_DIRS && !it.name.startsWith(".")) }
            .filter { it.isFile && (it.extension == "kt" || it.extension == "xml") && keep(it) }

    private fun File.relativePathFrom(root: File): String =
        toRelativeString(root).replace(File.separatorChar, '/')

    private companion object {
        /** Modules whose string resources this guard owns. */
        val GUARDED_MODULES = listOf("navigation", "feature/interview", "feature/profile")

        val IGNORED_DIRS = setOf("build", ".git", ".gradle", ".idea", "node_modules")

        val DECLARATION = Regex("<string name=\"([^\"]+)\"")

        /** `R.string.foo`, `androidx...R.string.foo` and `@string/foo` all funnel into one name. */
        val QUALIFIED_REFERENCE = Regex("""(?:\.string\.|@string/)([A-Za-z_][A-Za-z0-9_]*)""")

        /** A dynamic lookup by literal name, e.g. `getIdentifier("default_web_client_id", ...)`. */
        val DYNAMIC_REFERENCE = Regex("getIdentifier\\([^)]*\"([A-Za-z_][A-Za-z0-9_]*)\"")
    }
}
