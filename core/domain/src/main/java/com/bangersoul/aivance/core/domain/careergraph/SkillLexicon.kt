package com.bangersoul.aivance.core.domain.careergraph

/**
 * A curated, deterministic vocabulary of professional/technical skills used to ground
 * the Career Knowledge Graph's `REQUIRES_SKILL` edges.
 *
 * The lexicon exists so the graph never *fabricates* required skills: a skill is only
 * attributed to a job when a known canonical term (or one of its aliases) literally
 * appears in that job's text. Matching is case-insensitive and whole-word/phrase bounded,
 * so "java" never matches "javascript" and "go" never matches "google".
 *
 * This is intentionally a finite, auditable list rather than an AI extraction: it keeps
 * skill-gap analysis reproducible and free of hallucinated requirements. New terms can be
 * added here as the product's coverage grows.
 */
object SkillLexicon {

    /**
     * Canonical skill label → the set of lowercase surface forms that denote it.
     * The canonical label itself is always an implicit alias.
     */
    private val CANONICAL: Map<String, Set<String>> = mapOf(
        // Languages
        "Kotlin" to setOf("kotlin"),
        "Java" to setOf("java"),
        "Swift" to setOf("swift"),
        "Objective-C" to setOf("objective-c", "objective c"),
        "Python" to setOf("python"),
        "JavaScript" to setOf("javascript", "js"),
        "TypeScript" to setOf("typescript", "ts"),
        "Go" to setOf("golang", "go lang"),
        "Rust" to setOf("rust"),
        "C++" to setOf("c++", "cpp"),
        "C#" to setOf("c#", "c sharp"),
        "Ruby" to setOf("ruby"),
        "PHP" to setOf("php"),
        "Scala" to setOf("scala"),
        "Dart" to setOf("dart"),
        "SQL" to setOf("sql"),

        // Mobile
        "Android" to setOf("android"),
        "iOS" to setOf("ios"),
        "Jetpack Compose" to setOf("jetpack compose", "compose"),
        "SwiftUI" to setOf("swiftui"),
        "Flutter" to setOf("flutter"),
        "React Native" to setOf("react native"),
        "Coroutines" to setOf("coroutines", "kotlin coroutines"),
        "RxJava" to setOf("rxjava"),

        // Web / frontend
        "React" to setOf("react", "react.js", "reactjs"),
        "Angular" to setOf("angular"),
        "Vue" to setOf("vue", "vue.js", "vuejs"),
        "Node.js" to setOf("node.js", "nodejs", "node js"),
        "HTML" to setOf("html", "html5"),
        "CSS" to setOf("css", "css3"),

        // Backend / frameworks
        "Spring" to setOf("spring", "spring boot", "springboot"),
        "Django" to setOf("django"),
        "Flask" to setOf("flask"),
        "GraphQL" to setOf("graphql"),
        "REST" to setOf("rest", "rest api", "restful"),
        "gRPC" to setOf("grpc"),
        "Microservices" to setOf("microservices", "micro-services"),

        // Data / ML
        "Machine Learning" to setOf("machine learning", "ml"),
        "Deep Learning" to setOf("deep learning"),
        "Data Science" to setOf("data science"),
        "TensorFlow" to setOf("tensorflow"),
        "PyTorch" to setOf("pytorch"),
        "Pandas" to setOf("pandas"),
        "Spark" to setOf("apache spark", "spark"),
        "Kafka" to setOf("kafka", "apache kafka"),

        // Cloud / infra
        "AWS" to setOf("aws", "amazon web services"),
        "Azure" to setOf("azure"),
        "GCP" to setOf("gcp", "google cloud"),
        "Docker" to setOf("docker"),
        "Kubernetes" to setOf("kubernetes", "k8s"),
        "Terraform" to setOf("terraform"),
        "CI/CD" to setOf("ci/cd", "cicd", "continuous integration"),
        "Jenkins" to setOf("jenkins"),
        "Git" to setOf("git"),

        // Databases
        "PostgreSQL" to setOf("postgresql", "postgres"),
        "MySQL" to setOf("mysql"),
        "MongoDB" to setOf("mongodb", "mongo"),
        "Redis" to setOf("redis"),
        "Room" to setOf("room database", "androidx room"),
        "SQLite" to setOf("sqlite"),

        // Architecture / practices
        "MVVM" to setOf("mvvm"),
        "Clean Architecture" to setOf("clean architecture"),
        "Dependency Injection" to setOf("dependency injection", "dagger", "hilt"),
        "Testing" to setOf("unit testing", "test-driven", "tdd"),
        "Agile" to setOf("agile", "scrum"),

        // Professional / soft
        "Leadership" to setOf("leadership", "team lead", "tech lead"),
        "Communication" to setOf("communication"),
        "Product Management" to setOf("product management"),
        "Project Management" to setOf("project management")
    )

    /** Pre-compiled alias → canonical index, longest surface forms first for greedy matching. */
    private val ALIAS_INDEX: List<Pair<String, String>> = buildList {
        CANONICAL.forEach { (canonical, aliases) ->
            add(canonical.lowercase() to canonical)
            aliases.forEach { add(it to canonical) }
        }
    }.distinctBy { it.first }
        .sortedByDescending { it.first.length }

    /**
     * Returns the set of canonical skill labels literally present in [text].
     *
     * A term matches only on a whole-word / whole-phrase boundary, so partial tokens
     * (e.g. "java" inside "javascript") never produce a false positive. Never returns a
     * skill that does not appear verbatim in the source text.
     */
    fun extractSkills(text: String?): Set<String> {
        if (text.isNullOrBlank()) return emptySet()
        val haystack = text.lowercase()
        val found = LinkedHashSet<String>()
        ALIAS_INDEX.forEach { (alias, canonical) ->
            if (canonical !in found && containsAsToken(haystack, alias)) {
                found.add(canonical)
            }
        }
        return found
    }

    /**
     * Whole-token containment: [needle] must be bounded by a non-alphanumeric character
     * (or string edge) on both sides. Characters that are legitimately part of skill names
     * (`+`, `#`, `.`, `/`, `-`) are treated as part of the token so "c++" and "ci/cd" match.
     */
    private fun containsAsToken(haystack: String, needle: String): Boolean {
        var from = 0
        while (true) {
            val idx = haystack.indexOf(needle, from)
            if (idx < 0) return false
            val before = idx - 1
            val after = idx + needle.length
            val boundedLeft = before < 0 || !isTokenChar(haystack[before])
            val boundedRight = after >= haystack.length || !isTokenChar(haystack[after])
            if (boundedLeft && boundedRight) return true
            from = idx + 1
        }
    }

    private fun isTokenChar(c: Char): Boolean = c.isLetterOrDigit()
}
