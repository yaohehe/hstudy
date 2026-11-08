"""存储边界闸门（scripts/i18n_storage_boundary.py）的自我测试。

为什么要有这个文件：Ruling 25 的「经由变量传入」半侧闸门在所有已迁移页面上失效了很久，
根因之一就是**闸门本身没有可复现的红灯样本** —— 手工跑、无 CI，脚本改了正则也没人能让它
"必然失败"。这里给每条检测通道钉一个 fixture：通道坏掉 → 本自测红 → 闸门不许自称绿。

用法（一条命令，仓库根目录或任意目录都可）：
    python scripts/tests/i18n_storage_boundary_selftest.py
退出码：0 = 全部通过；1 = 有断言失败（逐条打印期望/实际）。

覆盖清单（EXPECTED_CASES，缺一即失败；fixture 目录里出现未登记的 .kt 也算失败）：
  必红：直接引用 / 跨行调用（含嵌套 lambda）/ 委托污点 / 赋值污点 / 污点链(长度 2 与 4) /
        sink 名单逐词根逐名字 / 别名句柄（taskListStrings・currentStrings・AppStringsZh・
        LocalStrings.current.<组>.<字段>）/ 字符串里带括号仍要抓
  必绿：字符串字面量里的 sink 名（缺陷 6 的反向）/ 今天真实存在的合规形态
        （Canonical*Text、app 作 lambda 参数、取组句柄、用户输入进 sink、buildList 的 add）
  退出码：真实树 exit 0 且扫描文件数 > 100；空目录 exit 2；不存在的目录 exit 2
  正则级断言：Canonical 豁免、句柄两级不算文案/三级算、TAINT_ASSIGN 不跨行、污点链迭代轮数
"""
import io
import os
import re
import shutil
import subprocess
import sys
import tempfile

# 自测会 import 闸门脚本做正则级断言；别让它在仓库里留下 __pycache__（.gitignore 没覆盖）
sys.dont_write_bytecode = True

HERE = os.path.dirname(os.path.abspath(__file__))
REPO_ROOT = os.path.dirname(os.path.dirname(HERE))
GATE = os.path.join(REPO_ROOT, "scripts", "i18n_storage_boundary.py")
FIXTURES = os.path.join(HERE, "fixtures", "storage_boundary")
REAL_SRC = os.path.join(REPO_ROOT, "composeApp", "src", "commonMain", "kotlin", "com", "hstudy", "app")

DIRECT = "直接引用文案表"
VIA = "经由变量"

# 每条：(fixture 文件名, 期望退出码, 期望命中数, 期望报出的 sink 名集合, 期望在输出里出现的片段)
EXPECTED_CASES = [
    ("DirectReference.kt", 1, 3,
     {"adjustPoints", "submitApproval", "checkInForTask"},
     [DIRECT]),
    ("CrossLineNestedLambda.kt", 1, 2,
     {"checkIn", "addPlan"},
     [DIRECT]),
    ("DelegateTaint.kt", 1, 3,
     {"adjustPoints", "recordFocusSession", "submitApproval"},
     [VIA + " storedNote", VIA + " savedTitle", VIA + " keyedNote"]),
    ("AssignTaint.kt", 1, 5,
     {"checkIn", "spendPoints", "submitApproval", "updateTask"},
     [VIA + " storedDesc", VIA + " firstNote", VIA + " secondNote",
      VIA + " typedNote", VIA + " copied"]),
    ("ChainTaint.kt", 1, 3,
     {"checkIn", "submitApproval", "adjustPoints"},
     [VIA + " secondHop", VIA + " hop4", VIA + " wrapped"]),
    ("SinkCoverage.kt", 1, 31,
     {"insertTask", "insertSession", "insertPointRecord", "insertPlan", "updateTask",
      "updatePlan", "updateStatus", "updateReward", "upsertHabit", "deleteByTitle",
      "softDeleteReward", "markCompleted", "recordFocusSession", "submitApproval",
      "adjustPoints", "spendPoints", "exchangeReward", "assignTask", "undoCheckIn",
      "reviewMistake", "checkIn", "checkInForTask", "checkInChallenge",
      "tryUnlockAchievement", "addPlan", "addReview", "addSavedPoints",
      "setDeviceName", "setPairedDevice", "saveDailyReflection", "saveVariation"},
     [DIRECT]),
    ("AliasedHandle.kt", 1, 5,
     {"adjustPoints", "submitApproval", "checkInForTask", "recordFocusSession"},
     [DIRECT, VIA + " aliasNote", "taskListStrings.pointsEarnedLabel",
      "currentStrings.milestoneBonusNote", "AppStringsZh.weeklyChallengeDescription",
      "LocalStrings.current.pomodoro.phaseBreak"]),
    ("ParenInStringStillCaught.kt", 1, 3,
     {"adjustPoints", "submitApproval", "checkIn"},
     [DIRECT]),
    # 必须报绿的两条
    ("StringLiteralNoFp.kt", 0, 0, set(), []),
    ("CanonicalCompliant.kt", 0, 0, set(), []),
]

