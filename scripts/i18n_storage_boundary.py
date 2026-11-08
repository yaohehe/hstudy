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
                    `LocalStrings.current.<组>.<字段>` / **由 `X.current.<组>` 赋值出来的任何句柄**
                    `.y`，与变量名无关，见 HANDLE_BIND / 已知边界第 3 条）
  B 跨行调用    —— 定位 `sink(` 的左括号后用括号配对取实参，行号按原文件计（Ruling 28 的修复）
  C 赋值污点    —— `val x = <含文案的表达式>`，允许类型注解与具名实参内联文案；值表达式**最多桥接
                    一个换行**（`=` 之后换行仍算同一条赋值，见 TAINT_ASSIGN 一节），多行**普通**串
                    不认；多行的三引号原始串（`val x = 三个双引号 … ${文案} … 三个双引号`）由
                    TAINT_ASSIGN_RAW 单独覆盖（9b 审查 Critical 1 之后插值是唯一还能带代码的东西，
                    锚在成对的三引号上放开 DOTALL 是安全的，与缺陷 1 的"整段文件被吃进来"不是一回事）
  D 委托污点    —— `var x by remember/rememberSaveable { mutableStateOf(<文案>) }`
  E 污点链      —— C/D 得到的名字集迭代到不动点（`val a = strings.x; val b = a;` → sink 算违规），
                    深度不设上限；9b 实测当前工作树第二轮不产生任何新名字，即该通道是纯增量
  F sink 名单   —— 见下方 SINK_STEMS / SINK_EXTRA / SINK_CAPSUFFIX / RESERVED_STEMS，
                    SINK_STEMS / SINK_EXTRA / SINK_CAPSUFFIX 每个词根/名字都带 commonMain 取证行号，
                    且由自测反向对账"真实树里确有此声明"（自测第 3 节）。
                    **RESERVED_STEMS 走的是相反的那条规则、不在"确有声明"的对账里**：
                    它们是"预留、非现实"的词根，自测要求的是"真实树里**必须还没有**此声明"
                    （selftest:306-307），一旦被真实代码用上就必须搬回 SINK_STEMS
                    （selftest:26 与 :94-96 的注释就是这条规则本来的表述）。
  G 插值文案    —— 字符串字面量里的 `${strings.x}` / `$taintedVar` **算文案**（9b 审查 Critical 1：
                    双语迁移最常产出的复合说明就长这样；空白化时必须保留插值区，见
                    blank_string_contents）；fixture InterpolationCopy.kt / InterpolationNoFp.kt

