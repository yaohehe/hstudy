"""审计：双语第一阶段的计划是否覆盖了 spec §1 声明的页面。

对比 ui/ 下每个文件的中文文案条数与计划文本里出现的文件名，列出计划未覆盖的文件。
"""
import io
import os
import re
import sys

ROOT = "composeApp/src/commonMain/kotlin/com/hstudy/app/"
PATTERN = re.compile(r'"([^"\n]*[一-鿿][^"\n]*)"')


def count(path):
    total = 0
    seen = set()
    for line in io.open(path, encoding="utf-8"):
        code = line.split("//", 1)[0]
        for match in PATTERN.finditer(code):
            total += 1
            seen.add(match.group(1))
    return total, len(seen)


def main():
    plan = io.open("docs/superpowers/plans/2026-11-07-hstudy-bilingual-stage1.md", encoding="utf-8").read()
    rows = []
    for dirpath, _dirnames, filenames in os.walk(ROOT + "ui"):
        for name in filenames:
            if not name.endswith(".kt"):
                continue
            full = os.path.join(dirpath, name)
            occ, uniq = count(full)
            if uniq:
                rows.append((occ, uniq, name, name in plan))
    rows.sort(reverse=True)
    covered = sum(uniq for _o, uniq, _n, inplan in rows if inplan)
    uncovered = sum(uniq for _o, uniq, _n, inplan in rows if not inplan)
    print("计划已覆盖页面的去重文案条数: %d" % covered)
    print("计划未覆盖页面的去重文案条数: %d" % uncovered)
    print("\n计划未覆盖、且条数 >= 10 的文件:")
    for occ, uniq, name, inplan in rows:
        if not inplan and uniq >= 10:
            print("  出现 %3d / 去重 %3d  %s" % (occ, uniq, name))
    return 0


def _force_utf8_streams():
    """stdout/stderr 钉 UTF-8：本脚本打印中文表头与中文文件名，管道/重定向到 GBK 控制台时
    `print` 会抛 UnicodeEncodeError 让审计非零退出。完整理由见 `scripts/i18n_template_diff.py`
    的同名函数（终审 D-B：修这一类，不写进文档当环境注）。

    放在文件末尾是刻意的：`docs/i18n-stage1-verification.md` L-13 按行号引用本文件
    （`scripts/i18n_coverage_audit.py:35`），护栏插到 import 之后会把那条引用推成假引用。
    这里**不移动任何一行既有代码**，模块执行到此处即完成换装，`main()` 在其后才被调用。
    """
    for stream in (sys.stdout, sys.stderr):
        if hasattr(stream, "reconfigure"):  # Python 3.7+ 的 TextIOWrapper；没有就退回原行为
            try:
                stream.reconfigure(encoding="utf-8", errors="replace")
            except (OSError, ValueError, RuntimeError):
                pass


_force_utf8_streams()

if __name__ == "__main__":
    sys.exit(main())
