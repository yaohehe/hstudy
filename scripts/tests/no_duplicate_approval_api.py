# -*- coding: utf-8 -*-
"""Block ledger-bypassing approval APIs in active Kotlin source sets.

The archived ``legacy/`` tree and static-analysis fixtures are deliberately outside
this gate. Every Kotlin file under ``composeApp/src`` is scanned, including tests
and platform source sets. Kotlin comments, regular/character literals, nested
block comments, and raw strings are masked without changing offsets or lines.
"""
import io
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
SOURCE_ROOT = ROOT / "composeApp" / "src"
FORBIDDEN_CLASS = re.compile(
    r"\bclass\s+(?:`PointApprovalRepository`|PointApprovalRepository(?!\w))"
)


def method_pattern(name: str) -> re.Pattern:
    escaped = re.escape(name)
    return re.compile(r"\bfun\s+(?:`" + escaped + r"`|" + escaped + r"(?!\w))\s*\(")


REPOSITORY_METHODS = (
    ("approve", method_pattern("approve")),
    ("approveAll", method_pattern("approveAll")),
)
DAO_METHODS = (
    ("approve", method_pattern("approve")),
    ("approveAll", method_pattern("approveAll")),
    ("updateStatus", method_pattern("updateStatus")),
)


def configure_output() -> None:
    sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding="utf-8", errors="replace")
    sys.stderr = io.TextIOWrapper(sys.stderr.buffer, encoding="utf-8", errors="replace")


def mask_comments_and_literals(source: str) -> str:
    """Blank non-code while preserving exact offsets and line numbers.

    Kotlin block comments nest, unlike C-style comments. Raw strings use triple
    quotes and do not process backslash escapes; both facts matter to avoid a
    forbidden-looking fixture swallowing real code after it (or vice versa).
    """
    chars = list(source)
    block_depth = 0
    state = "code"
    i = 0
    while i < len(source):
        ch = source[i]
        nxt = source[i + 1] if i + 1 < len(source) else ""

        def blank(index: int) -> None:
            if source[index] != "\n":
                chars[index] = " "

        if state == "line_comment":
            if ch == "\n":
                state = "code"
            else:
                blank(i)
        elif state == "block_comment":
            if ch == "/" and nxt == "*":
                blank(i)
                blank(i + 1)
                block_depth += 1
                i += 1
            elif ch == "*" and nxt == "/":
                blank(i)
                blank(i + 1)
                block_depth -= 1
                i += 1
                if block_depth == 0:
                    state = "code"
            else:
                blank(i)
        elif state == "string":
            if ch == "\\":
                blank(i)
                if i + 1 < len(source) and source[i + 1] != "\n":
                    blank(i + 1)
                    i += 1
            elif ch == '"':
                blank(i)
                state = "code"
            else:
                blank(i)
        elif state == "char":
            if ch == "\\":
                blank(i)
                if i + 1 < len(source):
                    blank(i + 1)
                    i += 1
            elif ch == "'":
                blank(i)
                state = "code"
            else:
                blank(i)
        elif state == "raw_string":
            if source.startswith('"""', i):
                blank(i)
                blank(i + 1)
                blank(i + 2)
                i += 2
                state = "code"
            else:
                blank(i)
        elif ch == "/" and nxt == "/":
            blank(i)
            blank(i + 1)
            i += 1
            state = "line_comment"
        elif ch == "/" and nxt == "*":
            blank(i)
            blank(i + 1)
            i += 1
            block_depth = 1
            state = "block_comment"
        elif source.startswith('"""', i):
            blank(i)
            blank(i + 1)
            blank(i + 2)
            i += 2
            state = "raw_string"
        elif ch == '"':
            blank(i)
            state = "string"
        elif ch == "'":
            blank(i)
            state = "char"
        i += 1
    return "".join(chars)


def class_body_span(masked: str, class_match: re.Match) -> tuple[int, int]:
    """Return the half-open body span for a matched class declaration."""
    opening = masked.find("{", class_match.end())
    if opening < 0:
        return (len(masked), len(masked))
    depth = 0
    for index in range(opening, len(masked)):
        if masked[index] == "{":
            depth += 1
        elif masked[index] == "}":
            depth -= 1
            if depth == 0:
                return (opening + 1, index)
    return (opening + 1, len(masked))


def line_number(source: str, offset: int) -> int:
    return source.count("\n", 0, offset) + 1


def scan_source(source: str, relative_path: str) -> list[str]:
    """Return diagnostics for one Kotlin source fixture or production file."""
    masked = mask_comments_and_literals(source)
    failures = []
    repository_spans = []
    for match in FORBIDDEN_CLASS.finditer(masked):
        failures.append(
            "%s:%d: forbidden class PointApprovalRepository"
            % (relative_path, line_number(source, match.start()))
        )
        repository_spans.append(class_body_span(masked, match))

    def inside_repository(offset: int) -> bool:
        return any(start <= offset < end for start, end in repository_spans)

    for method_name, pattern in REPOSITORY_METHODS:
        for match in pattern.finditer(masked):
            if inside_repository(match.start()):
                failures.append(
                    "%s:%d: forbidden ledger-bypassing method %s"
                    % (relative_path, line_number(source, match.start()), method_name)
                )

    for method_name, pattern in DAO_METHODS:
        for match in pattern.finditer(masked):
            if not inside_repository(match.start()):
                label = (
                    "forbidden generic status setter"
                    if method_name == "updateStatus"
                    else "forbidden DAO method"
                )
                failures.append(
                    "%s:%d: %s %s"
                    % (relative_path, line_number(source, match.start()), label, method_name)
                )
    return failures


def main() -> int:
    configure_output()
    failures = []
    scanned = 0
    for path in sorted(SOURCE_ROOT.rglob("*.kt")):
        scanned += 1
        source = path.read_text(encoding="utf-8")
        failures.extend(scan_source(source, path.relative_to(ROOT).as_posix()))

    if failures:
        for failure in failures:
            print("DUPLICATE_APPROVAL_API_GATE_FAIL: " + failure)
        return 1
    print(
        "DUPLICATE_APPROVAL_API_GATE_OK: %d Kotlin files scanned; "
        "PointApprovalRepository, DAO approve/approveAll, and generic updateStatus are absent" % scanned
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
