"""存储边界守卫：禁止界面文案表的字符串进入写库调用或业务键。

背景（Ruling 25）：双语迁移会把 `"中文"` 换成 `strings.<field>`。若该值被写进数据库或参与
业务判等（如 `checkIn(bizKey, description)` 的 description 参与幂等比较），语言切换就会改变
存储字节，导致历史记录按语言分裂、幂等判定被误触发。spec §5 的边界正是为此而设。

用法：
    python scripts/i18n_storage_boundary.py                  # 扫描全部 commonMain
    python scripts/tests/i18n_storage_boundary_selftest.py    # 闸门自证（每条检测通道一个必红 fixture）
    # 只有自测会设置这个环境变量，用来把扫描根指到 fixture 目录：
    #   I18N_BOUNDARY_SRC=<目录> python scripts/i18n_storage_boundary.py
退出码：0 = 干净；1 = 发现文案进入写库/业务键；2 = 一个 .kt 都没扫到（空过，见 main()）。
扫描根由 `__file__` 解析、与当前工作目录无关（Task 9b 缺陷 5：原先是相对路径，在 scripts/ 目录下
跑会扫到 0 个文件却仍打印"通过"并 exit 0，闸门形同虚设还给出假绿）。

检测通道（`scripts/tests/fixtures/storage_boundary/` 逐条覆盖；自测发现 fixture 缺失即失败）：
  A 直接引用    —— sink 实参里出现文案表引用（`strings.X` / `XyzStrings.y` / `AppStringsZh.y` /
                    别名句柄 `taskListStrings.y`、`currentStrings.y` / 三级以上的
                    `LocalStrings.current.<组>.<字段>`）
  B 跨行调用    —— 定位 `sink(` 的左括号后用括号配对取实参，行号按原文件计（Ruling 28 的修复）
  C 赋值污点    —— `val x = <同一行内含文案的表达式>`，允许类型注解与具名实参内联文案
  D 委托污点    —— `var x by remember/rememberSaveable { mutableStateOf(<文案>) }`
  E 污点链      —— C/D 得到的名字集迭代到不动点（`val a = strings.x; val b = a;` → sink 算违规），
                    深度不设上限；9b 实测当前工作树第二轮不产生任何新名字，即该通道是纯增量
  F sink 名单   —— 见下方 SINK_STEMS / SINK_EXTRA，每个词根/名字都带 commonMain 取证行号

已知边界（诚实声明，勿据此以为闸门覆盖了这些形态）：
  - 只做**同文件**赋值/委托追踪，没有跨文件数据流，也不分析函数返回值；
  - 变量名按整词匹配，同名变量在别处复用会被算进同一条污点（宁多报不漏报）；
  - `val strings = LocalStrings.current.<组>` 这种"取组句柄"的写法**不算**文案（Ruling 13 规定
    页面就这么写；把它当文案会把每页的句柄变量都污点化，退化成 9b 之前那批 `app`/`strings`
    误报的形态）；代价是更高阶的链式写法只有三级以上（`LocalStrings.current.<组>.<字段>`）才认得；
  - Canonical*Text.*（ChildHomeStrings.kt:193 / PomodoroStrings.kt:269）是 Ruling 25 处置方案的
    规范值出口，**不是**文案，本闸门永久豁免它——这是合规形态而非漏洞，不得删掉这条豁免。
"""
import io
import os
import re
import sys

REPO_ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SRC = os.path.join(REPO_ROOT, "composeApp", "src", "commonMain", "kotlin", "com", "hstudy", "app")
# 仅自测使用：把扫描根换到 fixture 目录（也用来验证"空目录必须非零退出"那一条）
SRC = os.environ.get("I18N_BOUNDARY_SRC", SRC)