HIT_LINE = re.compile(r"^\s+\S+\.kt:(\S+)\s+(\S+)\(\.\.\.\)\s+\[(.+?)\]$")
SCANNED = re.compile(r"已扫描 (\d+) 个")

failures = []
checks = 0


def check(label, ok, detail=""):
    global checks
    checks += 1
    print("  [%s] %s%s" % ("OK  " if ok else "FAIL", label, "" if ok else "  <<< " + detail))
    if not ok:
        failures.append(label)
    return ok


def run_gate(src_dir=None, expect_cwd=None):
    """以真实 CLI 方式跑一次闸门，返回 (exitcode, stdout)。"""
    env = dict(os.environ)
    if src_dir is None:
        env.pop("I18N_BOUNDARY_SRC", None)
    else:
        env["I18N_BOUNDARY_SRC"] = src_dir
    env["PYTHONIOENCODING"] = "utf-8"
    proc = subprocess.run([sys.executable, GATE], cwd=expect_cwd or REPO_ROOT, env=env,
                          stdout=subprocess.PIPE, stderr=subprocess.STDOUT)
    return proc.returncode, proc.stdout.decode("utf-8", "replace")


def parse_hits(out):
    """返回 [(span, sink, why)]。"""
    hits = []
    for line in out.splitlines():
        match = HIT_LINE.match(line)
        if match:
            hits.append((match.group(1), match.group(2), match.group(3)))
    return hits


def run_fixture(name):
    """把单个 fixture 拷进临时目录单独扫，保证退出码就是这一条通道的退出码。"""
    src = os.path.join(FIXTURES, name)
    with tempfile.TemporaryDirectory(prefix="i18n-boundary-") as tmp:
        shutil.copyfile(src, os.path.join(tmp, name))
        code, out = run_gate(src_dir=tmp)
        # 把临时目录绝对路径换成相对输出，免得断言里嵌着 mkdtemp 的随机串
        out = out.replace(tmp, FIXTURES)
    return code, out