已知边界（诚实声明，勿据此以为闸门覆盖了这些形态）：
  - 只做**同文件**赋值/委托追踪，没有跨文件数据流；下列三种形态至今仍然漏抓，Task 10/11 需人工确认：
      · 函数返回值：`fun buildNote() = strings.x` 之后 `adjustPoints(1, buildNote())`；
      · 字段赋值：`task.note = strings.x` 之后 `updateTask(task)`（污点集只记**变量名**，不记属性）；
      · 无关键字的重新赋值：`note = strings.x`（`note` 之前已声明，本轮不加 `var/val`），
        C 通道要求 `var|val` 前缀，因此这一形不进污点集；
  - 变量名按整词匹配，同名变量在别处复用会被算进同一条污点（宁多报不漏报）；
  - **句柄认的是形状，不是名字**（9b 审查 Important 3）：`val <任意名> = X.current.<组>` 绑定出来的
    变量一律算文案句柄，`<任意名>.<字段>` 就是文案——旧规则只认"变量名以 Strings 结尾"，于是仓库
    自己的约定（`val common = LocalStrings.current.common`，真实树 4 处：ChildDailyPlanScreen.kt:123/172、
    ChildMemoryTrainingScreen.kt:26、ChildPomodoroScreen.kt:28）是一条活的漏抓通道。两级
    `X.current.<组>` 本身仍**不算**文案（Ruling 13 规定页面就这么写；把它当文案会把每页的句柄变量
    都污点化，退化成 9b 之前那批 `app`/`strings` 误报的形态），只有 `.字段` 才算；
    句柄别名 `val cur = strings` 也登记为句柄，但**只限右端整个就是句柄**（`val x by
    rememberUpdatedState(句柄)` 同形）——否则 ChildHomeScreen.kt:253 那种
    `val hardestName = when (…) { -> strings.x }` 的中间变量会变成"文案来源"，那是误报温床，
    它该走的是污点通道而不是句柄通道（两条都各有 fixture 钉住）；
  - **句柄别名再包一层就看不见**（Task 10 补，四种形态全部实测：把句柄换成 sink 实参后闸门不报）：
      · 函数**参数**句柄：`fun f(c: CommonStrings) { repo.adjustPoints(1, c.cancelButton) }`；
      · **类字段 / 构造参数**句柄：`class X(private val common: CommonStrings)` → `common.<字段>` 进 sink；
      · **解构**句柄：`val (a, b) = strings.common to strings.parentHome` → `a.<字段>` 进 sink
        （C 通道要求 `=` 右端整个就是句柄，解构形没有 `val a = …` 这条赋值，所以进不了污点集）；
      · **容器字段**句柄：`private val pair: Pair<CommonStrings, CommonStrings>` → `pair.first.<字段>`
        （HANDLE_BIND 的形状锚在 `X.current.<组>` 的 `current` 上，字段名走不到这条规则）。
    边界**不是**"别名一律漏抓"：`val g = remember { common }`、`val g = if (f) common else pet`、
    `val g = holder.first`（`holder` 是**局部** `val holder = common to pet`）三种 Task 10 实测**能抓**
    ——局部变量由 `X.current.<组>` 绑定后再被 remember/三元/局部 Pair 转一手，
    仍会经 C+E 两条通道进污点集（DocProbe.kt 三条 sink 全部报红）。
    真正的分水岭是"**句柄的最初来源是不是 `X.current.<组>` 赋值句**"：来自参数、字段、解构、
    容器字段这三种"非赋值句来源"时，第一个句柄就不在污点集里，后面转几手都追不到。
    复现配方（Task 10 用过的探针不在仓库里，是 gitignore 下的一次性目录）：
    在 `<dir>/DocProbe2.kt` 里写上面四种形态，各配一条 `repo.adjustPoints(n, …)`，
    再跑 `I18N_BOUNDARY_SRC=<dir> python scripts/i18n_storage_boundary.py`——
    报出的行号里查不到那四条就是本条边界成立；Task 11/12 若要补通道，应先把这四种做成 fixture。
  - 文案赋值的**上限**（超出就漏抓，9b 审查 Important 5 要求逐条写在这里）：多行普通串/多行 lambda
    表达式里的文案（C 通道只允许 `=` 之后穿过纯空白行，中间一旦有内容的行就断）、
    `var` 已声明变量的**无关键字重新赋值**（上面第 1 条第 3 小点）、字段赋值（同第 1 条第 2 小点）、
    同文件 helper 的返回值（同第 1 条第 1 小点）；
  - 三引号原始串赋值另有一道**行数上限** `_RAW_ASSIGN_MAX_LINES = 60`（Task 10 实测）：
    原始串体的换行数超过 60 时 TAINT_ASSIGN_RAW 直接跳过这一条赋值，于是
    `val note = 三个双引号 …${strings.x}…（66 行）… 三个双引号` + 下一行 `repo.adjustPoints(2, note)` **不报**，
    而完全同形态、只有 12 行的那一条**报**（探针 DocProbe3.kt / DocProbe4.kt，同一目录同一命令）。
    这道上限是"残缺语法最坏只吃这么多行"的保险，**不是覆盖率声明**：
    它把"长帮助文本 / 引导文案"这一类（最可能被人顺手本地化后写库的形态）留在盲区里；
  - Canonical*Text.*（ChildHomeStrings.kt:193 / PomodoroStrings.kt:269）是 Ruling 25 处置方案的
    规范值出口，**不是**文案，本闸门永久豁免它——这是合规形态而非漏洞，不得删掉这条豁免；
  - `add`/`set` 只在**大写后缀**形态下算 sink（`addTask(` / `setDeviceName(`），裸 `add(`/`set(`
    （buildList/MutableList 渲染容器）不算，`setOf(`/`addAll(` 等集合与标准库操作在
    SINK_CAPSUFFIX_DENY 里逐字排除；
  - 字符串字面量里的插值区按代码处理，所以**插值里再套一层字面量**（`"${"yes"} ${strings.x}"`）时
    `extract_args` 的引号配对会被内层引号带偏（走向和直觉不一样，属于脆性）。9b round 1 实测三种
    嵌套引号形状（`"${"yes"} ${strings.x} ok"`、`"前缀 ${strings.x} 后缀"`、
    `"${if (x > 1) "多" else "少"} ${strings.y}"`）**全部照样报红**，因此这条记为"脆性、目前未见
    漏抓"，不是一张覆盖保证；与之同类的还有 blank_string_contents 一节记录的"串内奇数引号"。
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
#      9b 审查 Important 4 又把这条规则收紧了一格：**"有 fixture"不等于"存在"**——`upsert` 当时
#      只有 fixture 里的 `upsertHabit(` 撑着，真实树里 `fun upsertXxx(` 声明数 = 0（实测），
#      所以它现在被挪进 RESERVED_STEMS 单独标注（预留、非现实）；SINK_STEMS 里每一条都必须
#      在 commonMain 有 ≥1 个 `fun <词根>*(` 声明，自测第 3 节反向对账，同时
#      RESERVED_STEMS 一旦被真实代码用上就必须搬回 SINK_STEMS（反向对账也会抓住赖着不走的）。
#   5) `add` / `set` **不做成裸词根**，而是走 SINK_CAPSUFFIX 的"大写后缀"规则：
#      裸 `add(strings.weakTipCheckin)`（ChildHomeScreen.kt:302/303/304 的 buildList 渲染列表）
#      是合规形态，前缀化会把这三条判红（9b 实测 3 处误报）；而 `\b(?:add|set)[A-Z]\w*` 在真实树上
#      **0 命中**，却能一次性覆盖 addMistake/addTask/addGoal/addReward/addChallenge/addSchedule
#      这 6 条早已存在、UI 可达、且写进内容最密的表（task/mistake/wish/reward 的 name+title 列）
#      的写库路径（9b 审查 Critical 2 的测量，我逐条复现：见下方取证行号）。
#      所以"以后新增 addXxx(`/setXxx(` 写库路径要人工补条目"这句话**已经不成立了**：
#      这两个家族现在由形状规则自动覆盖，SINK_EXTRA 里不再需要逐字列举它们。
# ---------------------------------------------------------------------------
SINK_STEMS = [
    "insert",       # TaskDao.kt:27 / MistakeDao.kt:13 / WishGoalDao.kt:11 insert、
                    # FocusDao.kt:13 insertSession、:35 insertPlan、PointDao.kt:13 insertPointRecord、
                    # RewardDao.kt:22 insertReward、:32 insertExchange、MistakeDao.kt:45 insertReview、
                    # HabitDao.kt:15 insertChallenge、DailyAssignmentDao.kt:11 insert
    "update",       # TaskDao.kt:30 update、FocusDao.kt:38 updatePlan、
                    # PointApprovalDao.kt:28 updateStatus、RewardDao.kt:25 updateReward、
                    # MistakeDao.kt:38 updateReviewProgress、DailyScheduleDao.kt:14 update
    "delete",       # 删除路径同样决定业务键；MistakeRepository.kt:25 deleteMistake、
                    # WishGoalRepository.kt:26 deleteGoal、软删除见 softDelete
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

# **预留**词根：真实树当前 0 声明（自测会核对这一点），一旦引入（Room @Upsert、
# `fun upsertXxx`）就立刻生效；搬进 SINK_STEMS 之前它不参与"必须有真实声明"的对账。
RESERVED_STEMS = [
    "upsert",       # 9b 实测 commonMain `fun upsert*(` 声明 0 个、`upsert*(` 调用 0 处
]

# 大写后缀词根：`\b(?:add|set)[A-Z]\w*(` 才算写库调用（原因见上面第 5 条）。
# 真实树取证（9b 审查 Critical 2 的六条 + set 家族抽样，全部是 DAO/Repository/Preferences 写路径）：
#   addMistake   MistakeRepository.kt:21      → mistakeDao.insert(mistake)（题干列）
#   addTask      TaskRepository.kt:29         → taskDao.insert(task)（标题列）
#   addGoal      WishGoalRepository.kt:22     → wishGoalDao.insert(goal)（目标名）
#   addReward    RewardRepository.kt:23       → rewardDao.insertReward(reward)（奖品名）
#   addChallenge HabitRepository.kt:11        → habitDao.insertChallenge(challenge)（挑战标题）
#   addSchedule  ScheduleRepository.kt:18     → scheduleDao.insert(schedule)（日程标题）
#   另加 set 家族同理：setMastered MistakeDao.kt:34、setPlanCompleted FocusDao.kt:47、
#   setDailyTargets UserPreferences.kt:209、addProof HabitRepository.kt:83 …
SINK_CAPSUFFIX = ["add", "set"]

# 大写后缀规则的**逐字排除表**：这些是标准库/集合/框架装配，不是写库路径。
# 留在这里而不下沉到正则，是为了让"排除了什么"可被自测第 3 节直接对账。
SINK_CAPSUFFIX_DENY = [
    "setOf",        # kotlin 集合构造；真实树 commonMain 当前 0 处（fixture SinkCoverage.kt 的
                    # saveDailyReflection 那一条里就写着 `setOf(strings.dailyReflectionTag)`），
                    # 但它是"把文案放进标签集合"的渲染形态，判红就是误报
    "setOfNotNull", # 同上
    "addAll",       # MutableList 批量追加（Screen.kt:121 用于拼路由表）= 渲染容器，
                    # 与裸 add( 同一类；Room 的批量写库走 insertAll，已被 insert 词根覆盖
]

# 词根覆盖不到、但调用点确实存在的写库路径。9b 审查 Important 4 之后这里只留**真实声明**：
# 每一条都能在 commonMain 找到 `fun <名字>(`，自测第 3 节逐条核对，凭想象的名字会让自测变红。
# 下面 5 条其实也已被 SINK_CAPSUFFIX 的大写后缀规则覆盖，保留是为了让"取证名字 ↔ 真实声明"
# 逐条可对账（历史上它们正是靠逐字列举才没被漏掉的），不是"人工补条目"那条旧义务的残留。
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
    + "(?:" + "|".join(re.escape(s) for s in sorted(SINK_STEMS + RESERVED_STEMS, key=len, reverse=True))
    + r")\w*"
    + "|(?:" + "|".join(re.escape(s) for s in sorted(SINK_EXTRA, key=len, reverse=True)) + r")\b"
    + "|(?:" + "|".join(re.escape(s) for s in SINK_CAPSUFFIX) + r")[A-Z]\w*"
    + r")\s*\("
)

