package com.bangersoul.aivance.core.domain.usecase.interview

import com.bangersoul.aivance.core.common.model.InterviewQuestion

/**
 * Deterministic STAR (Situation, Task, Action, Result) question-pack generator
 * (R-05).
 *
 * Serves as the offline fallback for [GenerateStarPackUseCase] — every question
 * carries the STAR key-points framework and a worked `idealAnswer`, so a pack is
 * always available even without an AI provider. AI-generated packs are preferred
 * when a provider is configured; this generator guarantees the flow never
 * dead-ends on an empty pack.
 */
object STARPrepGenerator {

    /**
     * Returns a role-tailored pack of up to [count] STAR-format questions.
     * The role is interpolated into every question so packs stay specific to
     * the user's chosen target role.
     */
    fun generateStarPack(role: String, count: Int = 5): List<InterviewQuestion> {
        val roleTitle = role.ifBlank { "Software Engineer" }
        return TEMPLATES.take(count.coerceIn(1, TEMPLATES.size)).map { template ->
            template(roleTitle)
        }
    }

    private val TEMPLATES: List<(String) -> InterviewQuestion> = listOf(
        { role ->
            InterviewQuestion(
                text = "Describe a challenging technical problem you solved as a $role using the STAR method.",
                category = "BEHAVIORAL",
                difficulty = "MEDIUM",
                expectedKeyPoints = listOf(
                    "Situation: Context & scope of the problem",
                    "Task: Your specific responsibilities & constraints",
                    "Action: Concrete technical steps & decisions taken",
                    "Result: Quantitative business impact & lessons learned"
                ),
                idealAnswer = "Situation: At my previous company, our production microservice experienced severe memory leaks under peak traffic.\nTask: As the lead engineer, I was responsible for diagnosing the root cause and deploying a zero-downtime fix within 48 hours.\nAction: I captured heap dumps using async-profiler, identified unclosed database connection pools, refactored thread resource management, and added automated alerts.\nResult: System latency dropped by 40%, zero crashes occurred during the peak event, and the team adopted automated connection-pool linting."
            )
        },
        { role ->
            InterviewQuestion(
                text = "Tell me about a time you had a cross-functional disagreement regarding architectural trade-offs as a $role.",
                category = "LEADERSHIP",
                difficulty = "HARD",
                expectedKeyPoints = listOf(
                    "Situation: Differing engineering vs product priorities",
                    "Task: Aligning stakeholders without compromising system reliability",
                    "Action: Benchmarking performance metrics & facilitating consensus",
                    "Result: Successful delivery with the reliability target met"
                ),
                idealAnswer = "Situation: Product wanted to launch an analytics feature immediately using quick REST polling, while engineering advocated for WebSocket streaming to reduce backend load.\nTask: Bridge the perspective gap and deliver a scalable solution without delaying launch.\nAction: I built a one-day benchmark demo comparing cost and latency under 10k users, presented the data objectively to product leads, and proposed a hybrid polling-fallback model.\nResult: Product agreed on WebSockets for the initial release, launch was on time, and backend infrastructure costs were reduced by 65%."
            )
        },
        { role ->
            InterviewQuestion(
                text = "Walk me through the most impactful project you delivered as a $role — what made it hard and what did you learn?",
                category = "TECHNICAL",
                difficulty = "MEDIUM",
                expectedKeyPoints = listOf(
                    "Situation: The product problem and why it mattered",
                    "Task: Your role, ownership and success criteria",
                    "Action: Design choices, trade-offs and execution",
                    "Result: Measurable outcomes and lessons learned"
                ),
                idealAnswer = "Situation: Our team was shipping a legacy feature with a 30% user-error rate that generated support tickets.\nTask: As the $role on the squad, I owned the redesign end to end.\nAction: I profiled the failure modes, shipped a guided flow with inline validation, and instrumented funnel analytics.\nResult: The error rate dropped to 6% within one quarter and support ticket volume fell by half."
            )
        },
        { role ->
            InterviewQuestion(
                text = "Give an example of when you had to learn a new technology quickly to unblock the team as a $role.",
                category = "TECHNICAL",
                difficulty = "EASY",
                expectedKeyPoints = listOf(
                    "Situation: The unfamiliar technology and the deadline",
                    "Task: What the team needed from you",
                    "Action: How you learned and applied it",
                    "Result: Outcome and how you now use that skill"
                ),
                idealAnswer = "Situation: A client deliverable required a GraphQL migration none of us had production experience with.\nTask: I needed to reach production proficiency in two weeks.\nAction: I worked through the official docs and a side project, paired with a domain expert, and prototyped the migration against a staging mirror.\nResult: The migration shipped on schedule with zero downtime, and I now mentor new hires on GraphQL."
            )
        },
        { role ->
            InterviewQuestion(
                text = "Describe a time you received critical feedback as a $role and how you acted on it.",
                category = "LEADERSHIP",
                difficulty = "MEDIUM",
                expectedKeyPoints = listOf(
                    "Situation: The feedback and the context around it",
                    "Task: The behavior you needed to change",
                    "Action: Concrete steps you took",
                    "Result: The improvement and how it stuck"
                ),
                idealAnswer = "Situation: After a major release, my manager flagged that I was over-communicating progress and under-communicating risks.\nTask: I needed to surface risks earlier and more directly.\nAction: I started a weekly risk register, opened design reviews two weeks earlier, and asked for feedback on the change.\nResult: The next release had zero surprises, and my peer reviews reflected stronger collaboration."
            )
        },
        { role ->
            InterviewQuestion(
                text = "Tell me about a time you had to debug a production incident as a $role while users were affected.",
                category = "TECHNICAL",
                difficulty = "HARD",
                expectedKeyPoints = listOf(
                    "Situation: The incident, severity and blast radius",
                    "Task: Your role in the response",
                    "Action: Diagnosis, mitigation and prevention steps",
                    "Result: Recovery time and the postmortem outcome"
                ),
                idealAnswer = "Situation: A bad deploy caused a partial outage affecting checkouts for about 20 minutes.\nTask: As the on-call $role, I led the response.\nAction: I rolled back immediately, gathered metrics and logs, and identified a schema migration that ran out of order; I added a pre-deploy validation step.\nResult: Service was restored within the incident window, and the same class of failure was caught by CI the following week."
            )
        }
    )
}