# ---------------------------------------------------------------------------
# sink 名单（Task 9b 全部重写；旧注释"来自实际调用点，勿凭想象增删"当时并不成立）
#
# 取证口径：
#   1) SINK_STEMS 按**词根前缀**匹配：`insert` 命中 `insert / insertPlan / insertPointRecord / …`。
#      本仓 DAO 清一色是 `insertXxx(...)` / `updateXxx(...)` 形态，旧表的精确 "insert"、"update"
#      只能命中裸 `insert(`/`update(`（9b 实测 16/12 处），`insertPlan(` 这类一律漏掉。
#   2) SINK_EXTRA 是前缀覆盖不到、调用点又确实存在的精确名（原因见第 5 条）。
#   3) 每条后面注了它在 commonMain 的真实定义处/调用处行号；新增条目请一并补行号。
#      凭想象写进来的名字会被自测的"每个 sink 都必须被 fixture 报出来"那一环抓住。
#   4) 旧表里 8 项在本仓根本不存在（addRecord / save / upsert / deleteBy / submitProof /
#      recordMistake / saveHabit / setApproval —— 9b 实测 `<name>(` 的调用点计数均为 0），已删。
#      其中 `save` / `upsert` / `delete` 作为**词根**是有价值的（覆盖 saveVariation、
#      saveDailyReflection、softDeleteReward 等真实函数），故以词根形式回归；其余 5 项不保留。
#   5) `add` 与 `set` **故意不做成词根**：9b 实测 `add(strings.weakTipCheckin)` 在
#      ChildHomeScreen.kt:302/303/304 是 `buildList { }` 里的渲染用列表、不是写库，
#      前缀化会把合规代码判红。这两个家族改为逐字列举，代价是以后新出现的
#      `addXxx(`/`setXxx(` 写库路径需要人工补条目（自测 SinkCoverage.kt 会提醒漏了哪个名字）。
# ---------------------------------------------------------------------------
SINK_STEMS = [
    "insert",       # TaskDao.kt:27 / MistakeDao.kt:13 / WishGoalDao.kt:11 insert、
                    # FocusDao.kt:13 insertSession、:35 insertPlan、PointDao.kt:13 insertPointRecord、
                    # RewardDao.kt:22 insertReward、:32 insertExchange、MistakeDao.kt:45 insertReview、
                    # HabitDao.kt:15 insertChallenge、DailyAssignmentDao.kt:11 insert
    "update",       # TaskDao.kt:30 update、FocusDao.kt:38 updatePlan、
                    # PointApprovalDao.kt:28 updateStatus、RewardDao.kt:25 updateReward、
                    # MistakeDao.kt:38 updateReviewProgress、DailyScheduleDao.kt:14 update
    "upsert",       # 本仓暂无调用点（Room @Upsert 一旦引入就走这条路，先把词根占住）
    "delete",       # 删除路径同样决定业务键；软删除见 softDelete
    "softDelete",   # RewardDao.kt:28 softDeleteReward
    "save",         # MistakeRepository.kt:64 saveVariation(mistake, note)、
                    # UserPreferences.kt:233 saveDailyReflection(date, stars, tags)
    "mark",         # DailyAssignmentDao.kt:29 / WishGoalDao.kt:32 markCompleted
    "record",       # FocusRepository.kt:12 recordFocusSession(durationMinutes, taskTitle, points)
    "submit",       # PointApprovalRepository.kt:28 submitApproval（description 参与幂等比较）
    "adjust",       # PointRepository.kt:288 adjustPoints(points, note) → point_records.note
    "spend",        # PointRepository.kt:303 spendPoints(points, note)：**与 adjustPoints 同表同列**；
                    # 调用点 ChildRewardStoreScreen.kt:222、ChildPetScreen.kt:210/225/361
    "exchange",     # RewardRepository.kt:29 exchangeReward(rewardId, pointsSpent, note)
    "assign",       # AssignmentRepository.kt:12 assignTask（调用点 ParentAssignTaskScreen.kt:98）
    "undo",         # PointRepository.kt:138/146 undoCheckIn(bizKey, …) → bizKey 参与幂等读取
    "review",       # MistakeRepository.kt:48 reviewMistake(mistakeId, points, note)
    "checkIn",      # PointRepository.kt:113 checkIn、:87 checkInForTask、
                    # HabitRepository.kt:35 checkInChallenge
    "tryUnlock",    # HabitRepository.kt:71 tryUnlockAchievement(type, title, description, emoji)
]

# 词根覆盖不到、但调用点确实存在的写库路径（`add`/`set` 词根太宽，见上面第 5 条）
SINK_EXTRA = [
    "addPlan",          # FocusRepository.kt:38 addPlan(DailyPlanEntity) → taskTitle 直接写库
                        # （Task 7b 审查补：ChildDailyPlanScreen.kt:100 当前传的是对话框输入）
    "addReview",        # MistakeRepository.kt:34 addReview(mistakeId, points, note)
    "addSavedPoints",   # WishGoalDao.kt:29（心愿存钱罐累加，与目标名同表）
    "setDeviceName",    # UserPreferences.kt:138（DataStore 落盘；调用点 ParentSettingsScreen.kt:301）
    "setPairedDevice",  # UserPreferences.kt:142
]

