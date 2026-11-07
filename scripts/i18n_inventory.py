#!/usr/bin/env python3
"""打印指定 UI 文件的中文字面量清单（行号 + 原文），供 i18n 迁移任务逐条核对。

用法：python scripts/i18n_inventory.py child/ChildHomeScreen.kt parent/ParentHomeScreen.kt
路径相对 composeApp/src/commonMain/kotlin/com/hstudy/app/ui/。
"""
import io
import re
import sys

UI_ROOT = "composeApp/src/commonMain/kotlin/com/hstudy/app/ui/"
PATTERN = re.compile(r'"([^"\n]*[一-鿿][^"\n]*)"')


def main(argv):
    if not argv:
        print(__doc__)
        return 1
    for rel in argv:
        path = UI_ROOT + rel
        try:
            lines = io.open(path, encoding="utf-8").read().splitlines()
        except OSError as exc:
            print("!! 无法读取 %s: %s" % (path, exc))
            continue
        seen = {}
        occurrences = 0
        for lineno, line in enumerate(lines, 1):
            for match in PATTERN.finditer(line):
                text = match.group(1)
                occurrences += 1
                seen.setdefault(text, lineno)
        interpolated = sum(1 for text in seen if "$" in text)
        print("\n## %s — 出现 %d 次 / 去重 %d 条 / 含插值 %d 条" % (rel, occurrences, len(seen), interpolated))
        print("| 行 | 中文 | 插值 |")
        print("|---|---|---|")
        for text, lineno in sorted(seen.items(), key=lambda item: item[1]):
            print("| %d | %s | %s |" % (lineno, text, "是" if "$" in text else ""))
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
