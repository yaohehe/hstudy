"""存储边界守卫：禁止界面文案表的字符串进入写库调用或业务键。

背景（Ruling 25）：双语迁移会把 `"中文"` 换成 `strings.<field>`。若该值被写进数据库或参与
业务判等（如 `checkIn(bizKey, description)` 的 description 参与幂等比较），语言切换就会改变
存储字节，导致历史记录按语言分裂、幂等判定被误触发。spec §5 的边界正是为此而设。

用法：
    python scripts/i18n_storage_boundary.py            # 扫描全部 commonMain
退出码 1 表示发现可疑写入；0 表示干净。
"""
import io
import os
import re
import sys

SRC = "composeApp/src/commonMain/kotlin/com/hstudy/app"

# 会写入数据库或参与业务判等的方法名（来自实际调用点，勿凭想象增删）
SINKS = [
    "submitApproval", "recordFocusSession", "adjustPoints", "checkIn", "checkInForTask",
    "tryUnlockAchievement", "addRecord", "insert", "update", "save", "upsert", "deleteBy",
    "submitProof", "recordMistake", "saveHabit", "setApproval",
    # Task 7b 第 1 轮审查补：每日计划的两条写入路径把 `DailyPlanEntity.taskTitle` 直接写库
    # （ChildDailyPlanScreen.kt:100 addPlan / FocusRepository.kt:40），现有的 "insert"/"save" 匹配不到
    # `addPlan(` 这种名字。这两个调用点当前传的是用户输入（对话框 TextField），但一旦有人
    # 换成文案表就是这个缺陷的复发形态，所以按实际调用点补进来（补完扫描仍然 0 命中）。
    "addPlan", "updatePlan",
]

# 文案来源的三种写法：局部变量 strings.X、表字段 XyzStrings.y / AppStringsZh.x、直接调 lambda
#
# 外层必须是非捕获分组：Task 9 修 matcher 时发现原先没有这层括号，而 `|` 的优先级最低，
# 于是拼进 TAINT_DELEGATE 时整条模式被**从中间劈成三个备选支**——第 2、3 支变成"在文件任何位置
# 找 AppStringsZh.x / XyzStringsEn.y"，既不再要求 `var x by remember { … }` 前缀（污点规则形同虚设），
# 又没有捕获组（group(1) 是 None，扫描时直接 TypeError 崩掉）。这个崩溃正是往 ChildHomeScreen 注入
# `PomodoroStringsZh.breathingGuide` 时暴露出来的，属于 Ruling 28 之外的第二个闸门缺陷，一并修掉。
COPY_SOURCE = (
    r"(?:"
    r"\bstrings\.[A-Za-z]\w*"
    r"|\bAppStrings(?:Zh|En)\.\w+"
    r"|(?:[A-Za-z]\w*Strings(?:Zh|En))\.\w+"
    r")"
)
COPY_REF = re.compile(COPY_SOURCE)

CALL_HEAD = re.compile(r"\b(" + "|".join(re.escape(s) for s in SINKS) + r")\s*\(")

# Ruling 28 的缺陷与本节的修法（Task 9 开工前修，先于页面迁移单独提交）：
# 原先 CALL 是"单行正则"，而 scan() 又按行喂给它，于是**跨行的调用永远不会被匹配**。
# ChildHomeScreen.kt:583-589 的 checkIn(bizKey = …, description = …) 正好跨 7 行，
# ChildDailyPlanScreen.kt:100 的 addPlan(DailyPlanEntity(…)) 跨 8 行 —— 两处都在盲区内，
# 也就是说这条"硬约束"闸门对最危险的那个调用点（参与幂等判等的 description）从来没亮过。
# 现在改成：定位 `sink(` 的左括号，再用括号配对扫描取出**跨行**的实参文本。
# 扫描时跳过字符串字面量（英文文案里本来就写着括号，见 i18n_template_diff.py 同样的教训），
# 所以 `Text("已完成(点击撤销)")` 这种串不会把配对带偏。
def strip_comments(text):
    """把 `//` 与 `/* */` 注释内容替换成空格（换行原样保留），下标与行号仍对得上原文件。

    字符串字面量整体跳过：注释定界符出现在文案串里（URL、`http://…` 之类）时不能当注释剥掉，
    否则会把实参里的文案抹白，闸门反而漏抓。
    """
    out = list(text)
    i, n = 0, len(text)
    while i < n:
        c = text[i]
        if c == '"':
            i += 1
            triple = text.startswith('"""', i - 1)
            if triple:
                i += 2
            while i < n:
                if text[i] == "\\":
                    i += 2
                    continue
                if triple and text.startswith('"""', i):
                    i += 3
                    break
                if text[i] == '"':
                    i += 1
                    break
                i += 1
            continue
        if c == "/" and text[i + 1:i + 2] == "/":
            j = text.find("\n", i)
            j = n if j < 0 else j
            for k in range(i, j):
                if out[k] != "\n":
                    out[k] = " "
            i = j
            continue
        if c == "/" and text[i + 1:i + 2] == "*":
            j = text.find("*/", i + 2)
            j = n if j < 0 else j + 2
            for k in range(i, j):
                if out[k] != "\n":
                    out[k] = " "
            i = j
            continue
        i += 1
    return "".join(out)