# 文案来源的四种写法（局部变量 strings.X、全表实例 AppStringsZh.x、别名句柄 XyzStrings.y、
# 高阶链式 LocalStrings.current.<组>.<字段>），外加 9b 审查 Important 3 补的**形状句柄**一支
# （copy_source_for() / HANDLE_BIND，见下）。
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
#
# 9b 审查 Important 3（**这一支改了上面那条"按名字"的口径**）：第 3 支是按**标识符名**（`\w*Strings`）
# 认句柄的，而仓库自己的约定里有一大批句柄名字不以 Strings 结尾——`val common =
# LocalStrings.current.common` 就至少 4 处（ChildDailyPlanScreen.kt:123/172、
# ChildMemoryTrainingScreen.kt:26、ChildPomodoroScreen.kt:28），审查探针
# `adjustPoints(1, common.deleteContentDescription)` 当时 exit 0。现在**按表达式形状**登记句柄名
# （HANDLE_BIND），`copy_source_for(handles)` 把 `<句柄名>.<字段>` 追加成第 5 支；名字仍以
# Strings 结尾的走第 3 支，两条并存、互不冲突。
# Canonical*Text.* 不以 `Strings` 结尾，天然不匹配 → 规范值豁免保留，由自测的
# CanonicalCompliant.kt 钉住，不得改成文案。
COPY_SOURCE_INNER = (
    r"\bstrings\.[A-Za-z]\w*"
    r"|\bAppStrings(?:Zh|En)\.\w+"
    r"|\b(?!LocalStrings\b)[A-Za-z]\w*Strings(?:Zh|En)?\.\w+"
    r"|\bLocalStrings\.current(?:\.\w+){2,}"
)
COPY_SOURCE = r"(?:" + COPY_SOURCE_INNER + r")"
COPY_REF = re.compile(COPY_SOURCE)

