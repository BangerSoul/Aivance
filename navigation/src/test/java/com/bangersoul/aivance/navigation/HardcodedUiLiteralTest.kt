package com.bangersoul.aivance.navigation

import com.google.common.truth.Truth.assertWithMessage
import org.junit.Test
import java.io.File

/**
 * Guards every composable against rendering a hardcoded user-visible literal.
 *
 * A `Text("Save")` compiles, runs, and looks finished — it just never speaks Hindi.
 * Unlike a missing resource, nothing downstream catches it: the string never enters
 * the resource tables that [StringResourceReferenceTest] guards, so orphans of this
 * kind are invisible to both the compiler and that test. This guard closes the gap
 * by scanning the Compose sources themselves.
 *
 * What counts as a violation: a string literal placed directly (top level) inside
 * a render site — the `Text(...)` call, `Toast.makeText(...)`, or a named argument
 * named `text`, `title`, `label`, `placeholder`, `description` or
 * `contentDescription`. Those are the shapes through which copy actually reaches
 * the screen. The remedy is always the same: extract the copy into the module's
 * `strings.xml` (both locales) and render it with `stringResource`. There is no
 * allowlist.
 *
 * What is deliberately exempt, each for a stated reason:
 *
 *  - `@Preview`-only composables: IDE chrome, never shipped.
 *  - literals inside a `stringResource(...)` / `pluralStringResource(...)` call:
 *    those are resource *names*, not rendered copy.
 *  - interpolation-only templates such as `"$score%"` or
 *    `"${greetingForTime()}, $name"`: every letter they render comes from a
 *    resource or data; there is nothing left to translate.
 *  - `label =` / `label:` arguments of the animation APIs (`AnimatedContent`,
 *    `animate*AsState`, `updateTransition`, `rememberInfiniteTransition`,
 *    `transition.animateFloat`, …): tooling identifiers, never rendered.
 *  - non-composable files: repositories, engines, providers and ViewModels build
 *    LLM prompts, analytics payloads and data defaults; their strings are not
 *    rendered by the file that declares them. When a ViewModel string does reach
 *    the screen it flows through a variable, which this scan does not follow —
 *    that gap is accepted, because the UI side of every such flow is still
 *    guarded at the render site.
 *
 * Two canaries keep the scan honest: if the file walk finds suspiciously few
 * composable sources, or the trigger regexes fire suspiciously rarely, the test
 * reports itself instead of masquerading as a clean project.
 */
class HardcodedUiLiteralTest {

    @Test
    fun everyUserVisibleLiteralInComposablesIsAResource() {
        val parsed = composableSources()
        assertWithMessage(
            "the source scan found almost nothing — it is probably looking in the wrong place"
        ).that(parsed.size).isAtLeast(40)

        val triggerSites = parsed.sumOf { it.triggers.size }
        assertWithMessage(
            "the render-site scan fired almost never — the trigger patterns are probably stale"
        ).that(triggerSites).isAtLeast(250)

        val violations = parsed
            .flatMap { it.violations() }
            .distinct()
            .sorted()

        assertWithMessage(
            "Composables that render hardcoded user-visible literals instead of string " +
                "resources. Extract the copy into the module's values/strings.xml (and " +
                "values-hi/), render it with stringResource, and re-run. Never allowlist:\n" +
                violations.joinToString("\n") { "  - $it" }
        ).that(violations).isEmpty()
    }

    // ---------------------------------------------------------------- discovery