def main():
    if hasattr(sys.stdout, "reconfigure"):
        sys.stdout.reconfigure(encoding="utf-8", errors="replace")

    print("== 0) 预检：fixture 清单 ==")
    if not os.path.isdir(FIXTURES):
        print("  [FAIL] fixture 目录不存在：%s" % FIXTURES)
        return 1
    present = sorted(n for n in os.listdir(FIXTURES) if n.endswith(".kt"))
    expected_files = sorted(name for name, *_ in EXPECTED_CASES)
    missing = [n for n in expected_files if n not in present]
    unregistered = [n for n in present if n not in expected_files]
    check("10 个通道 fixture 全部在库", not missing, "缺 %s" % missing)
    check("fixture 目录没有未登记的 .kt（新增通道必须同时登记用例）", not unregistered,
          "未登记 %s" % unregistered)
    for name in present:
        size = os.path.getsize(os.path.join(FIXTURES, name))
        check("fixture %s 非空" % name, size > 200, "只有 %d 字节" % size)
    check("闸门脚本存在", os.path.isfile(GATE), GATE)
    check("真实扫描根存在（缺陷 5 的病根：相对路径 + os.walk 静默空过）",
          os.path.isdir(REAL_SRC), REAL_SRC)

    print("\n== 1) 每条检测通道一个退出码断言 ==")
    for name, want_code, want_hits, want_sinks, want_snippets in EXPECTED_CASES:
        path = os.path.join(FIXTURES, name)
        if not os.path.isfile(path):
            check("%s 存在" % name, False, "fixture 文件缺失，无法证明该通道")
            continue
        code, out = run_fixture(name)
        hits = parse_hits(out)
        if want_code == 0:
            check("%s 退出码 0（合规必须绿）" % name, code == 0,
                  "实际 %d；输出头：%s" % (code, out.splitlines()[0] if out else ""))
        else:
            check("%s 退出码 1（通道必须红）" % name, code == 1,
                  "实际 %d" % code)
        got_sinks = set(s for _sp, s, _w in hits)
        if want_code == 1:
            check("%s 命中数 %d" % (name, want_hits), len(hits) == want_hits,
                  "实际 %d：%s" % (len(hits), hits))
            check("%s 报出的 sink 名集合" % name, got_sinks == want_sinks,
                  "缺 %s / 多 %s" % (sorted(want_sinks - got_sinks), sorted(got_sinks - want_sinks)))
        else:
            check("%s 命中数 0（不许误报）" % name, len(hits) == 0, "实际 %d：%s" % (len(hits), hits))
        for snippet in want_snippets:
            check("%s 输出含「%s」" % (name, snippet), snippet in out, "输出：%s" % out[:400])

    print("\n== 2) 跨行调用确实跨行（Ruling 28 的通道） ==")
    _code, out = run_fixture("CrossLineNestedLambda.kt")
    spans = [sp for sp, _s, _w in parse_hits(out)]
    check("跨行样本的每一条都报出行号区间而非单行", bool(spans) and all("-" in sp for sp in spans),
          "实际 span：%s" % spans)

    print("\n== 3) sink 名单与 fixture 一一对账（防「凭想象加 sink」复发） ==")
    sys.path.insert(0, os.path.dirname(GATE))
    import i18n_storage_boundary as gate
    _code, out = run_fixture("SinkCoverage.kt")
    reported = [s for _sp, s, _w in parse_hits(out)]
    for stem in gate.SINK_STEMS:
        check("词根 %s 至少被 fixture 报出一次" % stem,
              any(s.startswith(stem) for s in reported),
              "fixture 报出的名字：%s" % reported)
    for extra in gate.SINK_EXTRA:
        check("精确名 %s 被 fixture 报出" % extra, extra in reported, "实际：%s" % reported)
    check("fixture 里没有名单外的 sink 名（否则说明有别名走漏）",
          all(any(s.startswith(s2) for s2 in gate.SINK_STEMS) or s in gate.SINK_EXTRA
              for s in reported),
          "未覆盖：%s" % [s for s in reported
                          if not (any(s.startswith(s2) for s2 in gate.SINK_STEMS)
                                  or s in gate.SINK_EXTRA)])

    print("\n== 4) 空过与路径缺陷（缺陷 5） ==")
    with tempfile.TemporaryDirectory(prefix="i18n-boundary-empty-") as empty:
        code, out = run_gate(src_dir=empty)
    check("空目录（0 个文件）退出码非 0", code != 0, "实际 %d / 输出：%s" % (code, out))
    check("空目录不得打印「通过」", "通过" not in out, out)
    code, _out = run_gate(src_dir=os.path.join(tempfile.gettempdir(), "i18n-boundary-不存在-目录"))
    check("不存在的扫描根退出码非 0", code != 0, "实际 %d" % code)
    code, out = run_gate(src_dir=None, expect_cwd=os.path.join(REPO_ROOT, "scripts"))
    from_cwd = SCANNED.search(out)
    check("从 scripts/ 目录跑也必须扫到真文件（旧版在此空过 exit 0）",
          code == 0 and bool(from_cwd) and int(from_cwd.group(1)) > 100,
          "实际 %d / 输出：%s" % (code, out[:300]))

    print("\n== 5) 真实工作树：必须 0 命中 ==")
    code, out = run_gate(src_dir=None, expect_cwd=REPO_ROOT)
    check("真实树退出码 0", code == 0, "实际 %d / 输出：%s" % (code, out[:800]))
    match = SCANNED.search(out)
    count = int(match.group(1)) if match else 0
    check("真实树扫描文件数 > 100（证明不是空过）", count > 100, "实际 %d" % count)
    print("       真实树扫描文件数：%d" % count)

    print("\n== 6) 正则级断言（不需要子进程的机理证明） ==")
    canon = "CanonicalChildHomeText.weeklyChallengeReward(days) CanonicalFocusText.recordDescription(t, m)"
    check("Canonical*Text.* 不算文案（Ruling 25 处置出口，豁免必须保住）",
          gate.COPY_REF.search(canon) is None, "被匹配成了文案")
    check("别名句柄 taskListStrings.pointsEarnedLabel 算文案（缺陷 3）",
          gate.COPY_REF.search("taskListStrings.pointsEarnedLabel(3)") is not None, "没匹配上")
    check("rememberUpdatedState 别名 currentStrings.phaseFocus 算文案（缺陷 3）",
          gate.COPY_REF.search("currentStrings.phaseFocus") is not None, "没匹配上")
    check("取组句柄两级 LocalStrings.current.taskList 不算文案",
          gate.COPY_REF.search("LocalStrings.current.taskList") is None, "被误判成文案")
    check("三级 LocalStrings.current.pomodoro.phaseBreak 算文案",
          gate.COPY_REF.search("LocalStrings.current.pomodoro.phaseBreak") is not None, "没匹配上")

    no_dotall = "val app = AppGraph\nval other = 3\nval leaked = strings.note\n"
    names = sorted(m.group(1) for m in gate.TAINT_ASSIGN.finditer(no_dotall))
    check("TAINT_ASSIGN 不再跨行吞整段（缺陷 1：旧实现在此处只产出 ['app']）",
          names == ["leaked"], "实际 %s" % names)
    two_assigns = "val firstNote = strings.a\nval widthModifier = Modifier.size(12.dp)\nval secondNote = strings.b\n"
    names = sorted(m.group(1) for m in gate.TAINT_ASSIGN.finditer(two_assigns))
    check("同一文件多个赋值污点都要收到（旧实现每文件只给 1 个位置性名字）",
          names == ["firstNote", "secondNote"], "实际 %s" % names)
    typed = "val typedNote: () -> String = { strings.tip }\n"
    names = [m.group(1) for m in gate.TAINT_ASSIGN.finditer(typed)]
    check("带类型注解的函数型文案字段被认成污点（旧实现被 `: ` 挡掉）",
          names == ["typedNote"], "实际 %s" % names)
    inline_copy = "val n = task.copy(note = strings.x)\n"
    names = [m.group(1) for m in gate.TAINT_ASSIGN.finditer(inline_copy)]
    check("具名实参里内联的文案（.copy(note = strings.x)）被认成污点",
          names == ["n"], "实际 %s" % names)

    chain = "val a = strings.x\nval b = a\nval c = b\n"
    tainted, rounds = gate.collect_taint(chain)
    check("污点链迭代到不动点（缺陷 2：旧实现只有深度 1，b/c 都看不见）",
          tainted == {"a", "b", "c"} and rounds >= 2,
          "实际 %s / 轮数 %d" % (sorted(tainted), rounds))
    reverse = "val tailNote = midNote\nval midNote = headNote\nval headNote = strings.x\n"
    tainted_r, rounds_r = gate.collect_taint(reverse)
    check("反序书写（先使用后赋值）的多跳链也要收敛",
          tainted_r == {"tailNote", "midNote", "headNote"} and rounds_r >= 3,
          "实际 %s / 轮数 %d" % (sorted(tainted_r), rounds_r))
    depth1 = "val a = strings.x\n"
    check("没有第二跳时不要把无关变量拖进污点集（链通道是纯增量）",
          gate.collect_taint(depth1)[0] == {"a"}, "实际 %s" % sorted(gate.collect_taint(depth1)[0]))

    handle_text = io.open(os.path.join(FIXTURES, "CanonicalCompliant.kt"),
                          encoding="utf-8").read()
    tainted, _r = gate.collect_taint(gate.blank_string_contents(gate.strip_comments(handle_text)))
    check("合规页里的 app / strings / widthModifier 不再被误判成污点变量",
          not ({"app", "strings", "widthModifier"} & tainted), "实际污点集 %s" % sorted(tainted))

    # 缺陷 6 的机理证明：空白化前后 CALL_HEAD 的匹配数必须下降（文案串里的假 sink 头被去掉）。
    # 注意这只证明"假匹配消失"，不代表真误报过——extract_args 本身就跳过字符串，
    # 所以串里的 sink 名一般到不了"报红"那一步（9b 实测真实树 0 命中）。这条断言存在的意义
    # 是让 blank_string_contents 这一步**不可被悄悄删掉**（删掉后自测变红）。
    literal_text = io.open(os.path.join(FIXTURES, "StringLiteralNoFp.kt"), encoding="utf-8").read()
    stripped_literal = gate.strip_comments(literal_text)
    heads_raw = [m.group(1) for m in gate.CALL_HEAD.finditer(stripped_literal)]
    heads_blank = [m.group(1) for m in
                   gate.CALL_HEAD.finditer(gate.blank_string_contents(stripped_literal))]
    check("字符串空白化消灭了文案串里的假 sink 头（缺陷 6）",
          len(heads_blank) < len(heads_raw),
          "空白化前 %d 个 %s / 之后 %d 个 %s" % (len(heads_raw), heads_raw, len(heads_blank), heads_blank))
    check("被去掉的假头确实是串里的 save/update/checkIn/insert",
          {"save", "update", "checkIn", "insert"} <= set(heads_raw) - set(heads_blank),
          "差集 %s" % sorted(set(heads_raw) - set(heads_blank)))
    # 缺陷 6 里**真正会误报**的一类：文案串里写的赋值示例进不了污点集（空白化），
    # 但同一份文本不空白化时确实会进去 —— 这条对比就是 blank_string_contents 的存在理由。
    phantom_tainted_blank, _b = gate.collect_taint(gate.blank_string_contents(stripped_literal))
    phantom_tainted_raw, _r2 = gate.collect_taint(stripped_literal)
    check("串里的 `val demoNote = strings.demoTip` 示例不产生污点（空白化后）",
          "demoNote" not in phantom_tainted_blank, "实际污点集 %s" % sorted(phantom_tainted_blank))
    check("同一份文本不空白化的时候**确实**会被污染（证明这一步不是摆设）",
          "demoNote" in phantom_tainted_raw, "实际污点集 %s" % sorted(phantom_tainted_raw))

    print("\n== 7) 旧缺陷的实测复现（信息，不作断言） ==")
    for rel in ["ui" + os.sep + "child" + os.sep + "ChildHomeScreen.kt",
                "ui" + os.sep + "child" + os.sep + "ChildTaskListScreen.kt",
                "ui" + os.sep + "common" + os.sep + "CommonComponents.kt"]:
        full = os.path.join(REAL_SRC, rel)
        if not os.path.isfile(full):
            print("       （%s 不在库，跳过）" % rel)
            continue
        text = gate.blank_string_contents(gate.strip_comments(io.open(full, encoding="utf-8").read()))
        assign_names = sorted(m.group(1) for m in gate.TAINT_ASSIGN.finditer(text))
        delegate_names = sorted(m.group(1) for m in gate.TAINT_DELEGATE.finditer(text))
        print("       %-42s 赋值污点=%s 委托污点=%s" % (rel, assign_names, delegate_names))
    print("       （缺陷 1 的修复前实测：同一批文件 TAINT_ASSIGN 各只给 1 个名字，"
          "且依次是 ['app'] / ['strings'] / ['widthModifier']）")

    print("\n== 汇总 ==")
    print("  断言 %d 条，失败 %d 条" % (checks, len(failures)))
    if failures:
        for f in failures:
            print("  FAIL: %s" % f)
        print("存储边界闸门自测**未通过** —— 闸门不可信时不得继续派发 Task 10/11（Ruling 29）。")
        return 1
    print("存储边界闸门自测通过：8 条必红通道 + 2 条必绿通道 + 空过防护 + 正则级机理断言全部符合预期。")
    return 0


if __name__ == "__main__":
    sys.exit(main())
