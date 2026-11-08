#!/usr/bin/env python3
"""Ruling 20(b) 的等价性证据：迁移前的页面中文模板 与 迁移后的文案表，逐字节比对。

用法（不带参数就是 Task 7 那一组，直接跑即复现任务报告 §4 的两行结论）：

    python scripts/i18n_template_diff.py
    python scripts/i18n_template_diff.py --pages-rev d336fb8 --tables-rev HEAD
    python scripts/i18n_template_diff.py --pages-rev <迁移前的 rev> \\
        --page com/hstudy/app/ui/child/ChildHomeScreen.kt \\
        --table com/hstudy/app/i18n/ChildHomeStrings.kt#ChildHomeStringsZh

三层比对（都是"集合相等"，不是条数相等）：

* strict —— 只把插值（`$x` / `${…}`）塌成一个"数值位"记号 `⟨$⟩`，**数字格式串原样保留**。
  这层报出"有多少条模板是整串逐字节搬进表里的"，以及"哪几条走了 Ruling 20 的拆分"：
  页面写 `strFormat("%.1f 秒", x)` 而表写 `"$seconds 秒"` 的那一处，在 strict 层就是唯一的不一致，
  它必须被点名而不是被归一化掉（评审 Task 7 时指出：旧脚本的归一化恰好把它显示成"也相同"）。
* [A] —— 再把数字格式串（`%d`、`%02d`、`%.1f` …）也塌成同一个记号，即"数值位在哪都算对齐"，
  比较两边的模板集合是否完全一致（missing = 页面有表里没有，invented = 反过来）。
* [B] —— 去掉所有数值位之后，只比中文文案本身（空格、标点、`·`、emoji 都算文案）。

局限（有意为之，别当成 bug）：
* 只认非三引号的双引号字面量；本阶段迁移的文件里没有三引号字符串。
* 相邻字符串拼接（`"a" + "b"`）算两条模板，所以中文表里不要写跨行拼接的**中文**值
  （Task 7 的 ZH 表没有；EN 表有，本脚本不读 EN 侧）。
* 注释里的中文按 Ruling 18 不算可迁移文案，脚本会剥掉 `//` 行注释与 `/* */` 块注释。
* 表侧字段名按"字面量前最近的一个 `字段 =`"归属，只为把报错指到具体字段，不参与等价性判定。

退出码：[A] 与 [B] 都成立返回 0，否则返回 1（可当闸门用）。
"""
import argparse
import io
import os
import re
import subprocess
import sys

SRC_ROOT = "composeApp/src/commonMain/kotlin/"
# 数值位记号：刻意用非汉字字符，否则"只剩插值"的排版串（`"${x}m"`）会被认成中文文案。
VALUE = "⟨$⟩"
CJK = re.compile(r"[一-鿿]")
FORMAT_SPEC = re.compile(r"%[-+ 0#]*[0-9]*(?:\.[0-9]+)?[a-zA-Z]")
FIELD_ASSIGN = re.compile(r"^\s*(\w+)\s*=", re.MULTILINE)

# Task 7 的默认比对组：页面取迁移前的 d336fb8，表取当前工作区。
DEFAULT_PAGES = [
    "com/hstudy/app/ui/common/PomodoroTimer.kt",
    "com/hstudy/app/ui/focus/ChildFocusStatsScreen.kt",
    "com/hstudy/app/ui/focus/ChildFocusTrainingScreen.kt",
]
DEFAULT_TABLES = [
    ("com/hstudy/app/i18n/PomodoroStrings.kt", "PomodoroStringsZh"),
    ("com/hstudy/app/i18n/FocusStrings.kt", "FocusStringsZh"),
]
WORKTREE = "WORKTREE"


def read(rev, rel):
    """rev == WORKTREE 时读工作区文件，否则读 `git show rev:rel`。"""
    path = SRC_ROOT + rel
    if rev == WORKTREE:
        return io.open(path, encoding="utf-8").read()
    proc = subprocess.run(["git", "show", "%s:%s" % (rev, path)], capture_output=True)
    if proc.returncode:
        sys.exit("!! git show %s:%s 失败：%s" % (rev, path, proc.stderr.decode("utf-8", "replace")))
    return proc.stdout.decode("utf-8")


