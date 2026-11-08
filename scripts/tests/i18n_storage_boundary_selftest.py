"""存储边界闸门（scripts/i18n_storage_boundary.py）的自我测试。

为什么要有这个文件：Ruling 25 的「经由变量传入」半侧闸门在所有已迁移页面上失效了很久，
根因之一就是**闸门本身没有可复现的红灯样本** —— 手工跑、无 CI，脚本改了正则也没人能让它
"必然失败"。这里给每条检测通道钉一个 fixture：通道坏掉 → 本自测红 → 闸门不许自称绿。

用法（一条命令，仓库根目录或任意目录都可）：
    python scripts/tests/i18n_storage_boundary_selftest.py
退出码：0 = 全部通过；1 = 有断言失败（逐条打印期望/实际）。

覆盖清单（EXPECTED_CASES，缺一即失败；fixture 目录里出现未登记的 .kt 也算失败）：
  必红（11）：直接引用 / 跨行调用（含嵌套 lambda）/ 委托污点 / 赋值污点 / 污点链(长度 2 与 4) /
        sink 名单逐词根逐名字 / 别名句柄（taskListStrings・currentStrings・AppStringsZh・
        LocalStrings.current.<组>.<字段>）/ 字符串里带括号仍要抓 /
        **插值文案 `${…}`・`$name`（9b 审查 Critical 1）** /
        **add·set 大写后缀写库家族（9b 审查 Critical 2）** /
        **形状句柄 `X.current.<组>` 绑出来的任意名（9b 审查 Important 3）**
  必绿（4）：字符串字面量里的 sink 名（缺陷 6 的反向）/ 今天真实存在的合规形态
        （Canonical*Text、app 作 lambda 参数、取组句柄、用户输入进 sink、buildList 的 add）/
        **插值保留的副作用（InterpolationNoFp）** /
        **注释里的 sink 调用（CommentedSinkNoFp，strip_comments 的专职样本）**
  退出码：真实树 exit 0 且扫描文件数 > 100；空目录 exit 2；不存在的目录 exit 2
  行号断言：每条必红通道的 [(行号区间, sink 名)] 有序列表逐条比对（9b 审查 Minor：
        原先只有"span 里有没有 '-'"这种格式检查，行号漂移没人看守）
  sink 双向对账：词根/精确名 ↔ fixture 调用点，**并且** ↔ commonMain 里的真实 `fun` 声明；
        RESERVED_STEMS 走反向规则（当前必须无真实声明，一有就要搬进 SINK_STEMS）
  正则级断言：Canonical 豁免、句柄两级不算文案/三级算、形状句柄登记、插值保留与嵌套串空白化、
        TAINT_ASSIGN 的换行上限、污点链迭代轮数、strip_comments 与 blank_string_contents 的对比
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

# main() 里 import 进来给 code_of()/declared_functions() 用（延迟 import：闸门脚本自己坏掉时，
# 自测要先报"预检失败"而不是连 import 都崩在半路）
gate = None

# 每条：(fixture 文件名, 期望退出码, 期望命中数, 期望报出的 sink 名集合, 期望在输出里出现的片段,
#        期望的 [(行号区间, sink 名)] 有序列表)
EXPECTED_CASES = [
    ("DirectReference.kt", 1, 3,
     {"adjustPoints", "submitApproval", "checkInForTask"},
     [DIRECT],
     [("15", "adjustPoints"), ("17-21", "submitApproval"), ("23", "checkInForTask")]),
    ("CrossLineNestedLambda.kt", 1, 2,
     {"checkIn", "addPlan"},
     [DIRECT],
     [("15-24", "checkIn"), ("25-30", "addPlan")]),
    ("DelegateTaint.kt", 1, 3,
     {"adjustPoints", "recordFocusSession", "submitApproval"},
     [VIA + " storedNote", VIA + " savedTitle", VIA + " keyedNote"],
     [("19", "adjustPoints"), ("22", "recordFocusSession"), ("25", "submitApproval")]),
    ("AssignTaint.kt", 1, 5,
     {"checkIn", "spendPoints", "submitApproval", "updateTask"},
     [VIA + " storedDesc", VIA + " firstNote", VIA + " secondNote",
      VIA + " typedNote", VIA + " copied"],
     [("20", "checkIn"), ("25", "spendPoints"), ("26", "spendPoints"),
      ("30", "submitApproval"), ("34", "updateTask")]),
    ("ChainTaint.kt", 1, 3,
     {"checkIn", "submitApproval", "adjustPoints"},
     [VIA + " secondHop", VIA + " hop4", VIA + " wrapped"],
     [("19", "checkIn"), ("26", "submitApproval"), ("30", "adjustPoints")]),
    ("SinkCoverage.kt", 1, 31,
     {"insertTask", "insertSession", "insertPointRecord", "insertPlan", "updateTask",
      "updatePlan", "updateStatus", "updateReward", "upsertHabit", "deleteMistake",
      "softDeleteReward", "markCompleted", "recordFocusSession", "submitApproval",
      "adjustPoints", "spendPoints", "exchangeReward", "assignTask", "undoCheckIn",
      "reviewMistake", "checkIn", "checkInForTask", "checkInChallenge",
      "tryUnlockAchievement", "addPlan", "addReview", "addSavedPoints",
      "setDeviceName", "setPairedDevice", "saveDailyReflection", "saveVariation"},
     [DIRECT],
     [("27", "insertTask"), ("28", "insertSession"), ("29", "insertPointRecord"),
      ("30", "insertPlan"), ("31", "updateTask"), ("32", "updatePlan"),
      ("33", "updateStatus"), ("34", "updateReward"), ("35", "upsertHabit"),
      ("36", "deleteMistake"), ("37", "softDeleteReward"), ("38", "markCompleted"),
      ("39", "recordFocusSession"), ("40", "submitApproval"), ("41", "adjustPoints"),
      ("42", "spendPoints"), ("43", "exchangeReward"), ("44", "assignTask"),
      ("45", "undoCheckIn"), ("46", "reviewMistake"), ("47", "checkIn"),
      ("48", "checkInForTask"), ("49", "checkInChallenge"),
      ("50", "tryUnlockAchievement"), ("53", "addPlan"), ("54", "addReview"),
      ("55", "addSavedPoints"), ("56", "setDeviceName"), ("57", "setPairedDevice"),
      ("60", "saveDailyReflection"), ("61", "saveVariation")]),
    ("AliasedHandle.kt", 1, 5,
     {"adjustPoints", "submitApproval", "checkInForTask", "recordFocusSession"},
     [DIRECT, VIA + " aliasNote", "taskListStrings.pointsEarnedLabel",
      "currentStrings.milestoneBonusNote", "AppStringsZh.weeklyChallengeDescription",
      "LocalStrings.current.pomodoro.phaseBreak"],
     [("21", "adjustPoints"), ("23", "submitApproval"), ("25", "checkInForTask"),
      ("27", "recordFocusSession"), ("30", "submitApproval")]),
    ("ParenInStringStillCaught.kt", 1, 3,
     {"adjustPoints", "submitApproval", "checkIn"},
     [DIRECT],
     [("19", "adjustPoints"), ("21-25", "submitApproval"), ("30", "checkIn")]),
    # ---- 9b 审查 round 1 新增的三条必红通道 ----
    ("InterpolationCopy.kt", 1, 5,
     {"adjustPoints", "submitApproval", "spendPoints", "recordFocusSession"},
     [VIA + " composed", VIA + " outerNote", VIA + " multilineNote", VIA + " rawNote",
      DIRECT, '"${strings.milestoneRewardNote} ok"'],
     [("20", "adjustPoints"), ("23", "submitApproval"), ("28", "spendPoints"),
      ("33", "recordFocusSession"), ("40", "submitApproval")]),
    ("AddSetFamily.kt", 1, 9,
     {"addTask", "addMistake", "addGoal", "addReward", "addChallenge", "addSchedule",
      "setDailyTargets", "setMastered"},
     [VIA + " taskTitle", DIRECT],
     [("20", "addTask"), ("21", "addMistake"), ("22", "addGoal"), ("23", "addReward"),
      ("24", "addChallenge"), ("25", "addSchedule"), ("28", "setDailyTargets"),
      ("29", "setMastered"), ("33", "addTask")]),
    ("GroupHandleCopy.kt", 1, 5,
     {"adjustPoints", "submitApproval", "recordFocusSession", "spendPoints", "updateTask"},
     [DIRECT, VIA + " note", "common.deleteContentDescription",
      "plan.focusTargetLabel", "alias.petFeedTitle"],
     [("24", "adjustPoints"), ("28", "submitApproval"), ("31", "recordFocusSession"),
      ("34", "spendPoints"), ("37", "updateTask")]),
    # ---- 必须报绿的四条 ----
    ("StringLiteralNoFp.kt", 0, 0, set(), [], []),
    ("CanonicalCompliant.kt", 0, 0, set(), [], []),
    ("InterpolationNoFp.kt", 0, 0, set(), [], []),
    ("CommentedSinkNoFp.kt", 0, 0, set(), [], []),
]

HIT_LINE = re.compile(r"^\s+\S+\.kt:(\S+)\s+(\S+)\(\.\.\.\)\s+\[(.+?)\]$")
SCANNED = re.compile(r"已扫描 (\d+) 个")
FUN_DECL = re.compile(r"\bfun\s+([A-Za-z_]\w*)\s*\(")

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


def fixture_text(name):
    return io.open(os.path.join(FIXTURES, name), encoding="utf-8").read()


def code_of(name):
    """闸门真正吃进去的那份文本：剥注释 + 空白化（插值区保留）。"""
    return gate.blank_string_contents(gate.strip_comments(fixture_text(name)))


def declared_functions():
    """commonMain 里真实声明的 `fun <名字>(` 集合（注释/字符串内容先剥掉，防"注释里的声明"）。"""
    names = set()
    scanned = 0
    for dirpath, _names, filenames in os.walk(REAL_SRC):
        for fname in sorted(filenames):
            if not fname.endswith(".kt"):
                continue
            scanned += 1
            body = gate.blank_string_contents(gate.strip_comments(
                io.open(os.path.join(dirpath, fname), encoding="utf-8").read()))
            names.update(FUN_DECL.findall(body))
    return names, scanned


def main():
    global gate
    if hasattr(sys.stdout, "reconfigure"):
        sys.stdout.reconfigure(encoding="utf-8", errors="replace")

    sys.path.insert(0, os.path.dirname(GATE))
    import i18n_storage_boundary as gate_module
    gate = gate_module

    print("== 0) 预检：fixture 清单 ==")
    if not os.path.isdir(FIXTURES):
        print("  [FAIL] fixture 目录不存在：%s" % FIXTURES)
        return 1
    present = sorted(n for n in os.listdir(FIXTURES) if n.endswith(".kt"))
    expected_files = sorted(name for name, *_ in EXPECTED_CASES)
    missing = [n for n in expected_files if n not in present]
    unregistered = [n for n in present if n not in expected_files]
    check("%d 个通道 fixture 全部在库" % len(expected_files), not missing, "缺 %s" % missing)
    check("fixture 目录没有未登记的 .kt（新增通道必须同时登记用例）", not unregistered,
          "未登记 %s" % unregistered)
    for name in present:
        size = os.path.getsize(os.path.join(FIXTURES, name))
        check("fixture %s 非空" % name, size > 200, "只有 %d 字节" % size)
    check("闸门脚本存在", os.path.isfile(GATE), GATE)
    check("真实扫描根存在（缺陷 5 的病根：相对路径 + os.walk 静默空过）",
          os.path.isdir(REAL_SRC), REAL_SRC)

    print("\n== 1) 每条检测通道一个退出码断言 ==")
    for name, want_code, want_hits, want_sinks, want_snippets, want_lines in EXPECTED_CASES:
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
            check("%s 退出码 1（通道必须红）" % name, code == 1, "实际 %d" % code)
        got_sinks = set(s for _sp, s, _w in hits)
        if want_code == 1:
            check("%s 命中数 %d" % (name, want_hits), len(hits) == want_hits,
                  "实际 %d：%s" % (len(hits), hits))
            check("%s 报出的 sink 名集合" % name, got_sinks == want_sinks,
                  "缺 %s / 多 %s" % (sorted(want_sinks - got_sinks), sorted(got_sinks - want_sinks)))
            got_lines = [(sp, s) for sp, s, _w in hits]
            check("%s 的行号区间与 sink 名逐条相符（钉死到具体行）" % name,
                  got_lines == want_lines, "期望 %s / 实际 %s" % (want_lines, got_lines))
        else:
            check("%s 命中数 0（不许误报）" % name, len(hits) == 0, "实际 %d：%s" % (len(hits), hits))
        for snippet in want_snippets:
            check("%s 输出含「%s」" % (name, snippet), snippet in out, "输出：%s" % out[:400])

    print("\n== 2) 跨行调用确实跨行（Ruling 28 的通道） ==")
    _code, out = run_fixture("CrossLineNestedLambda.kt")
    spans = [sp for sp, _s, _w in parse_hits(out)]
    check("跨行样本的每一条都报出行号区间而非单行", bool(spans) and all("-" in sp for sp in spans),
          "实际 span：%s" % spans)

    print("\n== 3) sink 名单双向对账（9b 审查 Important 4：只有 fixture 不算数） ==")
    reported_by = {}
    for name in ("SinkCoverage.kt", "AddSetFamily.kt"):
        _code, out = run_fixture(name)
        reported_by[name] = [s for _sp, s, _w in parse_hits(out)]
    reported = reported_by["SinkCoverage.kt"]
    reported_all = reported + reported_by["AddSetFamily.kt"]
    capsuffix_re = re.compile(r"(?:%s)[A-Z]" % "|".join(gate.SINK_CAPSUFFIX))
    declared, decl_scanned = declared_functions()
    check("真实树声明取样可用（%d 个文件 / %d 个 fun 名）" % (decl_scanned, len(declared)),
          decl_scanned > 100 and len(declared) > 200,
          "文件 %d / 声明 %d" % (decl_scanned, len(declared)))

    def covered(s):
        return (any(s.startswith(stem) for stem in gate.SINK_STEMS + gate.RESERVED_STEMS)
                or s in gate.SINK_EXTRA or bool(capsuffix_re.match(s)))

    for stem in gate.SINK_STEMS:
        check("词根 %s 至少被 fixture 报出一次" % stem,
              any(s.startswith(stem) for s in reported),
              "fixture 报出的名字：%s" % reported)
    for extra in gate.SINK_EXTRA:
        check("精确名 %s 被 fixture 报出" % extra, extra in reported, "实际：%s" % reported)
    check("fixture 里没有名单外的 sink 名（否则说明有别名走漏）",
          all(covered(s) for s in reported_all),
          "未覆盖：%s" % [s for s in reported_all if not covered(s)])

    # —— 反向半侧：名单里的每个词根/名字都要在 commonMain 找得到真实声明 ——
    stem_no_decl = [s for s in gate.SINK_STEMS if not any(n.startswith(s) for n in declared)]
    check("SINK_STEMS 每一条都匹配到 commonMain 的真实声明（0 声明的词根应挪进 RESERVED_STEMS）",
          not stem_no_decl, "查无声明：%s" % stem_no_decl)
    extra_no_decl = [e for e in gate.SINK_EXTRA if e not in declared]
    check("SINK_EXTRA 每个精确名都在 commonMain 里有 `fun <名字>(` 声明",
          not extra_no_decl, "凭想象的名字：%s" % extra_no_decl)
    reserved_with_decl = [s for s in gate.RESERVED_STEMS if any(n.startswith(s) for n in declared)]
    check("RESERVED_STEMS 目前在 commonMain 仍无声明（一旦有了就必须搬进 SINK_STEMS）",
          not reserved_with_decl, "%s 已出现真实声明，请搬进 SINK_STEMS" % reserved_with_decl)
    check("RESERVED_STEMS 仍然被 fixture 覆盖（预留不等于免检）",
          all(any(s.startswith(r) for s in reported_all) for r in gate.RESERVED_STEMS),
          "没有 fixture 的预留词根：%s" % [r for r in gate.RESERVED_STEMS
                                          if not any(s.startswith(r) for s in reported_all)])
    for root in gate.SINK_CAPSUFFIX:
        got = [s for s in reported_all if capsuffix_re.match(s) and s.startswith(root)]
        ghost = [s for s in got if s not in declared]
        check("大写后缀词根 %sXxx 被 fixture 报出且每条都有真实声明（add/set 家族）" % root,
              bool(got) and not ghost, "报出：%s / 查无声明：%s" % (got, ghost))
    for denied in gate.SINK_CAPSUFFIX_DENY:
        check("排除表里的 %s 一条都没报（渲染容器/标准库不是写库路径）" % denied,
              denied not in reported_all, "误报出现在：%s" % reported_all)

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

    # ---- Important 3：句柄登记看**形状**，不看标识符名 ----
    handle_src = ("val common = LocalStrings.current.common\n"
                  "val plan: FocusStrings = LocalStrings.current.focus\n"
                  "val alias = common\n")
    handles = gate.collect_copy_handles(handle_src)
    check("`X.current.<组>` 绑出来的任意名登记成句柄（common/plan/alias 三个都要有）",
          {"common", "plan", "alias"} <= handles, "实际 %s" % sorted(handles))
    check("句柄字段 common.deleteContentDescription 算文案（审查探针 R4 的机理）",
          gate.copy_ref_for(handles).search("common.deleteContentDescription") is not None,
          "没匹配上")
    check("句柄绑定这一行本身不算文案（否则每页的句柄变量都会被污点化 → 9b 前的误报复发）",
          gate.copy_ref_for(handles).search("val common = LocalStrings.current.common") is None,
          "被误判成文案")
    check("反向对照：不登记句柄时 common.deleteContentDescription 不算文案"
          "（证明第 5 支是新通道，不是既有规则的副产物）",
          gate.copy_ref_for(set()).search("common.deleteContentDescription") is None,
          "基础四支就已经匹配了")
    check("取组句柄只登记为句柄、不进污点集（Important 3 不误报的一侧）",
          gate.collect_taint(handle_src + "val n = 3\n")[0] == set(),
          "实际污点集 %s" % sorted(gate.collect_taint(handle_src + "val n = 3\n")[0]))
    # 收紧：句柄别名**只在右端整个就是句柄时**传播。否则 ChildHomeScreen.kt:253 那种
    # `val hardestName = when (…) { -> strings.x }` 的中间变量会被登记成"文案来源"，
    # 那是把每个提到 strings 的变量都变成误报温床（Important 3 的修法必须带的刹车）。
    when_src = ("val strings = LocalStrings.current.childHome\n"
                "val hardestName = when (d) { 3 -> strings.levelChallenge; else -> strings.levelNormal }\n")
    handles_when = gate.collect_copy_handles(when_src)
    check("when(...) 这类中间变量不变成句柄（别名传播要求右端整个是句柄）",
          "hardestName" not in handles_when and "strings" in handles_when,
          "实际句柄集 %s" % sorted(handles_when))
    check("但它仍然是**污点**（收紧句柄没有放松污点通道）",
          "hardestName" in gate.collect_taint(when_src)[0],
          "实际污点集 %s" % sorted(gate.collect_taint(when_src)[0]))
    rus_src = ("val common = LocalStrings.current.common\n"
               "val currentCommon by rememberUpdatedState(common)\n")
    check("`val x by rememberUpdatedState(句柄)` 也登记成句柄（PomodoroTimer.kt:49 的形状）",
          "currentCommon" in gate.collect_copy_handles(rus_src),
          "实际句柄集 %s" % sorted(gate.collect_copy_handles(rus_src)))
    home_full = os.path.join(REAL_SRC, "ui", "child", "ChildHomeScreen.kt")
    if os.path.isfile(home_full):
        home_code = gate.blank_string_contents(
            gate.strip_comments(io.open(home_full, encoding="utf-8").read()))
        home_handles = gate.collect_copy_handles(home_code)
        check("真实树取证：ChildHomeScreen.kt 的句柄集是 strings/taskListStrings，"
              "不含 hardestName（收紧在真树上生效，不是只在 fixture 里成立）",
              home_handles == {"strings", "taskListStrings"}, "实际 %s" % sorted(home_handles))

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
    # 9b 审查 Minor：旧注释写"值限定在同一行"不准确 —— `=` 后面的 `\s*` 确实会桥接换行，
    # 但只能穿过**纯空白行**；中间一旦出现有内容的行就断掉
    # （这才是 C 通道的上限，也是"缺陷 1 的 DOTALL 没复发"的可测证明）。
    bridge = "val bridgeNote =\n    strings.tip\n"
    names = [m.group(1) for m in gate.TAINT_ASSIGN.finditer(bridge)]
    check("`=` 之后换行仍算同一条赋值（审查 R6 A 的形态；旧注释的「不跨行」说法不准确）",
          names == ["bridgeNote"], "实际 %s" % names)
    limit = "val noteA = compute(1)\nval noteB = strings.tip\n"
    names = sorted(m.group(1) for m in gate.TAINT_ASSIGN.finditer(limit))
    check("中间行有内容时赋值污点必须断掉（C 通道上限是可测的，不是注释里的承诺）",
          names == ["noteB"], "实际 %s" % names)
    raw_multiline = 'val rawNote = """\n    说明 ${strings.tip}\n""" \n'
    names = [m.group(1) for m in gate.taint_patterns(gate.COPY_SOURCE)[2].finditer(raw_multiline)
             if gate.COPY_REF.search(m.group(2))]
    check("多行三引号原始串里的插值也认（TAINT_ASSIGN_RAW 锚在成对三引号，不是 DOTALL 全放开）",
          names == ["rawNote"], "实际 %s" % names)

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

    tainted_canon, _r = gate.collect_taint(code_of("CanonicalCompliant.kt"))
    check("合规页里的 app / strings / widthModifier 不再被误判成污点变量",
          not ({"app", "strings", "widthModifier"} & tainted_canon),
          "实际污点集 %s" % sorted(tainted_canon))
    check("合规页里的形状句柄 common 只登记、不进污点集（Important 3 不误报的一侧，整页级）",
          "common" not in tainted_canon, "实际污点集 %s" % sorted(tainted_canon))

    # ---- Critical 1：插值保留的机理（不靠子进程，直接钉字符串）----
    blanked_interp = gate.blank_string_contents('val composed = "review: ${strings.checkInDescription}"')
    check("插值区在空白化后仍然可见（`${…}` 里的文案引用没被抹掉 = Critical 1 的修法）",
          "strings.checkInDescription" in blanked_interp, "实际：%s" % blanked_interp)
    check("插值之外的字面量内容照旧被空白化（不能矫枉过正把整串当代码）",
          "review:" not in blanked_interp, "实际：%s" % blanked_interp)
    simple_dollar = gate.blank_string_contents('val outer = "备注 $wrappedNote 结束"')
    check("简单 `$name` 插值同样保留（污点链第二跳 I3 靠它）",
          "$wrappedNote" in simple_dollar, "实际：%s" % simple_dollar)
    nested = 'val demo = "写法示例：${"val demoNote = strings.demoTip 会被写进库里"}"'
    blanked_nested = gate.blank_string_contents(nested)
    check("插值里再套一层字面量时那层内容继续被空白化（递归放行代码，不是整段放行）",
          "strings.demoTip" not in blanked_nested and "demoNote" not in blanked_nested,
          "实际：%s" % blanked_nested)
    check("但插值自己的定界符还在（上面那条不是因为整串被删掉了）",
          "${" in blanked_nested and "}" in blanked_nested, "实际：%s" % blanked_nested)
    check("嵌套串里的赋值示例不产生污点（递归失灵时 InterpolationNoFp.kt 的 N4 就会误报）",
          "demoNote" not in gate.collect_taint(blanked_nested)[0],
          "实际污点集 %s" % sorted(gate.collect_taint(blanked_nested)[0]))

    # 缺陷 6 的机理证明：空白化前后 CALL_HEAD 的匹配数必须下降（文案串里的假 sink 头被去掉）。
    # 注意这只证明"假匹配消失"，不代表真误报过——extract_args 本身就跳过字符串，
    # 所以串里的 sink 名一般到不了"报红"那一步（9b 实测真实树 0 命中）。这条断言存在的意义
    # 是让 blank_string_contents 这一步**不可被悄悄删掉**（删掉后自测变红）。
    stripped_literal = gate.strip_comments(fixture_text("StringLiteralNoFp.kt"))
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
    # 形状句柄（Important 3 的新通道）吃的是同一份空白化输入：串里写的"取组句柄示例"
    # 若漏进代码就会被登记成句柄，StringLiteralNoFp.kt 的 `demoHandle.noteText` 立刻误报。
    handles_blank = gate.collect_copy_handles(gate.blank_string_contents(stripped_literal))
    handles_raw = gate.collect_copy_handles(stripped_literal)
    check("串里的 `val demoHandle = LocalStrings.current.common` 示例不登记成句柄（空白化后）",
          "demoHandle" not in handles_blank, "实际句柄集 %s" % sorted(handles_blank))
    check("同一份文本不空白化时它**确实**被登记成句柄（形状句柄那一支也确实吃空白化后的文本）",
          "demoHandle" in handles_raw, "实际句柄集 %s" % sorted(handles_raw))

    # ---- strip_comments 的专职样本（9b 审查 Minor：以前只是"顺便"被别的 fixture 撞上）----
    commented_raw = fixture_text("CommentedSinkNoFp.kt")
    heads_commented_raw = [m.group(1) for m in gate.CALL_HEAD.finditer(commented_raw)]
    heads_commented_stripped = [m.group(1) for m in
                                gate.CALL_HEAD.finditer(gate.strip_comments(commented_raw))]
    check("原文里**确实**有注释掉的 adjustPoints/addTask/submitApproval 头"
          "（这条 fixture 是在测 strip_comments，不是空转）",
          {"adjustPoints", "addTask", "submitApproval"} <= set(heads_commented_raw),
          "原文里的头：%s" % heads_commented_raw)
    check("剥注释后这些头全部消失（只处理 // 不够，块注释也要处理）",
          not ({"adjustPoints", "addTask", "submitApproval"} & set(heads_commented_stripped)),
          "剥完还剩：%s" % heads_commented_stripped)
    check("不剥注释、只空白化时，注释里的文案赋值照样污染污点集",
          {"commentedNote", "commentedHop"} <= gate.collect_taint(
              gate.blank_string_contents(commented_raw))[0],
          "实际污点集 %s" % sorted(gate.collect_taint(gate.blank_string_contents(commented_raw))[0]))
    tainted_commented = gate.collect_taint(code_of("CommentedSinkNoFp.kt"))[0]
    check("剥完注释再空白化，污点集干净（CommentedSinkNoFp.kt 必须绿的机理）",
          not ({"commentedNote", "commentedHop", "strings", "common"} & tainted_commented),
          "实际污点集 %s" % sorted(tainted_commented))

    print("\n== 7) 旧缺陷的实测复现（信息，不作断言） ==")
    for rel in ["ui" + os.sep + "child" + os.sep + "ChildHomeScreen.kt",
                "ui" + os.sep + "child" + os.sep + "ChildTaskListScreen.kt",
                "ui" + os.sep + "common" + os.sep + "CommonComponents.kt",
                "ui" + os.sep + "focus" + os.sep + "ChildDailyPlanScreen.kt"]:
        full = os.path.join(REAL_SRC, rel)
        if not os.path.isfile(full):
            print("       （%s 不在库，跳过）" % rel)
            continue
        text = gate.blank_string_contents(gate.strip_comments(io.open(full, encoding="utf-8").read()))
        assign_names = sorted(m.group(1) for m in gate.TAINT_ASSIGN.finditer(text))
        delegate_names = sorted(m.group(1) for m in gate.TAINT_DELEGATE.finditer(text))
        handle_names = sorted(gate.collect_copy_handles(text))
        print("       %-42s 赋值污点=%s 委托污点=%s 形状句柄=%s"
              % (rel, assign_names, delegate_names, handle_names))
    print("       （缺陷 1 的修复前实测：同一批文件 TAINT_ASSIGN 各只给 1 个名字，"
          "且依次是 ['app'] / ['strings'] / ['widthModifier']；形状句柄那一列当时整个不存在）")

    print("\n== 汇总 ==")
    print("  断言 %d 条，失败 %d 条" % (checks, len(failures)))
    if failures:
        for f in failures:
            print("  FAIL: %s" % f)
        print("存储边界闸门自测**未通过** —— 闸门不可信时不得继续派发 Task 10/11（Ruling 29）。")
        return 1
    print("存储边界闸门自测通过：11 条必红通道 + 4 条必绿通道 + 行号钉死 + sink 双向对账 + "
          "空过防护 + 正则级机理断言全部符合预期。")
    return 0


if __name__ == "__main__":
    sys.exit(main())
