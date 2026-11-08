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
            # 注释里的中文不是可迁移文案；先剥掉行注释，避免把说明文字当成字面量统计进来
            code = line.split("//", 1)[0]
            for match in PATTERN.finditer(code):
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


def _force_utf8_streams():
    """stdout/stderr 钉 UTF-8。本机（Windows）locale 是 cp936：清单打印的中文字面量里只要有一个
    GBK 编不出的码位（UI 文案里有 `⭐`/`✅` 这类 emoji），管道或重定向时 `print` 就抛
    UnicodeEncodeError、脚本非零退出，症状是"清单跑不动"而不是"编码不对"。
    完整理由见 `scripts/i18n_template_diff.py` 里的同名函数（终审 D-B：修这一类，不写进文档当环境注）。

    **为什么定义在文件末尾而不是紧跟 import**：docs/i18n-stage1-verification.md L-13 按行号引用本文件
    （`scripts/i18n_inventory.py:30` 就是那句剥注释的代码），护栏插到前面会把那条引用变成假引用。
    放在 `if __name__` 之前，模块一被导入/执行就生效，且**不移动任何一行既有代码**。
    """
    for stream in (sys.stdout, sys.stderr):
        if hasattr(stream, "reconfigure"):  # Python 3.7+ 的 TextIOWrapper；没有就退回原行为
            try:
                stream.reconfigure(encoding="utf-8", errors="replace")
            except (OSError, ValueError, RuntimeError):
                pass


_force_utf8_streams()

if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