CALL_HEAD = re.compile(
    r"\b("
    + "(?:" + "|".join(re.escape(s) for s in sorted(SINK_STEMS, key=len, reverse=True)) + r")\w*"
    + "|(?:" + "|".join(re.escape(s) for s in sorted(SINK_EXTRA, key=len, reverse=True)) + r")\b"
    + r")\s*\("
)

# 文案来源的四种写法（局部变量 strings.X、全表实例 AppStringsZh.x、别名句柄 XyzStrings.y、
# 高阶链式 LocalStrings.current.<组>.<字段>）。
#
# 外层必须是非捕获分组：Task 9 修 matcher 时发现原先没有这层括号，而 `|` 的优先级最低，
# 于是拼进 TAINT_DELEGATE 时整条模式被**从中间劈成三个备选支**——第 2、3 支变成"在文件任何位置
# 找 AppStringsZh.x / XyzStringsEn.y"，既不再要求 `var x by remember { … }` 前缀（污点规则形同虚设），
# 又没有捕获组（group(1) 是 None，扫描时直接 TypeError 崩掉）。这个崩溃正是往 ChildHomeScreen 注入
# `PomodoroStringsZh.breathingGuide` 时暴露出来的，属于 Ruling 28 之外的第二个闸门缺陷，一并修掉。
#
# Task 9b 缺陷 3：第 3 支原先把 `(?:Zh|En)` 写成**强制**，于是
#   · ChildHomeScreen.kt:39 `val taskListStrings = LocalStrings.current.taskList`（活着的第二个文案句柄，
#     :264 的 `taskListStrings.pointsEarnedLabel(…)` 就在用它）
#   · PomodoroTimer.kt:49 `val currentStrings by rememberUpdatedState(strings)` → :105 `currentStrings.phaseBreak`
# 都不被认成文案，跨表复用的字段从此在闸门里隐身。现在后缀可选。
# `(?!LocalStrings\b)` 把 CompositionLocal 本身排除出去，理由见 docstring「已知边界」第 3 条：
# 不排除就会把 `val strings = LocalStrings.current.childHome` 这个合规句柄判成污点变量，
# 正是 9b 之前 `app`/`strings` 那批误报的复发形态。
# Canonical*Text.* 不以 `Strings` 结尾，天然不匹配 → 规范值豁免保留，由自测的
# CanonicalCompliant.kt 钉住，不得改成文案。
COPY_SOURCE = (
    r"(?:"
    r"\bstrings\.[A-Za-z]\w*"
    r"|\bAppStrings(?:Zh|En)\.\w+"
    r"|\b(?!LocalStrings\b)[A-Za-z]\w*Strings(?:Zh|En)?\.\w+"
    r"|\bLocalStrings\.current(?:\.\w+){2,}"
    r")"
)
COPY_REF = re.compile(COPY_SOURCE)


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


def blank_string_contents(text):
    """把字符串字面量的**内容**替换成空格（引号与换行保留，下标仍对得上原文件）。

    Task 9b 缺陷 6（9b 实测后的结论比简报更细分，两段都写在这里）：
    `strip_comments` 故意不动字符串内容（注释定界符可能写在文案串里），于是串内的
    `save(` / `checkIn(y)` 会被 `CALL_HEAD` 当成调用点。但**这类假头几乎报不出红**：
    `extract_args` 自己也跳字符串，而串内引号数量天生是奇数，配对一路吃到 EOF 就返回
    None 被丢弃（9b 用 6 种文案串形态试过，全部 None）。所以简报里"文案串里的 sink 名
    会误报"这半条**不成立**，只是每次扫描多花一次无谓配对。
    **真正会误报的是污点通道**：帮助文案/错误提示里常有代码示例，
    `Text("写法示例：val demoNote = strings.demoTip 会被写进库里")` 这一串会让旧实现把
    `demoNote` 收进污点集，于是紧随其后那个**真用户输入**的同名变量被判成
    "经由变量 demoNote 传入"→ 合规代码判红（9b 实测：空白化后污点集为空、命中 0）。
    因此这一步保留，并由自测的两条对比断言钉住（不空白化时确实污染、空白化后不污染）。
    文案引用是代码不是字符串内容，空白化不会造成漏抓
    （自测 ParenInStringStillCaught.kt 证明"不因此漏报"）。
    """
    out = list(text)
    i, n = 0, len(text)
    while i < n:
        if text[i] != '"':
            i += 1
            continue
        triple = text.startswith('"""', i)
        i += 3 if triple else 1
        while i < n:
            if text[i] == "\\":
                out[i] = " "
                i += 1
                if i < n and text[i] != "\n":
                    out[i] = " "
                i += 1
                continue
            if triple and text.startswith('"""', i):
                i += 3
                break
            if not triple and text[i] == '"':
                i += 1
                break
            if text[i] != "\n":
                out[i] = " "
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

