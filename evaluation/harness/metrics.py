"""
metrics.py - AI Evaluation Framework Metrics Calculator
Pure Python module computing accuracy, determinism, grounding, hallucination, and cost metrics for AiVance AI model evaluations.
"""

from __future__ import annotations

import math
import re
import statistics
from typing import Any, Dict, List, Sequence, Set, Tuple

# Common skill aliases for normalization
SKILL_ALIASES: Dict[str, str] = {
    "k8s": "kubernetes",
    "kube": "kubernetes",
    "reactjs": "react",
    "react.js": "react",
    "nextjs": "next.js",
    "golang": "go",
    "postgres": "postgresql",
    "amazon web services": "aws",
    "cicd": "ci/cd",
    "continuous integration": "ci/cd",
    "github actions": "ci/cd",
    "gitlab ci": "ci/cd",
    "restful api": "rest apis",
    "restful apis": "rest apis",
    "rest api": "rest apis",
    "rest": "rest apis",
    "jetpack compose": "jetpack compose",
    "compose": "jetpack compose",
    "tailwind": "tailwind css",
    "coroutine": "coroutines",
    "kotlin coroutines": "coroutines",
    "flow": "flow",
    "kotlin flow": "flow",
    "stateflow": "flow",
    "sharedflow": "flow",
    "dagger hilt": "dagger hilt",
    "hilt": "dagger hilt",
    "junit 5": "junit",
    "junit4": "junit",
    "clean arch": "clean architecture",
    "distributed system": "distributed systems",
    "microservice": "microservices",
    "docker containers": "docker",
    "gitops": "gitops",
    "argo": "argocd",
    "swift-ui": "swiftui",
}

# Negation and refutation phrases for hallucination detection
NEGATION_PHRASES: List[str] = [
    "does not have",
    "doesn't have",
    "does not possess",
    "has no",
    "no evidence of",
    "no record of",
    "no experience with",
    "not listed",
    "not mentioned",
    "refutes",
    "refute",
    "declines to",
    "cannot find",
    "never worked",
    "neither swift nor",
    "unsupported",
    "false claim",
    "inaccurate",
    "has not worked",
    "has not published",
    "instead of",
    "holds a master",
    "holds a bachelor",
    "only has",
    "graduated in 2023",
]

# Provider pricing table (per 1M tokens in USD)
PROVIDER_PRICING: Dict[str, Dict[str, Any]] = {
    "gemini": {
        "name": "Gemini 1.5 Pro",
        "input_per_million": 1.25,
        "output_per_million": 5.00,
        "type": "Cloud API",
    },
    "claude": {
        "name": "Claude 3.5 Sonnet",
        "input_per_million": 3.00,
        "output_per_million": 15.00,
        "type": "Cloud API",
    },
    "openai": {
        "name": "GPT-4o",
        "input_per_million": 2.50,
        "output_per_million": 10.00,
        "type": "Cloud API",
    },
    "groq": {
        "name": "Llama 3.3 70B (Groq LPU)",
        "input_per_million": 0.59,
        "output_per_million": 0.79,
        "type": "High-Speed LPU",
    },
    "gemma": {
        "name": "Gemma 2 9B (Local / On-Device)",
        "input_per_million": 0.00,
        "output_per_million": 0.00,
        "type": "Local On-Device",
    },
}


def normalize_skill(skill: str) -> str:
    """Normalize a skill name into a standardized lowercase format."""
    cleaned = re.sub(r"[\(\)\[\]\,]", "", skill.strip().lower())
    cleaned = re.sub(r"\s+", " ", cleaned)
    return SKILL_ALIASES.get(cleaned, cleaned)


def calculate_skill_metrics(
    predicted_skills: Sequence[str], ground_truth_skills: Sequence[str]
) -> Dict[str, Any]:
    """
    Calculate Precision, Recall, and F1 Score for skill extraction.

    Precision = TP / (TP + FP)
    Recall = TP / (TP + FN)
    F1 = 2 * (Precision * Recall) / (Precision + Recall)
    """
    pred_set: Set[str] = {normalize_skill(s) for s in predicted_skills if s.strip()}
    gt_set: Set[str] = {normalize_skill(s) for s in ground_truth_skills if s.strip()}

    if not gt_set and not pred_set:
        return {
            "precision": 1.0,
            "recall": 1.0,
            "f1_score": 1.0,
            "tp": 0,
            "fp": 0,
            "fn": 0,
            "matched_skills": [],
            "unmatched_predictions": [],
            "missing_ground_truth": [],
        }

    tp_set: Set[str] = set()
    # Support exact or substring containment matching for compound terms
    for p in pred_set:
        for g in gt_set:
            if p == g or (len(p) > 3 and p in g) or (len(g) > 3 and g in p):
                tp_set.add(g)

    fp_count = max(0, len(pred_set) - len(tp_set))
    fn_set = gt_set - tp_set
    fn_count = len(fn_set)
    tp_count = len(tp_set)

    precision = tp_count / (tp_count + fp_count) if (tp_count + fp_count) > 0 else 0.0
    recall = tp_count / (tp_count + fn_count) if (tp_count + fn_count) > 0 else 0.0
    f1 = (
        (2.0 * precision * recall) / (precision + recall)
        if (precision + recall) > 0
        else 0.0
    )

    return {
        "precision": round(precision, 4),
        "recall": round(recall, 4),
        "f1_score": round(f1, 4),
        "tp": tp_count,
        "fp": fp_count,
        "fn": fn_count,
        "matched_skills": sorted(list(tp_set)),
        "unmatched_predictions": sorted(list(pred_set - tp_set)),
        "missing_ground_truth": sorted(list(fn_set)),
    }