def literals(src):
    """返回 [(offset, 字面量文本)]，字面量文本里的插值整体塌成 VALUE，注释被跳过。"""
    out = []
    i, n = 0, len(src)
    while i < n:
        c = src[i]
        if c == "/" and src[i + 1:i + 2] == "/":
            j = src.find("\n", i)
            i = n if j < 0 else j
            continue
        if c == "/" and src[i + 1:i + 2] == "*":
            depth = 1
            i += 2
            while i < n and depth:
                if src[i:i + 2] == "/*":
                    depth += 1
                    i += 2
                elif src[i:i + 2] == "*/":
                    depth -= 1
                    i += 2
                else:
                    i += 1
            continue
        if c == '"':
            text, nxt = scan_string(src, i)
            out.append((i, text))
            i = nxt
            continue
        i += 1
    return out


def scan_string(src, i):
    """src[i] == '"'；返回 (塌掉插值后的内容, 闭引号之后的下标)。"""
    i += 1
    out = []
    n = len(src)
    while i < n:
        c = src[i]
        if c == "\\":
            out.append(src[i:i + 2])
            i += 2
            continue
        if c == '"':
            return "".join(out), i + 1
        if c == "$" and src[i + 1:i + 2] == "{":
            _, i = scan_interp(src, i + 1)
            out.append(VALUE)
            continue
        if c == "$" and src[i + 1:i + 2] and (src[i + 1].isalnum() or src[i + 1] == "_"):
            i += 1
            while i < n and (src[i].isalnum() or src[i] == "_"):
                i += 1
            out.append(VALUE)
            continue
        out.append(c)
        i += 1
    return "".join(out), i


def scan_interp(src, i):
    """src[i] == '{'（即 `${` 的那个花括号）；返回 (内容, 匹配闭花括号之后的下标)。"""
    depth = 0
    out = []
    n = len(src)
    while i < n:
        c = src[i]
        if c == "{":
            depth += 1
        elif c == "}":
            depth -= 1
            if depth == 0:
                return "".join(out), i + 1
        elif c == '"':
            inner, i = scan_string(src, i)
            out.append(inner)
            continue
        out.append(c)
        i += 1
    return "".join(out), i


def page_templates(rev, rels):
    """页面侧：所有塌掉插值后仍含汉字的字面量 -> {模板: [(文件, 行, 原文是否数字格式串)]}。"""
    found = {}
    for rel in rels:
        src = read(rev, rel)
        for offset, text in literals(src):
            if not CJK.search(text):
                continue
            lineno = src.count("\n", 0, offset) + 1
            # 以数字格式串开头的字面量 = 单位被写在格式串"里面"，正是 Ruling 20 拆分位点的形状
            found.setdefault(text, []).append((rel, lineno, bool(FORMAT_SPEC.match(text))))
    return found


def table_templates(rev, specs):
    """表侧：从 `val <Marker>` 起、到下一个顶层 `val ` 止，抽中文模板 -> {模板: 字段名}。"""
    found = {}
    for rel, marker in specs:
        src = read(rev, rel)
        start = src.index("val %s" % marker)
        nxt = src.find("\nval ", start + 1)
        region = src[start:nxt if nxt > 0 else len(src)]
        fields = [(m.start(), m.group(1)) for m in FIELD_ASSIGN.finditer(region)]
        for offset, text in literals(region):
            if not CJK.search(text):
                continue
            owner = [name for pos, name in fields if pos <= offset]
            found.setdefault(text, []).append("%s.%s" % (marker, owner[-1] if owner else "?"))
    return found


def strip_values(text):
    return text.replace(VALUE, "")


def collapse_formats(text):
    return FORMAT_SPEC.sub(VALUE, text)


