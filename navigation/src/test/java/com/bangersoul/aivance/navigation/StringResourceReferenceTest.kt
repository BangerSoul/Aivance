package com.bangersoul.aivance.navigation

import com.google.common.truth.Truth.assertWithMessage
import org.junit.Test
import java.io.File

/**
 * Guards every module's string resources against silent rot.
 *
 * An unreferenced `<string>` is invisible to both the compiler and the runtime, so dead
 * labels accumulate unnoticed. The subtractions caused most of it: the AUDIT §3.2
 * workspace prune, the providers merge, the Export row and the About Resources card each
 * deleted UI without deleting the strings that labelled it, and whole screen vocabularies
 * (Prep Studio, the Analytics Insights entry, the dashboard's Quick Actions) stayed behind
 * when their screens were rewritten.
 *
 * Nothing here is a hand-maintained list. The test discovers from the file tree which
 * modules ship string resources, and which locale overlays exist, so a new module is
 * covered the day it lands. Two invariants are enforced against the **whole repository** —
 * a string may legitimately be referenced from another module, and an overlay may
 * legitimately translate a string another module declares:
 *
 *  1. every string declared by any default locale is referenced by at least one
 *     `R.string.<name>` / `@string/<name>` site (or a literal `getIdentifier`);
 *  2. every name translated by a locale overlay (res/values-*) is declared by *some*
 *     default locale in the project, so a translation cannot outlive its string.
 *
 * The remedy for a failure is always to delete the dead resource, never to allowlist it.
 */
class StringResourceReferenceTest {

    @Test
    fun everyDeclaredStringIsReferenced() {
        val texts = sourcePaths.map { File(root, it).readText() }
        assertWithMessage("the source scan found almost nothing — it is probably looking in the wrong place")
            .that(texts.size)
            .isAtLeast(50)

        val referenced = referencedNames(texts)
        val modules = modulesWithStringResources()
        assertWithMessage("no module declares string resources — this guard would pass vacuously")
            .that(modules)
            .isNotEmpty()

        val declared = modules.associateWith { declaredInDefaultLocale(it) }
        val allDeclared = declared.values.flatten()
        val resolvable = allDeclared.count { it in referenced }
        assertWithMessage(
            "canary failed: only $resolvable of ${allDeclared.size} declared names resolve to a " +
                "reference, so the scan is probably not finding references at all"
        ).that(resolvable * 2).isAtLeast(allDeclared.size)

        val orphans = declared.flatMap { (module, names) ->
            names.filterNot { it in referenced }.map { "$module  $it" }
        }
        assertWithMessage(
            "String resources declared by a default locale but referenced nowhere in the " +
                "repository. Delete them, or wire them up:\n" +
                orphans.sorted().joinToString("\n") { "  - $it" }
        ).that(orphans).isEmpty()
    }

    @Test
    fun everyLocaleOverlayTargetsAStringSomeDefaultLocaleDeclares() {
        val declaredAnywhere = modulesWithStringResources()
            .flatMap { declaredInDefaultLocale(it) }
            .toSet()

        val overlays = overlayEntries()
        assertWithMessage("no locale overlays found — this guard would pass vacuously")
            .that(overlays)
            .isNotEmpty()

        val stale = overlays
            .filterNot { (_, name) -> name in declaredAnywhere }
            .map { (path, name) -> "$path  $name" }
        assertWithMessage(
            "Translations in a locale overlay whose string no default locale declares. " +
                "Nothing can ever render them — delete them:\n" +
                stale.sorted().joinToString("\n") { "  - $it" }
        ).that(stale).isEmpty()
    }

    // ---------------------------------------------------------------- discovery

    private val root: File by lazy { repoRoot() }

    private val sourcePaths: List<String> by lazy { root.paths { true } }

    private fun modulesWithStringResources(): List<String> =
        sourcePaths.filter { it.contains("/src/main/res/values/") }
            .map { it.substringBefore("/src/main/res/") }
            .distinct()
            .filter { declaredInDefaultLocale(it).isNotEmpty() }
            .sorted()

    private fun declaredInDefaultLocale(module: String): List<String> =
        declaredNamesIn(sourcePaths.filter { it.startsWith("$module/src/main/res/values/") })

    private fun overlayEntries(): List<Pair<String, String>> =
        sourcePaths.filter { it.contains("/res/values-") }
            .flatMap { path ->
                DECLARATION.findAll(File(root, path).readText()).map { path to it.groupValues[1] }
            }

    private fun declaredNamesIn(paths: List<String>): List<String> =
        paths.flatMap { path ->
            DECLARATION.findAll(File(root, path).readText()).map { it.groupValues[1] }
        }

    private fun referencedNames(sources: List<String>): Set<String> {
        val referenced = mutableSetOf<String>()
        for (source in sources) {
            QUALIFIED_REFERENCE.findAll(source).forEach { referenced += it.groupValues[1] }
            DYNAMIC_REFERENCE.findAll(source).forEach { referenced += it.groupValues[1] }
        }
        return referenced
    }

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

    // ---------------------------------------------------------------- file walking

    private fun File.paths(predicate: (String) -> Boolean): List<String> =
        walkTopDown()
            .onEnter { it == this || (it.name !in IGNORED_DIRS && !it.name.startsWith(".")) }
            .filter { it.isFile && (it.extension == "kt" || it.extension == "xml") }
            .map { it.toRelativeString(this).replace(File.separatorChar, '/') }
            .filter(predicate)
            .toList()

    private companion object {
        val IGNORED_DIRS = setOf("build", ".git", ".gradle", ".idea", "node_modules")

        val DECLARATION = Regex("<string name=\"([^\"]+)\"")

        /** `R.string.foo`, `androidx...R.string.foo` and `@string/foo` all funnel into one name. */
        val QUALIFIED_REFERENCE = Regex("""(?:\.string\.|@string/)([A-Za-z_][A-Za-z0-9_]*)""")

        /** A dynamic lookup by literal name, e.g. `getIdentifier("default_web_client_id", ...)`. */
        val DYNAMIC_REFERENCE = Regex("getIdentifier\\([^)]*\"([A-Za-z_][A-Za-z0-9_]*)\"")
    }
}
