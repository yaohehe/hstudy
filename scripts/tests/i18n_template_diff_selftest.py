"""等价性闸门（scripts/i18n_template_diff.py）**披露机制**的自我测试。

为什么要有这个文件：W9 Task 3 把 [DISCLOSED_RESIDUALS] 从"按值 pop"改成了可带
`table_field` / `page_path` 限定的形状（L-19 的收口）。这项改动有一个方向的错误是主命令
**看不出来**的：拆条不完整（同值的第二个字段没登记）时，剩下的那一侧会被别的条目顺带按值摘掉，
读数与拆分前逐字节相同——"档门是绿的"，但那条字段声明其实是死的，L-19 的口子还在。
所以这里不跑主命令，而是直接对 [apply_disclosures] 喂构造出来的两侧集合，把"限定只摘该字段 /
该页面"的正反例逐条钉住；最后再拿工作区的四十六张表做一次**对账**：对每个带 `table_field` 的值，
"已声明字段集合"必须等于"树上实际持有该值的字段集合"。后者就是 L-19 的收口本身——
有人往任一中文表新写一条同值，这条对账立刻红，必须补声明或改值才能绿。

用法（仓库根目录或任意目录都可）：
    python scripts/tests/i18n_template_diff_selftest.py
退出码：0 = 全部通过；1 = 有断言失败（逐条打印期望/实际）。

覆盖清单（分节见 main 里的 print）：
  1) 表侧 `table_field`：只摘该字段 / 值仍被别的字段持有 / 第二条把最后一个槽摘掉 /
     不带限定仍按值摘（向后兼容）/ 限定到不持有该值的字段 → 什么都不摘 /
     `表名.字段` 与 `表名Zh.字段` 两种写法等价
  2) 页面侧 `page_path`：只摘该文件 / 别的页面仍有原文时值留着 / 唯一出处被摘掉 /
     限定到没有该原文的路径 → 什么都不摘 / 不带 path 仍按值摘
  3) 向后兼容：无限定条目的说明行逐字节不变 / 双侧条目按值摘两侧 / 带限定的说明行写出限定 /
     说明行条数 == 条目条数
  4) 过期判定：字段对得上 → "单边在范围"（不失败）/ 值在但字段换了 → "登记过期" /
     页面限定后整枝范围里没有该文件 → "登记过期" / 无限定条目的旧判据不变
  5) 真实树对账：每条 `table_field` 都命中 / 声明字段集合 == 实际字段集合（拆条完整性）/
     每条 `page_path` 都是 [BRANCH_PAGES] 里的路径且文件在库 / 条目键只有约定的 5 个
  6) L-19 的正对照：同一张表里新写一条同值字段不会被这条披露吞掉（会作为 [A] 的 invented 出现）
"""
import os
import sys

# 自测会 import 闸门脚本做单元级断言；别让它在仓库里留下 __pycache__（.gitignore 没覆盖）
sys.dont_write_bytecode = True

HERE = os.path.dirname(os.path.abspath(__file__))
REPO_ROOT = os.path.dirname(os.path.dirname(HERE))
SCRIPTS = os.path.join(REPO_ROOT, "scripts")
GATE = os.path.join(SCRIPTS, "i18n_template_diff.py")
SRC_ROOT = os.path.join(REPO_ROOT, "composeApp", "src", "commonMain", "kotlin")

# 条目允许出现的键。键名打错（`table_filed`）在闸门里会被 `entry.get` 静默忽略，
# 于是"限定"变成"按值"——读数不变、复核看着也像今天这样，正是要防的一类静默失效。
ALLOWED_KEYS = {"page", "table", "reason", "table_field", "page_path"}
# L-19 点名的六条两侧同形共享词：它们必须都带 table_field 限定。
COMMON_WORDS = ["取消", "确定", "确认", "删除", "分", "+⟨$⟩分"]
# W8 LearningScoreCopy 的 5 个通用单词：它们必须都带 page_path 限定。
GENERIC_WORDS = ["优秀", "良好", "不错", "起步", "待开始"]

