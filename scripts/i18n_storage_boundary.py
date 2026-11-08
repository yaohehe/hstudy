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
]

# 文案来源的三种写法：局部变量 strings.X、表字段 XyzStrings.y / AppStringsZh.x、直接调 lambda
COPY_REF = re.compile(r"\bstrings\.[A-Za-z]\w*|\bAppStrings(?:Zh|En)\.\w+|\w+Strings(?:Zh|En)\.\w+")

CALL = re.compile(r"\b(" + "|".join(re.escape(s) for s in SINKS) + r")\s*\(([^()]*(?:\([^()]*\)[^()]*)*)\)")


def scan(path):
    hits = []
    with io.open(path, encoding="utf-8") as handle:
        for lineno, line in enumerate(handle, 1):
            code = line.split("//", 1)[0]
            for match in CALL.finditer(code):
                args = match.group(2)
                if COPY_REF.search(args):
                    hits.append((lineno, match.group(1), code.strip()))
    return hits


def main():
    found = []
    for dirpath, _names, filenames in os.walk(SRC):
        for name in filenames:
            if not name.endswith(".kt"):
                continue
            full = os.path.join(dirpath, name)
            for lineno, sink, text in scan(full):
                found.append((os.path.relpath(full, SRC), lineno, sink, text))
    if not found:
        print("存储边界守卫通过：没有文案表字符串出现在写库/业务键调用参数里")
        return 0
    print("发现 %d 处疑似「界面文案进入存储或业务键」：" % len(found))
    for rel, lineno, sink, text in found:
        print("  %s:%d  %s(...)  ←  %s" % (rel, lineno, sink, text[:160]))
    print("\n处置要求：改为写入规范值（迁移前的原始文本或稳定 key），只在渲染时本地化；"
          "若该值确实需要参与业务判等，禁止本地化。")
    return 1


if __name__ == "__main__":
    sys.exit(main())