# 句柄绑定：`val <任意名>[: 类型] = [限定名.]XxxStrings.current[.<组>]`。
# 只吃**两级以内**（`current` 或 `current.<组>`）：三级以上本来就是文案（第 4 支），
# 把它登记成句柄也只是多一条等价的宽松匹配，不影响判定方向。
# 用 blank_string_contents 之后的文本提取，所以文案串里写的"示例代码"不会造出句柄
# （与缺陷 6 的 demoNote 同源，自测 CommentedSinkNoFp.kt / StringLiteralNoFp.kt 盯着）。
HANDLE_BIND = re.compile(
    r"\b(?:var|val)\s+([A-Za-z_]\w*)\s*(?::[^=\n]+)?=\s*"
    r"(?:[A-Za-z_]\w*\.)?[A-Za-z]\w*Strings\.current(?:\.[A-Za-z_]\w*)?"
)

# 句柄**别名**：右端整表达式就是另一个句柄（`val alias = common`，允许 `!!` 尾巴），
# 以及 `val x by rememberUpdatedState(句柄)`（PomodoroTimer.kt:49 的真实形状）。
HANDLE_ALIAS = re.compile(r"^([A-Za-z_]\w*)(?:!!)?$")
HANDLE_DELEGATE = re.compile(
    r"\b(?:var|val)\s+([A-Za-z_]\w*)\s+by\s+rememberUpdatedState\s*\(\s*([A-Za-z_]\w*)\s*\)"
)