def main():
    ap = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    ap.add_argument("--pages-rev", default="d336fb8", help="页面取的 git rev（默认：Task 7 迁移前的父提交）")
    ap.add_argument("--tables-rev", default=WORKTREE, help="文案表取的位置（默认 WORKTREE，也可给 rev）")
    ap.add_argument("--page", action="append", dest="pages", help="可重复；相对 %s 的页面路径" % SRC_ROOT)
    ap.add_argument("--table", action="append", dest="tables", help="可重复；表文件#Zh 变量名")
    args = ap.parse_args()

    pages = args.pages or DEFAULT_PAGES
    tables = [t.split("#") for t in (args.tables or ["%s#%s" % (rel, m) for rel, m in DEFAULT_TABLES])]

    page = page_templates(args.pages_rev, pages)
    table = table_templates(args.tables_rev, tables)

    print("页面 rev=%s（%d 个文件）/ 文案表 rev=%s（%d 张表）" % (args.pages_rev, len(pages), args.tables_rev, len(tables)))
    occ = {}
    for rel in pages:
        src = read(args.pages_rev, rel)
        hits = [t for _, t in literals(src) if CJK.search(t)]
        occ[os.path.basename(rel)] = (len(hits), len(set(hits)))
    print("每页（塌掉插值后的中文文案出现次数, 去重）: %s" % occ)
    print("distinct templates pages / tables        : %d / %d" % (len(page), len(table)))

    # strict 层：数字格式串不塌，因此 Ruling 20 的拆分位点会在这里暴露出来。
    strict_only_page = sorted(set(page) - set(table))
    strict_only_table = sorted(set(table) - set(page))
    identical = len(set(page) & set(table))
    print("\n[strict] 整串逐字节相同（数值位塌缩、格式串不塌）: %d 条" % identical)
    print("[strict] 仅页面有: %d %s" % (len(strict_only_page), ["%r" % t for t in strict_only_page]))
    print("[strict] 仅表里有: %d %s" % (len(strict_only_table), ["%r" % t for t in strict_only_table]))
    splits = sorted(text for text, sites in page.items() if any(fmt for _rel, _line, fmt in sites))
    print("[strict] Ruling 20 拆分位点（页面的数字格式串内部带单位）: %d 条" % len(splits))
    for text in splits:
        for rel, lineno, _ in page[text]:
            print("           页面 %s:%d  %-12r -> 表 %s" % (rel, lineno, text, table.get(collapse_formats(text), "未找到同形模板")))

    # [A]：格式串也塌成数值位，比较模板集合。
    norm_page = {collapse_formats(t) for t in page}
    missing = sorted(norm_page - set(table))
    invented = sorted(set(table) - norm_page)
    print("\n[A] full template incl. value slots identical : %s (%d missing, %d invented)"
          % ("YES" if not missing and not invented else "NO", len(missing), len(invented)))
    for text in missing:
        raw = miss_raw(page, text)
        print("    页面有、表里没有: %r  来自 %s" % (text, ["%s:%d" % site[:2] for site in page.get(raw, [])]))
    for text in invented:
        print("    表里有、页面没有: %r  字段 %s" % (text, table[text]))

    # [B]：只剩中文文案本身（空格/标点/emoji 都参与比较）。
    copy_page = sorted({strip_values(collapse_formats(t)) for t in page if strip_values(collapse_formats(t)).strip()})
    copy_table = sorted({strip_values(t) for t in table if strip_values(t).strip()})
    diff = [t for t in copy_page if t not in copy_table] + [t for t in copy_table if t not in copy_page]
    print("[B] Chinese copy text alone (spaces included) : %s, %d difference"
          % ("YES" if not diff else "NO", len(diff)))
    print("    distinct Chinese copy texts compared      : %d / %d" % (len(copy_page), len(copy_table)))
    for text in diff:
        print("    差异: %r" % text)
    return 0 if not missing and not invented and not diff else 1


def miss_raw(page, norm_text):
    """把归一化后的模板反查回页面原始模板（用于打印来源行号）。"""
    for raw in page:
        if collapse_formats(raw) == norm_text:
            return raw
    return None


if __name__ == "__main__":
    sys.exit(main())