# Ruling 28 的缺陷与本节的修法（Task 9 开工前修，先于页面迁移单独提交）：
# 原先 CALL 是"单行正则"，而 scan() 又按行喂给它，于是**跨行的调用永远不会被匹配**。
# ChildHomeScreen.kt:583-589 的 checkIn(bizKey = …, description = …) 正好跨 7 行，
# ChildDailyPlanScreen.kt:100 的 addPlan(DailyPlanEntity(…)) 跨 8 行 —— 两处都在盲区内，
# 也就是说这条"硬约束"闸门对最危险的那个调用点（参与幂等判等的 description）从来没亮过。
# 现在改成：定位 `sink(` 的左括号，再用括号配对扫描取出**跨行**的实参文本。
# 扫描时跳过字符串字面量（英文文案里本来就写着括号，见 i18n_template_diff.py 同样的教训），
# 所以 `Text("已完成(点击撤销)")` 这种串不会把配对带偏。

# 一级污点（赋值形态）：`val x = <同一行里含文案的表达式>`，之后的 sink 调用只要出现 x 就算违规。
#
# Task 9b 缺陷 1 的实测（修改前）：这条正则同时用了**贪婪 `.*`** 和 **re.DOTALL**，于是每个匹配
# 都从文件里最靠前的 `var|val` 一路吃到最靠右的文案引用——`finditer` 每**个文件只产出一个名字，
# 且那个名字纯属位置、与文案毫无关系**：ChildHomeScreen.kt → ['app']、ChildTaskListScreen.kt →
# ['strings']、CommonComponents.kt → ['widthModifier']（同时 TAINT_DELEGATE 在这三个文件贡献 0 个
# 名字）。后果是"经由变量传入"这半侧闸门在所有已迁移页面上从未生效；而 `app` 进了污点集又恰好
# 出现在 lambda 实参里，把合规代码判红。另外 `: String =` 这类带类型注解的赋值被原模式的
# `\s*=` 挡掉，所以函数型文案字段 `val f: () -> String = { strings.x }` 与内联
# `.copy(note = strings.x)` 也一起漏抓。
# 修法：去掉 re.DOTALL、把值限定在同一行内、允许类型注解。9b 实测：真实树上新增的名字只有
# ChildHomeScreen.kt 的 `hardestName`（:253 `when (…) { … -> strings.difficultyChallengeLevel … }`，
# 确实是文案赋值，且只流向 Text()、不流向任何 sink），旧的 11 个位置性假名字全部消失。
# 没有数据流分析，只做同文件赋值追踪，因为本类缺陷的实际形态就是"把文案赋给状态再写库"。
TAINT_ASSIGN = re.compile(
    r"\b(?:var|val)\s+([A-Za-z_]\w*)\s*(?::[^=\n]+)?=\s*([^\n]*" + COPY_SOURCE + r"[^\n]*)"
)

# 二级污点（赋值链）：`val b = a` 且 a 已被判污点。TAINT_ASSIGN 只认"同一行含文案"，所以链长 ≥2
# （`val a = strings.x; val b = a;` → sink）在 9b 之前完全 uncovered，而且这个事实只写在注释里、
# 没有任何 fixture 能证明（缺陷 2）。现在把赋值形态迭代到不动点，深度不设上限
# （CHAIN_ROUNDS 只是防死循环的保险丝）；自测 ChainTaint.kt 走的就是这条通道。
ASSIGN_ANY = re.compile(r"\b(?:var|val)\s+([A-Za-z_]\w*)\s*(?::[^=\n]+)?=\s*([^\n]*)")
CHAIN_ROUNDS = 50