def collect_copy_handles(text):
    """本文件里的文案句柄名：HANDLE_BIND 直接绑定的，加上 `val 别名 = 句柄` 一类的别名（迭代到不动点）。

    别名这一轮是 9b 审查 Important 5 点名"要写进边界"的 `val cur = strings` 形态：既然句柄已经
    是一个名字集合，沿赋值链再传播一层就是几行代码的事，于是这条从"边界"变成"覆盖"。
    **但传播条件是右端整表达式就是那个句柄本身**（HANDLE_ALIAS），不能只看"出现过句柄名"——
    否则 ChildHomeScreen.kt:253 的
        val hardestName = when (…) { 3 -> strings.difficultyChallengeLevel; … }
    会被误登记成句柄（它当然是**污点**，但不是句柄；9b 审查 Important 3 的修法如果做得太松
    就会把每个提到 strings 的中间变量都变成"文案来源"，那是误报的温床）。
    """
    handles = set()
    for match in HANDLE_BIND.finditer(text):
        handles.add(match.group(1))
    for match in HANDLE_DELEGATE.finditer(text):
        if match.group(2) in handles or re.match(r"^[A-Za-z]\w*Strings$", match.group(2)):
            handles.add(match.group(1))
    changed = True
    while changed:
        changed = False
        for match in ASSIGN_ANY.finditer(text):
            name, rhs = match.group(1), match.group(2).strip()
            if name in handles:
                continue
            alias = HANDLE_ALIAS.match(rhs)
            if alias and alias.group(1) in handles:
                handles.add(name)
                changed = True
    return handles


_PATTERN_CACHE = {}


def copy_source_for(handles):
    """把句柄名集合拼进 COPY_SOURCE；无句柄时返回原串（缓存，逐文件调用不起成本）。"""
    if not handles:
        return COPY_SOURCE
    key = frozenset(handles)
    cached = _PATTERN_CACHE.get(key)
    if cached is None:
        alt = "|".join(re.escape(h) for h in sorted(key, key=len, reverse=True))
        cached = r"(?:" + COPY_SOURCE_INNER + r"|\b(?:" + alt + r")\.[A-Za-z]\w*)"
        _PATTERN_CACHE[key] = cached
    return cached


def copy_ref_for(handles):
    """scan() 用的"直接引用"模式：与 collect_taint 共享同一份按句柄定制编译的结果。"""
    return taint_patterns(copy_source_for(handles))[3]


