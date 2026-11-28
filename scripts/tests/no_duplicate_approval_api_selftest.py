# -*- coding: utf-8 -*-
"""Regression fixtures for the duplicate-approval API static gate.

Run with: python scripts/tests/no_duplicate_approval_api_selftest.py
The fixtures exercise the scanner against controlled Kotlin source so backtick
identifiers, nested comments, and raw strings cannot silently weaken the gate.
"""
import sys

sys.dont_write_bytecode = True

from pathlib import Path

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))

import no_duplicate_approval_api as gate

TRIPLE_QUOTE = '"""'

# (name, Kotlin fixture, expected diagnostic fragments)
CASES = [
    (
        "backtick escaped repository class",
        "class `PointApprovalRepository` { fun approve(id: Long) {} }",
        ("forbidden class PointApprovalRepository", "forbidden ledger-bypassing method approve"),
    ),
    (
        "backtick escaped repository bulk approval",
        "class `PointApprovalRepository` { fun approveAll() {} }",
        ("forbidden class PointApprovalRepository", "forbidden ledger-bypassing method approveAll"),
    ),
    (
        "DAO bulk approval outside repository",
        "@Dao interface OtherApprovalDao { suspend fun approveAll() }",
        ("forbidden DAO method approveAll",),
    ),
    (
        "generic DAO status setter outside repository",
        "@Dao interface OtherApprovalDao { suspend fun updateStatus(id: Long, status: String) }",
        ("forbidden generic status setter updateStatus",),
    ),
    (
        "backtick escaped generic status setter",
        "@Dao interface OtherApprovalDao { suspend fun `updateStatus`(id: Long) }",
        ("forbidden generic status setter updateStatus",),
    ),
    (
        "commented and literal APIs are ignored",
        "\n".join(
            [
                "// class PointApprovalRepository { fun approveAll() {} }",
                "/* class PointApprovalRepository { fun approve() {} } */",
                'val text = "fun updateStatus(id: Long)"',
                "val raw = " + TRIPLE_QUOTE,
                "class PointApprovalRepository { fun approveAll() {} }",
                TRIPLE_QUOTE,
            ]
        ),
        (),
    ),
    (
        "nested block comments and raw strings are masked",
        "\n".join(
            [
                "/* outer /* class PointApprovalRepository */ fun approveAll() */",
                "val raw = " + TRIPLE_QUOTE,
                "fun updateStatus(id: Long) = Unit",
                'embedded " quote and /* not a comment',
                TRIPLE_QUOTE,
                "class SafeDao",
            ]
        ),
        (),
    ),
    (
        "real API after raw string remains visible",
        "\n".join(
            [
                "val raw = " + TRIPLE_QUOTE,
                "class PointApprovalRepository { fun approveAll() {} }",
                TRIPLE_QUOTE,
                "class PointApprovalRepository { fun approve() {} }",
                "@Dao interface RealDao { fun approveAll() }",
            ]
        ),
        (
            "forbidden class PointApprovalRepository",
            "forbidden ledger-bypassing method approve",
            "forbidden DAO method approveAll",
        ),
    ),
    (
        "real API after nested comment remains visible",
        "/* outer /* inner */ still outer */ @Dao interface RealDao { fun updateStatus() }",
        ("forbidden generic status setter updateStatus",),
    ),
]


def main() -> int:
    if not hasattr(gate, "scan_source"):
        print("DUPLICATE_APPROVAL_API_SELFTEST_FAIL: scanner has no scan_source fixture boundary")
        return 1

    failures = []
    checks = 0
    for name, source, expected in CASES:
        checks += 1
        diagnostics = gate.scan_source(source, "fixtures/Example.kt")
        missing = [fragment for fragment in expected if not any(fragment in item for item in diagnostics)]
        unexpected = [] if expected else diagnostics
        if missing or unexpected:
            failures.append(name)
            print(
                "DUPLICATE_APPROVAL_API_SELFTEST_FAIL: %s; missing=%r unexpected=%r diagnostics=%r"
                % (name, missing, unexpected, diagnostics)
            )
        else:
            print("DUPLICATE_APPROVAL_API_SELFTEST_OK: " + name)

    sample = "/* a\n /* nested */\n b */\nval raw = " + TRIPLE_QUOTE + "\nclass Hidden\n" + TRIPLE_QUOTE
    masked = gate.mask_comments_and_literals(sample)
    checks += 1
    if len(masked) != len(sample) or masked.count("\n") != sample.count("\n"):
        failures.append("mask preserves offsets and line numbers")
        print("DUPLICATE_APPROVAL_API_SELFTEST_FAIL: mask preserves offsets and line numbers")
    elif "Hidden" in masked:
        failures.append("raw string body is blanked")
        print("DUPLICATE_APPROVAL_API_SELFTEST_FAIL: raw string body is blanked")
    else:
        print("DUPLICATE_APPROVAL_API_SELFTEST_OK: mask preserves offsets and line numbers")

    if failures:
        print(
            "DUPLICATE_APPROVAL_API_SELFTEST_FAIL: %d/%d checks failed"
            % (len(failures), checks)
        )
        return 1
    print("DUPLICATE_APPROVAL_API_SELFTEST_OK: %d checks, 0 failures" % checks)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