# 一级污点（委托形态）：`var x by remember { mutableStateOf(strings.y) }`。
# Task 7b 审查的实证补：原先只有赋值那条，所以 ChildPomodoroScreen.kt:47 这一形**漏抓**，
# :175 的 recordFocusSession(minutes, focusTarget, 0) 当时只报了 :177 的直接引用。
# 规范值不是文案：读的是 CanonicalFocusText.* / 字面量时，COPY_SOURCE 匹配不上，不会误报。
TAINT_DELEGATE = re.compile(
    r"\b(?:var|val)\s+([A-Za-z_]\w*)\s+by\s+remember(?:Saveable)?\s*(?:\([^()]*\))?\s*\{[^}]*"
    + COPY_SOURCE + r"[^}]*\}"
)


def collect_taint(text):
    """返回 (污点名集合, 链式传播轮数)。轮数供自测断言"fixture 确实走了链通道"。"""
    tainted = set()
    for pattern in (TAINT_ASSIGN, TAINT_DELEGATE):
        for match in pattern.finditer(text):
            name = match.group(1)
            # 只有参与了的分组 1 才是变量名：上面注释记录的那种"备选支被劈开"的形态会给 None，
            # 直接塞进 tainted 就会在扫描时崩掉（Task 9 注入变异时实测到）。
            if name:
                tainted.add(name)
    rounds = 0
    changed = True
    while changed and rounds < CHAIN_ROUNDS:
        changed = False
        rounds += 1
        for match in ASSIGN_ANY.finditer(text):
            name, rhs = match.group(1), match.group(2)
            if name in tainted:
                continue
            if any(re.search(r"\b%s\b" % re.escape(t), rhs) for t in tainted):
                tainted.add(name)
                changed = True
    return tainted, rounds


def scan(path):
    with io.open(path, encoding="utf-8") as handle:
        raw = handle.read()
    stripped = strip_comments(raw)
    code = blank_string_contents(stripped)
    tainted, _rounds = collect_taint(code)
    hits = []
    for match in CALL_HEAD.finditer(code):
        extracted = extract_args(code, match.end() - 1)
        if extracted is None:
            continue
        args, close = extracted
        start_line = code.count("\n", 0, match.start()) + 1
        end_line = code.count("\n", 0, close) + 1
        # 报错文本压成一行：跨行调用原来根本报不出来，这里先保证"报得出 + 行号对得上"
        # 摘要取自未空白化的文本便于阅读；空白化只替换字符，不改变下标与行号
        excerpt = " ".join(stripped[match.start():close + 1].split())
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


def iter_kt_files(root):
    for dirpath, _names, filenames in os.walk(root):
        for name in sorted(filenames):
            if name.endswith(".kt"):
                yield os.path.join(dirpath, name)


def main():
    found = []
    scanned = 0
    for full in iter_kt_files(SRC):
        scanned += 1
        for span, sink, text, why in scan(full):
            found.append((os.path.relpath(full, SRC), span, sink, text, why))
    if not scanned:
        print("存储边界守卫**异常退出**：扫描根下没有任何 .kt 文件")
        print("  扫描根：%s" % SRC)
        print("  这不是「干净」，是「什么都没扫」。请检查路径，以及是否残留了 I18N_BOUNDARY_SRC "
              "环境变量（正常跑闸门不应该设置它）。")
        return 2
    if not found:
        print("存储边界守卫通过：没有文案表字符串（含经由变量传入）出现在写库/业务键调用参数里")
        print("（已扫描 %d 个 .kt 文件，扫描根：%s）" % (scanned, SRC))
        print("（跨行调用已纳入匹配：Ruling 28 的行级盲区于 Task 9 修掉，见本文件 CALL_HEAD 一节）")
        print("（赋值污点/污点链/sink 词根/别名句柄于 Task 9b 加固，自证见 "
              "scripts/tests/i18n_storage_boundary_selftest.py）")
        return 0
    print("发现 %d 处疑似「界面文案进入存储或业务键」（扫描 %d 个 .kt 文件）：" % (len(found), scanned))
    for rel, span, sink, text, why in found:
        print("  %s:%s  %s(...)  [%s]\n      %s" % (rel, span, sink, why, text[:170]))
    print("\n处置要求：改为写入规范值（迁移前的原始文本或稳定 key），只在渲染时本地化；"
          "若该值参与业务判等（如 bizKey 的 description 比较），一律不本地化。")
    return 1


if __name__ == "__main__":
    sys.exit(main())