_IDENT_RE = re.compile(r"[A-Za-z_]\w*")


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


def _skip_literal(text, i):
    """text[i] 是开引号（单/三）；返回闭引号之后的下标，**不改动任何字符**。
    只给插值区做括号配对用（那里的内容要不要空白化由 _blank_literal 决定）。"""
    triple = text.startswith('"""', i)
    i += 3 if triple else 1
    n = len(text)
    while i < n:
        c = text[i]
        if c == "\\":
            i += 2
            continue
        if triple and text.startswith('"""', i):
            return i + 3
        if not triple and c == '"':
            return i + 1
        i += 1
    return n


def _interp_end(text, i, limit):
    """text[i] == '{'（插值开括号）；返回配对的 '}' 下标，字符串字面量整体跳过。
    配不上就返回 limit（语法残缺的文案串，最坏情况是把这一串剩下的部分当代码留着，
    方向上是"多扫一次"而不是漏抓）。"""
    depth = 0
    i = min(i, limit)
    while i < limit:
        c = text[i]
        if c == '"':
            i = min(_skip_literal(text, i), limit)
            continue
        if c == "{":
            depth += 1
        elif c == "}":
            depth -= 1
            if depth == 0:
                return i
        i += 1
    return limit


def _blank_literal(out, text, i, end):
    """text[i] 是开引号：空白化字面量内容，但**保留 ${…} 与 $name 插值区**（递归按代码处理），
    返回闭引号之后的下标。"""
    triple = text.startswith('"""', i)
    i += 3 if triple else 1
    while i < end:
        c = text[i]
        if c == "\\":
            out[i] = " "
            if i + 1 < end and text[i + 1] != "\n":
                out[i + 1] = " "
            i += 2
            continue
        if triple and text.startswith('"""', i):
            return i + 3
        if not triple and c == '"':
            return i + 1
        if c == "$":
            nxt = text[i + 1:i + 2]
            if nxt == "{":
                close = _interp_end(text, i + 1, end)
                _blank_code(out, text, i + 2, min(close, end))
                i = close + 1
                continue
            ident = _IDENT_RE.match(text, i + 1, end)
            if ident:                      # "$name" 带的就是那个变量本身 → 必须保留，否则漏抓
                i = ident.end()
                continue
        if c != "\n":
            out[i] = " "
        i += 1
    return i


def _blank_code(out, text, i, end):
    """代码模式：字符原样保留，只在遇到字符串字面量时交给 _blank_literal 处理其内容。"""
    while i < end:
        if text[i] == '"':
            i = _blank_literal(out, text, i, end)
            continue
        i += 1
    return i


def blank_string_contents(text):
    """把字符串字面量的**非插值部分**替换成空格（引号、换行、`${…}`/`$name` 原样保留，
    下标与行号仍对得上原文件）。

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

    9b 审查 Critical 1 **推翻了旧注释的最后一句**（原文写的是"文案引用是代码不是字符串内容，
    空白化不会造成漏抓"——这句话只在**不带插值**的串里成立）。`${…}` 里的内容确实是代码，
    但它**位于字符串字面量内部**，一刀切空白化就把双语迁移最常产出的复合说明抹平了：
        val composed = "review: ${strings.checkInDescription}" ; adjustPoints(1, composed)  → 旧版 exit 0
        submitApproval("R7", "${strings.milestoneRewardNote} ok", 1)                        → 旧版 exit 0
    （两条都是审查者复现、我逐条实测到同样结果的，见自测 InterpolationCopy.kt 的 G1/G2）。
    现在插值区按代码递归处理：`${…}` 与简单 `$name` 保留，`${}` 里再套的字面量内容继续空白化。
    代价是插值里的嵌套引号会让 `extract_args` 的配对走向变得不直观（实测三种形状仍全部报红，
    属于脆性而不是保证），详见 docstring 已知边界最后一条。
    """
    out = list(text)
    _blank_code(out, text, 0, len(text))
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

