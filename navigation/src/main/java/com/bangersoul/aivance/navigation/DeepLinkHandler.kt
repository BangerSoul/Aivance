package com.bangersoul.aivance.navigation

import android.content.Intent
import android.net.Uri
import timber.log.Timber

/**
 * Handles incoming deep link intents and converts them into
 * [Destination] objects for navigation.
 *
 * Supported URI patterns:
 *   aivance://jobs/{jobId}         → Job Details
 *   aivance://chat[/…]             → the AI orb (Assistant)
 *   aivance://interview[/…]        → Studio ▸ Practice
 *   aivance://resume[/…]           → Studio ▸ Resumes
 *   aivance://settings            → Identity Hub
 *   aivance://saved               → Saved Jobs
 *   aivance://notifications       → Notifications
 *   aivance://app (generic app open)
 */
object DeepLinkHandler {

    /** The most recently parsed deep link destination, if any. */
    @Volatile
    var pendingDestination: Destination? = null
        private set

    /** Same as [pendingDestination] but for warm-start consumption. */
    @Volatile
    var lastDeepLinkDestination: Destination? = null
        private set

    /**
     * Parses an incoming [Intent] and stores the resulting [Destination].
     * Returns `null` if the intent does not contain a known deep link.
     */
    fun handleIntent(intent: Intent): Destination? {
        val uri = intent.data ?: return null
        val destination = parseUri(uri)

        if (destination != null) {
            pendingDestination = destination
            lastDeepLinkDestination = destination
            Timber.d("Deep link resolved: %s → %s", uri, destination)
        } else {
            Timber.w("Deep link URI not recognised: %s", uri)
        }

        return destination
    }

    /**
     * Parses a deep-link [Uri] and maps it to a [Destination].
     *
     * Supported schemes:
     * - `aivance://host/path`
     * - `https://aivance.app/host/path`
     */
    fun parseUri(uri: Uri): Destination? {
        val scheme = uri.scheme ?: return null
        val host = uri.host ?: return null
        val pathSegments = uri.pathSegments

        // Only handle aivance:// or https://aivance.app URIs
        val isValidScheme = scheme == "aivance" ||
                (scheme == "https" && host == "aivance.app")

        if (!isValidScheme) return null

        return when (host.lowercase()) {
            "jobs" -> {
                val jobId = pathSegments.firstOrNull() ?: return null
                Destination.JobDetails(jobId = jobId)
            }
            "chat" -> {
                // The orb is the only assistant surface; the legacy Assistant
                // spoke is gone.
                Destination.AssistantOrb
            }
            "interview" -> {
                // Interview practice lives in Studio ▸ Practice: the segment is
                // a nav argument now, not a separate destination.
                Destination.Studio(segment = StudioSegment.PRACTICE)
            }
            "resume" -> {
                // Resumes live in Studio ▸ Resumes.
                Destination.Studio(segment = StudioSegment.RESUMES)
            }
            "app", "dashboard" -> {
                Destination.Dashboard
            }
            "settings", "profile", "identity" -> {
                Destination.IdentityHub
            }
            "saved" -> {
                Destination.SavedJobs
            }
            "notifications" -> {
                Destination.Notifications
            }
            else -> null
        }
    }

    /** Clears the pending deep link after it has been consumed. */
    fun consumePending(): Destination? {
        val dest = pendingDestination
        pendingDestination = null
        return dest
    }

    /** Resets all stored deep link state. */
    fun reset() {
        pendingDestination = null
        lastDeepLinkDestination = null
    }
}
