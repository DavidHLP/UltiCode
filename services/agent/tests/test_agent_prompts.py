"""The prompt system: covers every scored scenario, states each rule once, stays small.

These are the checks that keep the two prompt layers honest. A scenario the harness scores
but the prompt never mentions is a silent failure — the model is graded on an outcome it
was never told about — so the key sets are asserted equal rather than assumed to match.
"""

from __future__ import annotations

import sys
from pathlib import Path

import pytest

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "src"))

import agent_prompts as ap  # noqa: E402
import boundary_evaluation as be  # noqa: E402

CONTRACT = be.BOUNDARY_ANSWER_CONTRACT
SYSTEM = ap.tool_system_prompt("  - get_problem: slug")

#: Ceilings, not targets: they fail when a prompt edit adds prose instead of replacing it.
#: The previous separate contracts measured 2218 (answer) + 1342 (system) characters.
MAX_CONTRACT_CHARS = 1600
MAX_SYSTEM_CHARS = 1300


def test_every_scored_scenario_is_named_in_the_prompt() -> None:
    assert set(ap.SCENARIO_OUTCOMES) == set(be.BEHAVIOR_FOR_CATEGORY)


@pytest.mark.parametrize("category", sorted(ap.SCENARIO_OUTCOMES))
def test_scenario_outcome_reaches_the_contract(category: str) -> None:
    outcome = ap.SCENARIO_OUTCOMES[category]
    assert outcome.strip()
    assert outcome in CONTRACT


@pytest.mark.parametrize("category", sorted(ap.SCENARIO_OUTCOMES))
def test_scenario_outcomes_are_distinct(category: str) -> None:
    others = [text for key, text in ap.SCENARIO_OUTCOMES.items() if key != category]
    assert ap.SCENARIO_OUTCOMES[category] not in others


def test_citation_schema_in_the_prompt_matches_the_parser() -> None:
    """A field the parser requires but the prompt omits cannot be produced."""
    for field in be.CITATION_FIELDS:
        assert field in CONTRACT


def test_trust_boundaries_are_stated_once_across_both_layers() -> None:
    both = f"{SYSTEM}\n{CONTRACT}"
    assert both.count(ap.TRUST_INVARIANTS) == 1
    assert both.count(ap.AUTONOMY) == 1
    assert "untrusted data" in both
    assert "Identity comes only from the server session" in SYSTEM


def test_prompt_layers_stay_inside_their_budget() -> None:
    assert len(CONTRACT) <= MAX_CONTRACT_CHARS, len(CONTRACT)
    assert len(SYSTEM) <= MAX_SYSTEM_CHARS, len(SYSTEM)


def test_the_documented_envelope_is_what_the_adapter_and_parser_accept() -> None:
    """The contract describes a wire shape; this fails if the code drifts from it."""
    import json

    from deepseek_model import _parse_decision

    citation = {field: "x" for field in be.CITATION_FIELDS}
    inner = {"text": "answer text", "citations": [citation]}
    decision = _parse_decision(
        json.dumps({"answer": json.dumps(inner)}), finish_reason="stop"
    )
    text, checked, malformed = be._answer_of(decision.text)
    assert text == "answer text"
    assert checked == (citation,)
    assert malformed == ()


def test_tool_system_prompt_carries_the_tool_specs_and_protocol() -> None:
    assert "  - get_problem: slug" in SYSTEM
    assert '{"tool": "<name>", "args": {...}}' in SYSTEM
    assert '{"answer": "..."}' in SYSTEM


def test_manual_analysis_task_names_the_product_deliverable() -> None:
    instructions = ap.MANUAL_ANALYSIS_INSTRUCTIONS
    assert "get_submission_source" in instructions
    for part in ("core algorithm", "implementation steps", "complexity", "edge cases"):
        assert part in instructions