    private fun composableSources(): List<ParsedSource> {
        val root = repoRoot()
        return root.paths()
            .filter { it.contains("/src/main/") }
            .map { path ->
                val raw = File(root, path).readText()
                ParsedSource(path, raw)
            }
            .filter { it.masked.contains("@Composable") }
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

    private fun File.paths(): List<String> =
        walkTopDown()
            .onEnter { it == this || (it.name !in IGNORED_DIRS && !it.name.startsWith(".")) }
            .filter { it.isFile && it.extension == "kt" }
            .map { it.toRelativeString(this).replace(File.separatorChar, '/') }
            .toList()

    // ------------------------------------------------------- per-file analysis

    /**
     * One parsed source file. [masked] is the source with comments and string
     * contents blanked (length-preserving, newlines kept), so bracket matching and
     * regex triggers run over code structure only; [literals] are the raw string
     * literals with their positions in the original text.
     */
    private class ParsedSource(val path: String, val raw: String) {

        val masked: String
        val literals: List<Literal>
        val triggers: List<Trigger>

        private val previewSpans: List<IntRange>
        private val resourceSpans: List<IntRange>

        init {
            val tokenized = tokenize(raw)
            masked = tokenized.first
            literals = tokenized.second
            previewSpans = findPreviewSpans()
            resourceSpans = findResourceSpans()
            triggers = findTriggers()
        }

        fun violations(): List<String> {
            val found = mutableListOf<String>()
            for (trigger in triggers) {
                for (literal in literals) {
                    if (literal.start <= trigger.start || literal.start >= trigger.end) continue
                    if (previewSpans.any { literal.start in it }) continue
                    if (resourceSpans.any { literal.start in it }) continue
                    if (!isTopLevelWithin(trigger, literal.start)) continue
                    if (!literal.hasRenderableLetters()) continue
                    if (isAnimationLabel(literal.start)) continue
                    val line = raw.substring(0, literal.start).count { it == '\n' } + 1
                    found += "$path:$line: \"${literal.value}\""
                }
            }
            return found
        }

        /** True when only brackets sit between the trigger opening and the literal. */
        private fun isTopLevelWithin(trigger: Trigger, literalStart: Int): Boolean {
            var depth = 0
            for (j in trigger.start + 1 until literalStart) {
                when (masked[j]) {
                    '(', '{', '[' -> depth++
                    ')', '}', ']' -> depth--
                }
            }
            return depth == 0
        }

        private fun isAnimationLabel(literalStart: Int): Boolean {
            val head = enclosingCallHead(literalStart) ?: return false
            return ANIMATION_APIS.containsMatchIn(head)
        }

        /**
         * The identifier chain immediately before the innermost unclosed `(` that
         * encloses [index], e.g. `AnimatedContent` for a `label =` argument.
         */
        private fun enclosingCallHead(index: Int): String? {
            var depth = 0
            var k = index - 1
            while (k >= 0) {
                when (masked[k]) {
                    ')', '}', ']' -> depth++
                    '(', '{', '[' ->
                        if (depth == 0) {
                            val from = maxOf(0, k - 80)
                            val head = masked.substring(from, k)
                            return CALLEE_HEAD.find(head)?.groupValues?.get(1)
                        } else {
                            depth--
                        }
                }
                k--
            }
            return null
        }

        private fun findPreviewSpans(): List<IntRange> {
            val spans = mutableListOf<IntRange>()
            for (match in PREVIEW_ANNOTATION.findAll(masked)) {
                val open = masked.indexOf('{', match.range.first)
                if (open == -1) continue
                var depth = 0
                for (j in open until masked.length) {
                    when (masked[j]) {
                        '{' -> depth++
                        '}' -> {
                            depth--
                            if (depth == 0) {
                                spans += match.range.first..j
                                break
                            }
                        }
                    }
                }
            }
            return spans
        }

        private fun findResourceSpans(): List<IntRange> {
            val spans = mutableListOf<IntRange>()
            for (match in RESOURCE_CALL.findAll(masked)) {
                val open = match.range.last
                spans += open..balancedClose(open)
            }
            return spans
        }

        private fun findTriggers(): List<Trigger> {
            val found = mutableListOf<Trigger>()
            for (pattern in CALL_TRIGGERS) {
                for (match in pattern.findAll(masked)) {
                    val open = match.range.last
                    found += Trigger(open, balancedClose(open))
                }
            }
            for (match in NAMED_ARG.findAll(masked)) {
                val before = masked.substring(maxOf(0, match.range.first - 30), match.range.first)
                if (VAL_OR_VAR_BEFORE.containsMatchIn(before)) continue
                val afterEquals = match.range.last + 1
                found += Trigger(afterEquals, namedArgumentEnd(afterEquals))
            }
            return found
        }

        private fun balancedClose(open: Int): Int {
            var depth = 0
            for (j in open until masked.length) {
                when (masked[j]) {
                    '(', '{', '[' -> depth++
                    ')', '}', ']' -> {
                        depth--
                        if (depth == 0) return j
                    }
                }
            }
            return masked.length - 1
        }

        /** First top-level `,` or the enclosing closer — the argument's extent. */
        private fun namedArgumentEnd(start: Int): Int {
            var depth = 0
            for (j in start until masked.length) {
                when (masked[j]) {
                    '(', '{', '[' -> depth++
                    ')', '}', ']' ->
                        if (depth == 0) return j else depth--
                    ',' -> if (depth == 0) return j
                }
            }
            return masked.length - 1
        }

        private class Literal(val start: Int, val end: Int, val value: String)

        private class Trigger(val start: Int, val end: Int)

        /**
         * True when the literal renders at least one letter that is not part of a
         * Kotlin template — i.e. there is something left to translate.
         */
        private fun Literal.hasRenderableLetters(): Boolean {
            if (!value.any { it.isLetter() }) return false
            var withoutBlocks = TEMPLATE_BLOCK.replace(value, "")
            withoutBlocks = SIMPLE_TEMPLATE.replace(withoutBlocks, "")
            return withoutBlocks.any { it.isLetter() }
        }

        /**
         * Blanks comments and string contents in one left-to-right pass. String
         * scanning tracks `${ ... }` templates (including nested strings and nested
         * braces inside the template expression) so a `}` inside a template never
         * ends the string early.
         */
        private fun tokenize(src: String): Pair<String, List<Literal>> {
            val out = src.toCharArray()
            val literals = mutableListOf<Literal>()
            val n = src.length
            var i = 0

            fun blank(from: Int, to: Int) {
                for (k in from until to) if (out[k] != '\n' && out[k] != '\r') out[k] = ' '
            }

            while (i < n) {
                val c = src[i]
                when {
                    c == '/' && i + 1 < n && src[i + 1] == '/' -> {
                        val end = src.indexOf('\n', i).let { if (it == -1) n else it }
                        blank(i, end)
                        i = end
                    }
                    c == '/' && i + 1 < n && src[i + 1] == '*' -> {
                        var depth = 1
                        var j = i + 2
                        while (j < n - 1 && depth > 0) {
                            if (src[j] == '/' && src[j + 1] == '*') { depth++; j += 2 }
                            else if (src[j] == '*' && src[j + 1] == '/') { depth--; j += 2 }
                            else j++
                        }
                        blank(i, j)
                        i = j
                    }
                    c == '"' -> {
                        val start = i
                        var j = i + 1
                        var templateDepth = 0
                        while (j < n) {
                            val ch = src[j]
                            if (templateDepth == 0) {
                                when {
                                    ch == '\\' -> j += 2
                                    ch == '$' && j + 1 < n && src[j + 1] == '{' -> {
                                        templateDepth = 1; j += 2
                                    }
                                    ch == '"' -> { j++; break }
                                    else -> j++
                                }
                            } else {
                                when {
                                    ch == '{' -> { templateDepth++; j++ }
                                    ch == '}' -> { templateDepth--; j++ }
                                    ch == '"' -> {
                                        // A nested string inside the template
                                        // expression; skip it without emitting it.
                                        j++
                                        while (j < n) {
                                            val inner = src[j]
                                            if (inner == '\\') { j += 2; continue }
                                            if (inner == '$' && j + 1 < n && src[j + 1] == '{') {
                                                var braces = 1
                                                j += 2
                                                while (j < n && braces > 0) {
                                                    when (src[j]) {
                                                        '{' -> braces++
                                                        '}' -> braces--
                                                        '"' -> {
                                                            j++
                                                            while (j < n) {
                                                                if (src[j] == '\\') { j += 2; continue }
                                                                if (src[j] == '"') break
                                                                j++
                                                            }
                                                        }
                                                    }
                                                    j++
                                                }
                                                continue
                                            }
                                            if (inner == '"') break
                                            j++
                                        }
                                        j++
                                    }
                                    else -> j++
                                }
                            }
                        }
                        val end = j
                        blank(start + 1, end - 1)
                        literals += Literal(start, end, src.substring(start + 1, end - 1))
                        i = end
                    }
                    c == '\'' -> {
                        var j = i + 1
                        while (j < n) {
                            if (src[j] == '\\') { j += 2; continue }
                            if (src[j] == '\'') break
                            j++
                        }
                        blank(i, j + 1)
                        i = j + 1
                    }
                    else -> i++
                }
            }
            return String(out) to literals
        }
    }

    private companion object {
        val IGNORED_DIRS = setOf("build", ".git", ".gradle", ".idea", "node_modules")

        val PREVIEW_ANNOTATION = Regex("@(Preview|PreviewParameter)\\b")

        val RESOURCE_CALL = Regex("(?<![A-Za-z0-9_])(pluralStringResource|stringResource)\\s*\\(")

        /** Render sites: positional [Text] and [Toast.makeText]. */
        val CALL_TRIGGERS = listOf(
            Regex("(?<![A-Za-z0-9_])Text\\s*\\("),
            Regex("(?<![A-Za-z0-9_])Toast\\.makeText\\s*\\("),
        )

        /** Render sites: named arguments that carry copy. */
        val NAMED_ARG = Regex(
            "(?<![A-Za-z0-9_.])(text|title|label|placeholder|description|contentDescription)[ \t]*=(?!=)"
        )

        /** `val label =` declares a property; it does not pass one. */
        val VAL_OR_VAR_BEFORE = Regex("\\b(val|var)[ \t]$")
        val CALLEE_HEAD = Regex("([A-Za-z_][A-Za-z0-9_.]*)\\s*$")

        /** APIs whose `label` argument is tooling telemetry, never rendered copy. */
        val ANIMATION_APIS = Regex(
            "animate[A-Za-z]*AsState|AnimatedContent|updateTransition|" +
                "rememberInfiniteTransition|animateContentSize|animateItem[A-Za-z]*|" +
                "animateFloat|animateColor|animateDp|animateRect|animateOffset"
        )

        val TEMPLATE_BLOCK = Regex("\\$\\{[^{}]*(\\{[^{}]*\\}[^{}]*)*\\}")
        val SIMPLE_TEMPLATE = Regex("\\$[A-Za-z_][A-Za-z0-9_]*")
    }
}
