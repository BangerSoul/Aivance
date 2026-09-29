package com.bangersoul.aivance.core.domain.careergraph

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Guards the grounded, no-fabrication contract of [SkillLexicon]: a skill is only reported when
 * it literally appears in the source text on a token boundary.
 */
class SkillLexiconTest {

    @Test
    fun `extracts canonical skills present verbatim`() {
        val skills = SkillLexicon.extractSkills("We use Kotlin, Jetpack Compose and Kubernetes daily.")
        assertTrue(skills.contains("Kotlin"))
        assertTrue(skills.contains("Jetpack Compose"))
        assertTrue(skills.contains("Kubernetes"))
    }

    @Test
    fun `matches aliases and normalises to canonical label`() {
        val skills = SkillLexicon.extractSkills("Strong golang and k8s background, plus AWS.")
        assertTrue(skills.contains("Go"))
        assertTrue(skills.contains("Kubernetes"))
        assertTrue(skills.contains("AWS"))
    }

    @Test
    fun `does not match partial tokens`() {
        // "java" must not fire on "javascript"; "go" must not fire on "google".
        val skills = SkillLexicon.extractSkills("We build with javascript at google.")
        assertFalse(skills.contains("Java"))
        assertFalse(skills.contains("Go"))
        assertTrue(skills.contains("JavaScript"))
    }

    @Test
    fun `matches skill names containing punctuation`() {
        val skills = SkillLexicon.extractSkills("Expertise in C++ and CI/CD pipelines required.")
        assertTrue(skills.contains("C++"))
        assertTrue(skills.contains("CI/CD"))
    }

    @Test
    fun `returns empty for blank or null text`() {
        assertEquals(emptySet<String>(), SkillLexicon.extractSkills(null))
        assertEquals(emptySet<String>(), SkillLexicon.extractSkills("   "))
    }

    @Test
    fun `does not fabricate skills absent from the text`() {
        val skills = SkillLexicon.extractSkills("A friendly team that values collaboration.")
        assertTrue(skills.isEmpty())
    }
}
