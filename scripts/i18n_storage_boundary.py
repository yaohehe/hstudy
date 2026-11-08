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
COPY_SOURCE = (
    r"\bstrings\.[A-Za-z]\w*"
    r"|\bAppStrings(?:Zh|En)\.\w+"
    r"|(?:[A-Za-z]\w*Strings(?:Zh|En))\.\w+"
)
COPY_REF = re.compile(COPY_SOURCE)

CALL = re.compile(r"\b(" + "|".join(re.escape(s) for s in SINKS) + r")\s*\(([^()]*(?:\([^()]*\)[^()]*)*)\)")

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
            tainted.add(match.group(1))
    return tainted


def scan(path):
    with io.open(path, encoding="utf-8") as handle:
        raw = handle.read()
    tainted = collect_taint(raw)
    hits = []
    for lineno, line in enumerate(raw.splitlines(), 1):
        code = line.split("//", 1)[0]
        for match in CALL.finditer(code):
            args = match.group(2)
            if COPY_REF.search(args):
                hits.append((lineno, match.group(1), code.strip(), "直接引用文案表"))
            else:
                leaked = [name for name in tainted if re.search(r"\b%s\b" % re.escape(name), args)]
                if leaked:
                    hits.append((lineno, match.group(1), code.strip(), "经由变量 %s 传入" % ", ".join(sorted(leaked))))
    return hits


def main():
    found = []
    for dirpath, _names, filenames in os.walk(SRC):
        for name in filenames:
            if not name.endswith(".kt"):
                continue
            full = os.path.join(dirpath, name)
            for lineno, sink, text, why in scan(full):
                found.append((os.path.relpath(full, SRC), lineno, sink, text, why))
    if not found:
        print("存储边界守卫通过：没有文案表字符串（含经由变量传入）出现在写库/业务键调用参数里")
        return 0
    print("发现 %d 处疑似「界面文案进入存储或业务键」：" % len(found))
    for rel, lineno, sink, text, why in found:
        print("  %s:%d  %s(...)  [%s]\n      %s" % (rel, lineno, sink, why, text[:170]))
    print("\n处置要求：改为写入规范值（迁移前的原始文本或稳定 key），只在渲染时本地化；"
          "若该值参与业务判等（如 bizKey 的 description 比较），一律不本地化。")
    return 1


if __name__ == "__main__":
    sys.exit(main())
