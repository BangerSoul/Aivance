package com.bangersoul.aivance.navigation

import com.google.common.truth.Truth.assertWithMessage
import org.junit.Test
import java.io.File

/**
 * Guards this module's string resources against silent rot.
 *
 * An unreferenced `<string>` is invisible to both the compiler and the runtime, so
 * dead labels accumulate without anyone noticing: the AUDIT §3.2 workspace prune
 * deleted six destinations but left `dest_prep_studio` behind, and the screen strings
 * this module shipped back when it still hosted Prep Studio and the provider manager
 * stayed on as dead copies after those screens moved into their own feature modules.
 *
 * Two invariants are enforced, both against the **whole repository** — a `navigation`
 * string may legitimately be referenced from `app`, and the locale overlays here can
 * legitimately translate a string another module declares:
 *
 *  1. every string declared in `navigation/src/main/res/values/strings.xml` is
 *     referenced by at least one `R.string.<name>` / `@string/<name>` site;
 *  2. every name translated under `navigation/**​/res/values-*/` is declared by *some*
 *     default locale in the project, so a translation can never outlive its string.
 *
 * The remedy for a failure is always to delete the dead resource, never to allowlist it.
 */
class StringResourceReferenceTest {

    @Test
    fun everyDeclaredStringIsReferenced() {
        val declared = declaredNamesIn(defaultLocaleFile())
        assertWithMessage("navigation must declare string resources for this guard to mean anything")
            .that(declared)
            .isNotEmpty()

        val sources = repoRoot().sourceFiles().map { it.readText() }.toList()
        assertWithMessage("the source scan found almost nothing — it is probably looking in the wrong place")
            .that(sources.size)
            .isAtLeast(50)

        val referenced = referencedNames(sources)
        assertWithMessage(
            "canary failed: the scan cannot see this module's own R.string references, " +
                "so every resource would look orphaned"
        ).that(declared.any { it in referenced }).isTrue()

        val orphans = declared.filterNot { it in referenced }.sorted()
        assertWithMessage(
            "String resources declared in navigation/src/main/res/values/strings.xml but " +
                "referenced nowhere in the repository. Delete them, or wire them up:\n" +
                orphans.joinToString("\n") { "  - $it" }
        ).that(orphans).isEmpty()
    }

    @Test
    fun everyLocaleOverlayTargetsAStringSomeDefaultLocaleDeclares() {
        val translated = navigationOverlayNames()
        assertWithMessage("navigation is expected to ship locale overlays; this guard would pass vacuously")
            .that(translated)
            .isNotEmpty()

        val declaredBySomeDefaultLocale = declaredByEveryDefaultLocale()
        val stale = translated.filterNot { it in declaredBySomeDefaultLocale }.sorted()
        assertWithMessage(
            "Translations in navigation's locale overlays whose string no default locale declares. " +
                "Nothing can ever render them — delete them:\n" +
                stale.joinToString("\n") { "  - $it" }
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

    private val navigationRoot: File get() = File(repoRoot(), "navigation")

    private fun defaultLocaleFile(): File =
        File(navigationRoot, "src/main/res/values/strings.xml").also {
            check(it.isFile) { "missing ${it.path}" }
        }

    // ---------------------------------------------------------------- discovery

    private fun declaredByEveryDefaultLocale(): Set<String> =
        repoRoot().resourceFiles { relativePath -> relativePath.contains("/res/values/") }
            .flatMap { file -> declaredNamesIn(file).asSequence() }
            .toSet()

    private fun navigationOverlayNames(): Set<String> =
        navigationRoot.resourceFiles { relativePath -> relativePath.contains("/res/values-") }
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
        val IGNORED_DIRS = setOf("build", ".git", ".gradle", ".idea", "node_modules")

        val DECLARATION = Regex("<string name=\"([^\"]+)\"")

        /** `R.string.foo`, `androidx...R.string.foo` and `@string/foo` all funnel into one name. */
        val QUALIFIED_REFERENCE = Regex("""(?:\.string\.|@string/)([A-Za-z_][A-Za-z0-9_]*)""")

        /** A dynamic lookup by literal name, e.g. `getIdentifier("default_web_client_id", ...)`. */
        val DYNAMIC_REFERENCE = Regex("getIdentifier\\([^)]*\"([A-Za-z_][A-Za-z0-9_]*)\"")
    }
}