def calculate_ats_determinism(
    score_runs: Sequence[float], target_threshold_std_dev: float = 1.0
) -> Dict[str, Any]:
    """
    Calculate ATS scoring determinism across repeat runs.

    Computes mean, variance, standard deviation, and tests if std_dev < target_threshold_std_dev.
    """
    if not score_runs:
        return {
            "mean": 0.0,
            "variance": 0.0,
            "std_dev": 0.0,
            "min": 0.0,
            "max": 0.0,
            "range": 0.0,
            "is_deterministic": True,
            "determinism_rating": "PASS",
        }

    n = len(score_runs)
    mean_val = statistics.mean(score_runs)
    variance_val = statistics.variance(score_runs) if n > 1 else 0.0
    std_dev_val = statistics.stdev(score_runs) if n > 1 else 0.0
    score_min = min(score_runs)
    score_max = max(score_runs)
    score_range = score_max - score_min

    is_deterministic = std_dev_val <= target_threshold_std_dev

    return {
        "mean": round(mean_val, 2),
        "variance": round(variance_val, 4),
        "std_dev": round(std_dev_val, 4),
        "min": round(score_min, 2),
        "max": round(score_max, 2),
        "range": round(score_range, 2),
        "is_deterministic": is_deterministic,
        "determinism_rating": "PASS" if is_deterministic else "REGRESSION_WARNING",
    }


def calculate_grounding_score(verified_claims: int, total_claims: int) -> float:
    """Calculate percentage of AI claims verified against ground truth."""
    if total_claims <= 0:
        return 100.0
    score = (verified_claims / total_claims) * 100.0
    return round(max(0.0, min(100.0, score)), 2)


def calculate_hallucination_rate(fabricated_claims: int, total_claims: int) -> float:
    """Calculate percentage of fabricated / ungrounded claims."""
    if total_claims <= 0:
        return 0.0
    rate = (fabricated_claims / total_claims) * 100.0
    return round(max(0.0, min(100.0, rate)), 2)


def evaluate_hallucination_traps(
    response_text: str, forbidden_traps: Sequence[str]
) -> Dict[str, Any]:
    """
    Evaluate whether a model fell into adversarial hallucination traps or properly refuted them.

    A trap is triggered if the forbidden keyword is mentioned positively without proper negation/refutation context.
    """
    lower_text = response_text.lower()
    sentences = re.split(r"[\.\n\;\!]", lower_text)

    triggered_traps: List[str] = []
    refuted_traps: List[str] = []

    for trap in forbidden_traps:
        trap_lower = trap.lower()
        if trap_lower in lower_text:
            # Check if mentions are in a negating or refuting sentence
            is_refuted = False
            for sentence in sentences:
                if trap_lower in sentence:
                    if any(neg in sentence for neg in NEGATION_PHRASES):
                        is_refuted = True
                        break

            if is_refuted:
                refuted_traps.append(trap)
            else:
                triggered_traps.append(trap)

    total_traps = len(forbidden_traps)
    hallucination_count = len(triggered_traps)
    verified_count = total_traps - hallucination_count

    grounding_score = calculate_grounding_score(verified_count, total_traps)
    hallucination_rate = calculate_hallucination_rate(hallucination_count, total_traps)

    return {
        "is_hallucination": len(triggered_traps) > 0,
        "triggered_traps": triggered_traps,
        "refuted_traps": refuted_traps,
        "grounding_score": grounding_score,
        "hallucination_rate": hallucination_rate,
    }


def calculate_rubric_error(
    predicted: Dict[str, float], ground_truth: Dict[str, float]
) -> Dict[str, Any]:
    """
    Calculate Mean Absolute Error (MAE) between predicted rubric scores and ground truth.
    """
    keys = ["clarity", "technical_accuracy", "star_method_score", "overall_score"]
    errors: Dict[str, float] = {}
    diffs: List[float] = []

    for k in keys:
        if k in predicted and k in ground_truth:
            diff = abs(float(predicted[k]) - float(ground_truth[k]))
            errors[f"{k}_mae"] = round(diff, 2)
            diffs.append(diff)

    overall_mae = round(statistics.mean(diffs), 3) if diffs else 0.0
    alignment_pct = round(max(0.0, 100.0 - (overall_mae * 10.0)), 2)

    return {
        "mae_by_criterion": errors,
        "overall_mae": overall_mae,
        "rubric_alignment_pct": alignment_pct,
    }


def estimate_cost_and_latency(
    provider_id: str,
    input_tokens: int,
    output_tokens: int,
    ttft_ms: float,
    total_latency_ms: float,
) -> Dict[str, Any]:
    """
    Estimate invocation cost and record token latency metrics.
    """
    cfg = PROVIDER_PRICING.get(
        provider_id.lower(),
        {
            "name": provider_id,
            "input_per_million": 1.0,
            "output_per_million": 2.0,
            "type": "Unknown",
        },
    )

    input_cost = (input_tokens / 1_000_000.0) * cfg["input_per_million"]
    output_cost = (output_tokens / 1_000_000.0) * cfg["output_per_million"]
    total_cost = input_cost + output_cost
    cost_per_1k = total_cost * 1_000.0

    return {
        "provider": provider_id,
        "provider_display_name": cfg["name"],
        "provider_type": cfg["type"],
        "input_tokens": input_tokens,
        "output_tokens": output_tokens,
        "total_tokens": input_tokens + output_tokens,
        "ttft_ms": round(ttft_ms, 1),
        "total_latency_ms": round(total_latency_ms, 1),
        "total_cost_usd": round(total_cost, 6),
        "cost_per_1k_requests_usd": round(cost_per_1k, 4),
    }