def extract_args(code, open_paren):
    """code[open_paren] == '('；返回 (实参文本, 闭括号下标)，扫不完（语法残缺）返回 None。"""
    depth = 0
    i = open_paren
    n = len(code)
    while i < n:
        c = code[i]
        if c == '"':
            i += 1
            triple = code.startswith('"""', i - 1)
            if triple:
                i += 2
            while i < n:
                if code[i] == "\\":
                    i += 2
                    continue
                if triple and code.startswith('"""', i):
                    i += 3
                    break
                if code[i] == '"':
                    i += 1
                    break
                i += 1
            continue
        if c == "(":
            depth += 1
        elif c == ")":
            depth -= 1
            if depth == 0:
                return code[open_paren + 1:i], i
        i += 1
    return None

# 一级污点（赋值形态）：`var x = ...strings.y...` / `val x = strings.y`
# 之后的 sink 调用只要出现 x 就算违规。没有数据流分析，只做同文件赋值追踪，
# 因为本类缺陷的实际形态就是"把文案赋给状态，再写库"。
TAINT_ASSIGN = re.compile(
    r"\b(?:var|val)\s+([A-Za-z_]\w*)\s*(?:by\s+remember\s*\{[^}]*\})?\s*=\s*(.*"
    + COPY_SOURCE + r".*)",
    re.DOTALL,
)

# 一级污点（委托形态）：`var x by remember { mutableStateOf(strings.y) }`。
# Task 7b 审查的实证补：原先只有上面那条，所以 ChildPomodoroScreen.kt:47 这一形**漏抓**，
# :175 的 recordFocusSession(minutes, focusTarget, 0) 当时只报了 :177 的直接引用。
# 规范值不是文案：读的是 CanonicalFocusText.* / 字面量时，COPY_SOURCE 匹配不上，不会误报。
TAINT_DELEGATE = re.compile(
    r"\b(?:var|val)\s+([A-Za-z_]\w*)\s+by\s+remember(?:Saveable)?\s*(?:\([^()]*\))?\s*\{[^}]*"
    + COPY_SOURCE + r"[^}]*\}"
)


def collect_taint(text):
    tainted = set()
    for pattern in (TAINT_ASSIGN, TAINT_DELEGATE):
        for match in pattern.finditer(text):
            name = match.group(1)
            # 只有参与了的分组 1 才是变量名：上一段注释记录的那种"备选支被劈开"的形态会给 None，
            # 直接塞进 tainted 就会在扫描时崩掉（Task 9 注入变异时实测到）。
            if name:
                tainted.add(name)
    return tainted


def scan(path):
    with io.open(path, encoding="utf-8") as handle:
        raw = handle.read()
    code = strip_comments(raw)
    tainted = collect_taint(code)
    hits = []
    for match in CALL_HEAD.finditer(code):
        extracted = extract_args(code, match.end() - 1)
        if extracted is None:
            continue
        args, close = extracted
        start_line = code.count("\n", 0, match.start()) + 1
        end_line = code.count("\n", 0, close) + 1
        # 报错文本压成一行：跨行调用原来根本报不出来，这里先保证"报得出 + 行号对得上"
        excerpt = " ".join(code[match.start():close + 1].split())
        span = "%d" % start_line if start_line == end_line else "%d-%d" % (start_line, end_line)
        why = None
        if COPY_REF.search(args):
            why = "直接引用文案表"
        else:
            leaked = [name for name in tainted if re.search(r"\b%s\b" % re.escape(name), args)]
            if leaked:
                why = "经由变量 %s 传入" % ", ".join(sorted(leaked))
        if why:
            hits.append((span, match.group(1), excerpt, why))
    return hits


def main():
    found = []
    for dirpath, _names, filenames in os.walk(SRC):
        for name in filenames:
            if not name.endswith(".kt"):
                continue
            full = os.path.join(dirpath, name)
            for span, sink, text, why in scan(full):
                found.append((os.path.relpath(full, SRC), span, sink, text, why))
    if not found:
        print("存储边界守卫通过：没有文案表字符串（含经由变量传入）出现在写库/业务键调用参数里")
        print("（跨行调用已纳入匹配：Ruling 28 的行级盲区于 Task 9 修掉，见本文件 CALL_HEAD 一节）")
        return 0
    print("发现 %d 处疑似「界面文案进入存储或业务键」：" % len(found))
    for rel, span, sink, text, why in found:
        print("  %s:%s  %s(...)  [%s]\n      %s" % (rel, span, sink, why, text[:170]))
    print("\n处置要求：改为写入规范值（迁移前的原始文本或稳定 key），只在渲染时本地化；"
          "若该值参与业务判等（如 bizKey 的 description 比较），一律不本地化。")
    return 1


if __name__ == "__main__":
    sys.exit(main())