# 一级污点（赋值形态）：`val x = <含文案的表达式>`，之后的 sink 调用只要出现 x 就算违规。
# 关于"跨不跨行"（9b 审查 Minor，旧注释写的是"同一行"，不准确）：值部分用 `[^\n]*文案[^\n]*`
# 限定在**一个物理行**上，但 `=` 后面的 `\s*` 自己就能吃换行，所以
#     val x =
#         strings.note
# 这种"等号换行"仍然算同一条赋值（自测 InterpolationCopy.kt 的 I4 就是这一形，它依赖的正是这一步
# 而不是 DOTALL；同文件的 I5 是多行三引号原始串，由下面的 TAINT_ASSIGN_RAW 负责，两者都不是
# "DOTALL 全放开"）。多行 lambda / 多行普通串表达式里的文案则不认，那是 C 通道的真实上限；
# 旧实现靠 re.DOTALL 把整段文件吃进来才是缺陷 1 的病根。
#
# Task 9b 缺陷 1 的实测（修改前）：这条正则同时用了**贪婪 `.*`** 和 **re.DOTALL**，于是每个匹配
# 都从文件里最靠前的 `var|val` 一路吃到最靠右的文案引用——`finditer` 每**个文件只产出一个名字，
# 且那个名字纯属位置、与文案毫无关系**：ChildHomeScreen.kt → ['app']、ChildTaskListScreen.kt →
# ['strings']、CommonComponents.kt → ['widthModifier']（同时 TAINT_DELEGATE 在这三个文件贡献 0 个
# 名字）。后果是"经由变量传入"这半侧闸门在所有已迁移页面上从未生效；而 `app` 进了污点集又恰好
# 出现在 lambda 实参里，把合规代码判红。另外 `: String =` 这类带类型注解的赋值被原模式的
# `\s*=` 挡掉，所以函数型文案字段 `val f: () -> String = { strings.x }` 与内联
# `.copy(note = strings.x)` 也一起漏抓。
# 修法：去掉 re.DOTALL、把值限定在一行内、允许类型注解。9b 实测：真实树上新增的名字只有
# ChildHomeScreen.kt 的 `hardestName`（:253 `when (…) { … -> strings.difficultyChallengeLevel … }`，
# 确实是文案赋值，且只流向 Text()、不流向任何 sink），旧的 11 个位置性假名字全部消失。
# 没有数据流分析，只做同文件赋值追踪，因为本类缺陷的实际形态就是"把文案赋给状态再写库"。
TAINT_ASSIGN = re.compile(
    r"\b(?:var|val)\s+([A-Za-z_]\w*)\s*(?::[^=\n]+)?=\s*([^\n]*" + COPY_SOURCE + r"[^\n]*)"
)

# 二级污点（赋值链）：`val b = a` 且 a 已被判污点。TAINT_ASSIGN 只认"一行内含文案"，所以链长 ≥2
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

# COPY_SOURCE 的**变体**：TAINT_ASSIGN / TAINT_DELEGATE 这两个模块级常量只带基础四支，
# 真实扫描走下面这组按文件编译的模式（第 5 支"形状句柄"的名字集合是每个文件各自的，
# 见 collect_copy_handles）。自测第 6 节的正则级断言可以继续用上面两个常量。
#
# 第三个模式是多行原始串形态：`val x = """…${文案}…"""`。审查 Critical 1 之后插值会被保留，
# 于是"值跨多行"这一形在三引号串里只可能因为 TAINT_ASSIGN 的一行上限而漏抓。这里**只**对
# `"""…"""` 放开 DOTALL：锚定在成对的三引号上，不会像缺陷 1 那样把整段文件吃进来；
# 再加上 _RAW_ASSIGN_MAX_LINES 的行数上限，残缺语法最坏也只吃这么多行。
_VARIANT_CACHE = {}
_RAW_ASSIGN_MAX_LINES = 60


def taint_patterns(copy_source):
    """返回 (赋值, 委托, 多行原始串赋值, 直接引用) 四个 copy_source 定制版；带缓存。"""
    cached = _VARIANT_CACHE.get(copy_source)
    if cached is None:
        cached = (
            re.compile(r"\b(?:var|val)\s+([A-Za-z_]\w*)\s*(?::[^=\n]+)?=\s*"
                       r"([^\n]*" + copy_source + r"[^\n]*)"),
            re.compile(r"\b(?:var|val)\s+([A-Za-z_]\w*)\s+by\s+remember(?:Saveable)?"
                       r"\s*(?:\([^()]*\))?\s*\{[^}]*" + copy_source + r"[^}]*\}"),
            re.compile(r"\b(?:var|val)\s+([A-Za-z_]\w*)\s*(?::[^=\n]+)?=\s*\"\"\""
                       r"(.*?)\"\"\"", re.DOTALL),
            re.compile(copy_source),
        )
        _VARIANT_CACHE[copy_source] = cached
    return cached