failures = []
checks = 0


def check(label, ok, detail=""):
    global checks
    checks += 1
    print("  [%s] %s%s" % ("OK  " if ok else "FAIL", label, "" if ok else "  <<< " + detail))
    if not ok:
        failures.append(label)
    return ok


def page_map(*pairs):
    """构造页面侧集合：[("模板", [("路径", 行号), …]), …] -> {模板: [(路径, 行号, 是否格式串)]}。

    形态与 [i18n_template_diff.page_templates] 的返回值一致（第三位只用于 strict 层拆分位点）。
    """
    return {text: [(path, line, False) for path, line in sites] for text, sites in pairs}


def entry(page=None, table=None, reason="测试条目", **qual):
    out = {"page": page, "table": table, "reason": reason}
    out.update(qual)
    return out


def main():
    print("== 0) 预检：闸门可导入、登记表形态 ==")
    if not os.path.isfile(GATE):
        print("  [FAIL] 闸门脚本不存在：%s" % GATE)
        return 1
    sys.path.insert(0, SCRIPTS)
    import i18n_template_diff as gate

    entries = gate.DISCLOSED_RESIDUALS
    check("登记表非空（%d 条）" % len(entries), len(entries) > 100, "只有 %d 条" % len(entries))
    bad_keys = sorted({k for e in entries for k in e} - ALLOWED_KEYS)
    check("条目键只有约定的 5 个（键名打错不会被静默忽略）", not bad_keys, "多出 %s" % bad_keys)
    no_side = [i for i, e in enumerate(entries) if not (e.get("page") or e.get("table"))]
    check("每条登记 page/table 至少给一个", not no_side, "第 %s 条两侧都空" % no_side)
    orphan_qual = [i for i, e in enumerate(entries)
                   if (e.get("page_path") and not e.get("page")) or (e.get("table_field") and not e.get("table"))]
    check("限定必须挂在它限定的那一侧上（page_path 要配 page、table_field 要配 table）",
          not orphan_qual, "第 %s 条限定没有宿主侧" % orphan_qual)

    print("\n== 1) 表侧 table_field：只摘该字段 ==")
    table = {"取消": ["CommonStringsZh.cancelButton", "FooStringsZh.bar"]}
    ok, notes = gate.apply_disclosures({}, table, [entry(table="取消", table_field="CommonStrings.cancelButton")])
    check("带 table_field 时只摘该字段（值仍被别的字段持有 → 留在集合里）",
          table == {"取消": ["FooStringsZh.bar"]}, "实际 %r" % table)
    check("……这一次仍算「已披露」，不判失败", ok and any("（已披露）" in n for n in notes),
          "ok=%r notes=%r" % (ok, notes))
    gate.apply_disclosures({}, table, [entry(table="取消", table_field="FooStrings.bar")])
    check("第二个字段也声明后，值才从表侧集合里消失", "取消" not in table, "实际 %r" % table)

    table = {"删除": ["CommonStringsZh.deleteContentDescription", "FooStringsZh.bar"]}
    gate.apply_disclosures({}, table, [entry(table="删除")])
    check("不带限定（向后兼容）仍是按值摘：两个字段一起消失", "删除" not in table, "实际 %r" % table)

    table = {"分": ["CommonStringsZh.pointsUnit"]}
    ok, notes = gate.apply_disclosures({}, table, [entry(table="分", table_field="CommonStrings.minuteLabel")])
    check("限定到不持有该值的字段 → 什么都不摘（值留在集合里，会被 [A] 报成表侧独有）",
          table == {"分": ["CommonStringsZh.pointsUnit"]}, "实际 %r" % table)
    check("……并只打「（未用到）」，不判失败（与阶段 1 的未用到同义）",
          ok and any("（未用到）" in n for n in notes), "ok=%r notes=%r" % (ok, notes))

    table = {"确定": ["SettingsStringsZh.passwordOkButton"]}
    gate.apply_disclosures({}, table, [entry(table="确定", table_field="SettingsStringsZh.passwordOkButton")])
    check("`表名Zh.字段` 与 `表名.字段` 两种写法等价（字段 id 会归一化）",
          "确定" not in table, "实际 %r" % table)

    print("\n== 2) 页面侧 page_path：只摘该文件 ==")
    page = page_map(("优秀", [("com/hstudy/app/ui/common/A.kt", 10),
                             ("com/hstudy/app/ui/common/B.kt", 20)]))
    ok, _notes = gate.apply_disclosures(
        page, {}, [entry(page="优秀", page_path="com/hstudy/app/ui/common/A.kt")])
    check("带 page_path 时只摘该文件的出现（别的页面仍有原文 → 值留在集合里）",
          page == {"优秀": [("com/hstudy/app/ui/common/B.kt", 20, False)]}, "实际 %r" % page)
    check("……这一次仍算「已披露」，不判失败", ok)

    page = page_map(("待开始", [("com/hstudy/app/ui/common/A.kt", 11)]))
    gate.apply_disclosures(page, {}, [entry(page="待开始", page_path="com/hstudy/app/ui/common/A.kt")])
    check("该文件是唯一出处时，值从页面集合里消失", "待开始" not in page, "实际 %r" % page)

    page = page_map(("起步", [("com/hstudy/app/ui/common/A.kt", 12)]))
    ok, notes = gate.apply_disclosures(
        page, {}, [entry(page="起步", page_path="com/hstudy/app/ui/common/Z.kt")])
    check("限定到没有该原文的路径 → 什么都不摘（值留在页面集合里，会被报成 missing）",
          "起步" in page, "实际 %r" % page)
    check("……并只打「（未用到）」，不判失败", ok and any("（未用到）" in n for n in notes))

    page = page_map(("良好", [("com/hstudy/app/ui/common/A.kt", 13),
                             ("com/hstudy/app/ui/common/B.kt", 14)]))
    gate.apply_disclosures(page, {}, [entry(page="良好")])
    check("不带 page_path（向后兼容）仍是按值摘：两个页面一起消失", "良好" not in page, "实际 %r" % page)

    print("\n== 3) 向后兼容：无限定条目的行为与说明行逐字节不变 ==")
    page = page_map(("取消", [("com/hstudy/app/ui/common/A.kt", 1)]))
    table = {"取消": ["CommonStringsZh.cancelButton"]}
    _ok, notes = gate.apply_disclosures(page, table, [entry(page="取消", table="取消", reason="R")])
    check("无限定双侧条目的说明行逐字节不变（没有任何限定标记）",
          notes == ["    （已披露）page='取消' table='取消'：R"], "实际 %r" % notes)
    check("无限定双侧条目仍按值摘两侧（页面与表都空）", not page and not table,
          "page=%r table=%r" % (page, table))

    page = page_map(("分", [("com/hstudy/app/ui/common/A.kt", 1)]))
    table = {"分": ["CommonStringsZh.pointsUnit"]}
    _ok, notes = gate.apply_disclosures(page, table, [entry(
        page="分", table="分", table_field="CommonStrings.pointsUnit",
        page_path="com/hstudy/app/ui/common/A.kt", reason="R")])
    check("带限定的说明行把两个限定都写出来",
          "［限定 table_field=CommonStrings.pointsUnit，page_path=com/hstudy/app/ui/common/A.kt］" in notes[0],
          "实际 %r" % notes)
    check("……并且两侧都只摘了限定命中的那一次（值都从集合里消失）", not page and not table,
          "page=%r table=%r" % (page, table))

    _ok, notes = gate.apply_disclosures(
        {}, {}, [entry(page="a"), entry(table="b"), entry(page="c", table="d")])
    check("每个条目恰好产出一条说明行（登记条数与输出行数一一对应）", len(notes) == 3,
          "实际 %d 条：%r" % (len(notes), notes))

    print("\n== 4) 过期判定：限定把「存在」的判据也收紧了 ==")
    page = page_map(("分", [("com/hstudy/app/ui/common/A.kt", 1)]))
    scope_page = page_map(("分", [("com/hstudy/app/ui/common/A.kt", 1)]))
    scope_table = {"分": ["CommonStringsZh.pointsUnit"]}
    ok, notes = gate.apply_disclosures(
        page, {}, [entry(page="分", table="分", table_field="CommonStrings.pointsUnit")],
        scope_page, scope_table)
    check("字段对得上：只报「（单边在范围）」，不判失败",
          ok and any("（单边在范围）" in n for n in notes), "ok=%r notes=%r" % (ok, notes))
    check("……并照旧摘掉能找到的那一侧（页面侧）", not page, "实际 %r" % page)

    page = page_map(("分", [("com/hstudy/app/ui/common/A.kt", 1)]))
    ok, notes = gate.apply_disclosures(
        page, {}, [entry(page="分", table="分", table_field="CommonStrings.pointsUnit")],
        page_map(("分", [("com/hstudy/app/ui/common/A.kt", 1)])),
        {"分": ["CommonStringsZh.minuteLabel"]})
    check("值在、但字段换了 → 「（登记过期）」并判失败（字段限定同样会过期）",
          not ok and any("（登记过期）" in n for n in notes), "ok=%r notes=%r" % (ok, notes))

    page = page_map(("优秀", [("com/hstudy/app/ui/common/W.kt", 1)]))
    ok, notes = gate.apply_disclosures(
        page, {"优秀": ["X.y"]},
        [entry(page="优秀", table="优秀", page_path="com/hstudy/app/ui/common/Z.kt")],
        page_map(("优秀", [("com/hstudy/app/ui/common/A.kt", 9)])),
        {"优秀": ["X.y"]})
    check("整枝范围里有这个值、但没有这个文件的原文 → 「（登记过期）」并判失败",
          not ok and any("（登记过期）" in n for n in notes), "ok=%r notes=%r" % (ok, notes))

    page = page_map(("取消", [("com/hstudy/app/ui/common/A.kt", 1)]))
    ok, notes = gate.apply_disclosures(
        page, {}, [entry(page="取消", table="取消")],
        page_map(("取消", [("com/hstudy/app/ui/common/A.kt", 1)])),
        {"别的": ["X.y"]})
    check("无限定条目的旧判据不变：另一半在整枝范围里也不存在 → 「（登记过期）」",
          not ok and any("（登记过期）" in n for n in notes), "ok=%r notes=%r" % (ok, notes))

    print("\n== 5) 真实树对账：声明 == 树上实际（拆条完整性） ==")
    table_map = gate.table_templates(gate.WORKTREE, gate.BRANCH_TABLES)
    declared = [(e["table"], e["table_field"]) for e in entries if e.get("table_field")]
    check("带 table_field 的条目数（%d）不少于 L-19 的收口对象（6 个共享词 + 3 条定值/模板成对）"
          % len(declared), len(declared) >= 9, "只有 %d 条" % len(declared))
    misses = [(text, field) for text, field in declared if not gate._table_hit(table_map, text, field)]
    check("每条 (表侧值, table_field) 都在工作区整枝表组里命中（表名/字段名没有笔误）",
          not misses, "没命中 %r" % misses)
    by_value = {}
    for text, field in declared:
        by_value.setdefault(text, set()).add(gate._norm_field(field))
    gaps = {}
    for text, fields in by_value.items():
        actual = {gate._norm_field(owner) for owner in table_map.get(text, [])}
        if fields != actual:
            gaps[text] = {"未声明": sorted(actual - fields), "已声明但树上没有": sorted(fields - actual)}
    check("每个带 table_field 的值：已声明字段集合 == 树上实际持有该值的字段集合"
          "（新写一条同值字段 → 这里红，必须补声明；L-19 的收口）",
          not gaps, "对不上 %r" % gaps)

    path_entries = [e["page_path"] for e in entries if e.get("page_path")]
    decl_paths = set(path_entries)
    branch_paths = {rel for _rev, rel in gate.BRANCH_PAGES}
    check("带 page_path 的条目数（%d）不少于 5 个通用词" % len(path_entries), len(path_entries) >= 5,
          "只有 %d 条" % len(path_entries))
    unknown = sorted(decl_paths - branch_paths)
    check("每条 page_path 都是 [BRANCH_PAGES] 里登记的路径（否则那条披露永远命中不了）",
          not unknown, "不在登记页里 %r" % unknown)
    missing_files = sorted(rel for rel in decl_paths
                           if not os.path.isfile(os.path.join(SRC_ROOT, rel)))
    check("每条 page_path 对应的文件在工作区存在", not missing_files, "缺 %r" % missing_files)

    unqualified_common = [w for w in COMMON_WORDS
                          if not any(e.get("table") == w and e.get("table_field") for e in entries)]
    check("六条两侧同形共享词（%s）全部带 table_field" % " / ".join(COMMON_WORDS),
          not unqualified_common, "还没限定：%r" % unqualified_common)
    unqualified_generic = [w for w in GENERIC_WORDS
                           if not any(e.get("page") == w and e.get("page_path") for e in entries)]
    check("5 个通用单词（%s）全部带 page_path" % " / ".join(GENERIC_WORDS),
          not unqualified_generic, "还没限定：%r" % unqualified_generic)

    print("\n== 6) L-19 正对照：同值的新字段不会被吞掉 ==")
    page = page_map(("取消", [("com/hstudy/app/ui/common/A.kt", 5)]))
    table = {"取消": ["CommonStringsZh.cancelButton", "BrandNewStringsZh.someField"]}
    _ok, _notes = gate.apply_disclosures(
        page, table, [entry(page="取消", table="取消", table_field="CommonStrings.cancelButton")])
    check("同一张表里新写的同值字段仍在表侧集合里（会作为 [A] 的 invented 出现，不再被这条披露吞掉）",
          "取消" in table and "取消" not in page, "page=%r table=%r" % (page, table))
    check("此时值对应的字段只剩新写的那一个（旧字段已被摘）",
          table.get("取消") == ["BrandNewStringsZh.someField"], "实际 %r" % table)

    print("\n== 汇总 ==")
    print("  断言 %d 条，失败 %d 条" % (checks, len(failures)))
    if failures:
        for name in failures:
            print("  FAIL: %s" % name)
        print("披露机制自测**未通过** —— 限定语义坏掉时不得声称 L-19 已收口。")
        return 1
    print("披露机制自测通过：table_field / page_path 的正反例、向后兼容、过期判定、"
          "真实树字段对账、L-19 正对照全部符合预期。")
    return 0


def _force_utf8_streams():
    """stdout/stderr 钉 UTF-8（终审 D-B 的同类护栏）。

    本自测打印中文与 `⟨$⟩`；在 GBK 的管道/重定向下会 UnicodeEncodeError 而假红。
    完整理由见 `scripts/i18n_template_diff.py` 的同名函数。放文件末尾是刻意的：
    上面的断言都按"模块 import 期什么都还没执行"的形态写。
    """
    for stream in (sys.stdout, sys.stderr):
        if hasattr(stream, "reconfigure"):
            try:
                stream.reconfigure(encoding="utf-8", errors="replace")
            except (OSError, ValueError, RuntimeError):
                pass


_force_utf8_streams()

if __name__ == "__main__":
    sys.exit(main())