def collect_taint(text):
    """返回 (污点名集合, 链式传播轮数)。轮数供自测断言"fixture 确实走了链通道"。

    text 必须是 strip_comments + blank_string_contents 之后的代码；句柄名也从同一份文本里取，
    所以文案串里写的示例代码既不会造出句柄也不会造出污点（缺陷 6 与 Important 3 共用这一份输入）。
    """
    handles = collect_copy_handles(text)
    assign, delegate, raw_assign, ref = taint_patterns(copy_source_for(handles))
    tainted = set()
    for pattern in (assign, delegate):
        for match in pattern.finditer(text):
            name = match.group(1)
            # 只有参与了的分组 1 才是变量名：上面注释记录的那种"备选支被劈开"的形态会给 None，
            # 直接塞进 tainted 就会在扫描时崩掉（Task 9 注入变异时实测到）。
            if name:
                tainted.add(name)
    for match in raw_assign.finditer(text):
        name, body = match.group(1), match.group(2)
        if not name or body.count("\n") > _RAW_ASSIGN_MAX_LINES:
            continue
        if ref.search(body):
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
    # 直接引用检测用**本文件**的文案来源模式（形状句柄名是逐文件的，COPY_REF 那份全局常量只是
    # 基础四支，供自测的正则级断言使用）
    copy_ref = copy_ref_for(collect_copy_handles(code))
    hits = []
    for match in CALL_HEAD.finditer(code):
        sink_name = match.group(1)
        if sink_name in SINK_CAPSUFFIX_DENY:
            continue
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
        if copy_ref.search(args):
            why = "直接引用文案表"
        else:
            leaked = [name for name in tainted if re.search(r"\b%s\b" % re.escape(name), args)]
            if leaked:
                why = "经由变量 %s 传入" % ", ".join(sorted(leaked))
        if why:
            hits.append((span, sink_name, excerpt, why))
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
        print("（赋值污点/污点链/sink 词根/别名句柄于 Task 9b 加固，插值文案/add·set 大写后缀/"
              "形状句柄于 9b 审查 round 1 补上，自证见 "
              "scripts/tests/i18n_storage_boundary_selftest.py）")
        return 0
    print("发现 %d 处疑似「界面文案进入存储或业务键」（扫描 %d 个 .kt 文件）：" % (len(found), scanned))
    for rel, span, sink, text, why in found:
        print("  %s:%s  %s(...)  [%s]\n      %s" % (rel, span, sink, why, text[:170]))
    print("\n处置要求：改为写入规范值（迁移前的原始文本或稳定 key），只在渲染时本地化；"
          "若该值参与业务判等（如 bizKey 的 description 比较），一律不本地化。")
    return 1


def _force_utf8_streams():
    """stdout/stderr 钉 UTF-8（终审 D-B：修 GBK 崩溃**这一类**，不再写成"请先 export
    PYTHONIOENCODING=utf-8"的环境注）。闸门打印中文的"为什么算文案"说明与 `↔` 这类符号，
    管道/重定向到本机 cp936 控制台时 `print` 会抛 UnicodeEncodeError，于是**一条全绿的扫描
    也会非零退出**——这正是"恒红的闸门没人读"要防的假红。
    完整理由见 `scripts/i18n_template_diff.py` 的同名函数。

    放在文件末尾：本文件的行号被自测与本文件自己的「检测通道」清单按坐标引用，护栏插进
    import 之后会把那些坐标推成假引用。这里不移动任何一行既有代码。
    自测的 `run_gate()` 另有 `PYTHONIOENCODING=utf-8`，那是"自测读到的字节恒为 UTF-8"的契约，
    与本护栏互补：护栏管人直接跑闸门，环境变量管子进程输出。
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
