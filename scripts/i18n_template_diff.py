#!/usr/bin/env python3
"""Ruling 20(b) 的等价性证据：迁移前的页面中文模板 与 迁移后的文案表，逐字节比对。

用法（不带参数就复现"番茄钟 + 专注这一整组已迁页面"的等价性，**默认组是自洽的，应当退 0**）：

    python scripts/i18n_template_diff.py                 # 默认组：6 页 × 2 表（Pomodoro/Focus）
    python scripts/i18n_template_diff.py --branch        # 整枝组：全部已迁页面 × 全部四十三张表
    python scripts/i18n_template_diff.py --pages-rev 20bc060 \\
        --page com/hstudy/app/ui/child/ChildHomeScreen.kt \\
        --table com/hstudy/app/i18n/ChildHomeStrings.kt#ChildHomeStringsZh

`--page` 可以写成 `REV:路径`（也可以裸路径，此时用 `--pages-rev`），因为**同一张表可能服务两批
迁移时间不同的页面**：Task 7 的三页在 d336fb8 还是中文，Task 7b 的三页要到 761b24e 才是中文。
默认组就按这个方式把 6 个页面各取自己的迁移前 rev，表取工作区 —— 于是"表里每一条整串都能在
某个页面的迁移前原文里找到，反之亦然"这个命题第一次变得可以被一条命令验证。

哪些东西今天**能**钉回历史位置、哪些**不能**（终审 Minor 2 复核后写死在这里，因为上一版这段给的
`--tables-rev ceaf0f5` 已经跑不通了，见下）：

* **页面侧：可以逐个钉**，而且必须钉——每个页面取**它自己那批任务迁移前**的 rev，这是本命令能成立
  的前提。`--page <rev>:路径` 现给现用；默认组与整枝组的页面 rev 是 [DEFAULT_PAGES] / [BRANCH_PAGES]
  里的常量。这些常量现在**是 CI 的一部分**：`.github/workflows/android.yml` 里这道闸门自终审 D-A 起
  带 `fetch-depth: 0` 并**阻塞**构建，于是"某个钉住的 rev 不在历史里"不再是本地才能发现的问题。
* **表侧：`--tables-rev` 只对"那个 rev 已经含**全部四十三张**表"可用**。限制不来自表侧读取本身，而来自
  [_global_sets]：判断成对登记是否过期时，它**不管这次命令带了哪几张表**，都按 `--tables-rev` 把
  [BRANCH_TABLES] 四十三张全读一遍。阶段 2 W7 的 `PetStrings` 是 W7 提交才落进历史的，所以**可用的最早
  rev 就是 W7 提交本身**；在它之前（W6 收口 a5cd674、W5 收口 1e1e382、W4 收口 5d65128、W3 收口 14c4d82、W2 收口 aae364e、W1 收口 5deed68、4d28c8e、c3bd94a）一律
  `git show …失败`。实测（W2 提交之前的工作树）：
      --tables-rev HEAD      -> **1**    `git show HEAD:…/ParentTaskManageStrings.kt 失败`（W2 四张表尚未进历史）
      --tables-rev 4d28c8e   -> **1**    `git show 4d28c8e:…/MistakeStrings.kt 失败`（W1 之前没有错题表）
      --tables-rev c3bd94a   -> **1**    `git show c3bd94a:…/MistakeStrings.kt 失败`（W1 之后最早 rev 前移）
      --tables-rev 41c3d39   -> **1**    `git show 41c3d39:…/ParentGateStrings.kt 失败`
      --tables-rev ceaf0f5   -> **1**    `git show ceaf0f5:…/ChildHomeStrings.kt 失败`
  旧版这段的示例就是最后那条，`d4d441b`（终审第 9 条，引入整枝范围）之后它恒退 1，所以**已删**。
  真要复现某个早期提交的表读数，只能 `git checkout <rev> -- composeApp/src/commonMain/kotlin/
  com/hstudy/app/i18n/` 把表带回工作区再跑、读完还原（那会把页面也一起带回，只能整树复现，
  不是"表取历史 / 页面取现"的混合形态）；`--tables-rev` 做不到。

组合的规矩（Task 9+ 采用前必读）：表侧整串必须能在**这次带进来的页面**里找到原文。
只把一张表的一半页面带进命令，另一半页面拥有的字段就会被报成 `invented` —— 那是命令组合问题，
不是等价性问题。默认组之所以覆盖 6 个页面，是因为 [PomodoroStrings] 与 [FocusStrings] 这两张表
由 Task 7 + Task 7b 共同填写（Ruling 23 不新建第三张表）。

三层比对（都是"集合相等"，不是条数相等）：

* strict —— 只把插值（`$x` / `${…}`）塌成一个"数值位"记号 `⟨$⟩`，**数字格式串原样保留**。
  这层报出"有多少条模板是整串逐字节搬进表里的"，以及"哪几条走了 Ruling 20 的拆分"：
  页面写 `strFormat("%.1f 秒", x)` 而表写 `"$seconds 秒"` 的那一处，在 strict 层就是唯一的不一致，
  它必须被点名而不是被归一化掉（评审 Task 7 时指出：旧脚本的归一化恰好把它显示成"也相同"）。
* [A] —— 再把数字格式串（`%d`、`%02d`、`%.1f` …）也塌成同一个记号，即"数值位在哪都算对齐"，
  比较两边的模板集合是否完全一致（missing = 页面有表里没有，invented = 反过来）。
* [B] —— 去掉所有数值位之后，只比中文文案本身（空格、标点、`·`、emoji 都算文案）。

已知边界（有意为之的局限，别当成 bug；命名与 `scripts/i18n_storage_boundary.py` 的「已知边界」段对齐）：
* 只认非三引号的双引号字面量；本阶段迁移的文件里没有三引号字符串。
* 相邻字符串拼接（`"a" + "b"`）算两条模板，所以中文表里不要写跨行拼接的**中文**值
  （Task 7 的 ZH 表没有；EN 表有，本脚本不读 EN 侧）。
* 注释里的中文按 Ruling 18 不算可迁移文案，脚本会剥掉 `//` 行注释与 `/* */` 块注释。
* 表侧字段名按"字面量前最近的一个 `字段 =`"归属，只为把报错指到具体字段，不参与等价性判定。
* **登记会遮蔽表侧与它同名的值**（终审 D-A/第 4 条点出、控制器裁决为"只记录不修"）：
  [apply_disclosures] 是在三层比对**之前**把登记的文本从两侧集合里 `pop` 掉的，它按**值**摘、
  不按"这条值来自哪个字段"摘。于是登记表中那六条**两侧同形**的共享词——`取消` / `确定` / `确认` /
  `删除` / `分` / `+⟨$⟩分`——是一个"免检名单"：任何人往任一中文表里**新写**一条与之逐字节相同的值，
  这条值就在**默认组**里被无声摘掉，既不参与 [strict] 也不参与 [A]/[B]，等价性检查看不见它。
  边界要说清楚三点：① 只有**整串逐字节相同**才被遮蔽，改成 `取消掉` / `确认。` 这类任何漂移都照旧
  报 `invented` 并退 1（终审修复波次的正对照实测过：改一个中文字 → 默认组 [A] 立即 NO、退 1）；
  ② 遮蔽的范围是"这条**值**"，不是"这个**字段**"——同字段的其他值仍被比；
  ③ 整枝组（`--branch`）里这六条是**双边命中**（页面确实有同一个词），遮蔽的语义就是"这条披露成立"，
  风险只在窄组：窄组没有供值的那张表，页面侧那一条本来就会被摘掉，表侧再冒出一条同形的就一并消失。
  同一机制的第二个实例：登记表里 `分` 有两条（Task 10 的双边 `分`/`分` 与只看表侧的 scoreUnit
  `None`/`分`），前者先 pop 掉表侧的 `分` 之后，后者在 `--branch` 组里恒报"（未用到）"——
  它不是过期，是被前一条遮蔽（这就是整枝组"82 用到 / 1 未用到"里那 1 条的来历）。
  阶段二若要收掉这个口子，形状是给登记加"字段名"限定（`table_field="CommonStrings.cancelButton"`）
  而不是继续按值 pop。

退出码：[A]、[B] 与 [strict] 残差三者都成立、且残差全部被"格式串拆分位点"或
**已披露残差表** [DISCLOSED_RESIDUALS] 解释掉，才返回 0。

为什么要有 DISCLOSED_RESIDUALS（Task 7b 第 1 轮审查第 7 条）：原先的退出条件只承认
"数字格式串拆分"这一种残差，于是任何**别的**已披露拆分（单位来自另一张表、两个数字进实参位、
把通用词上收到第三张表）都会让**每一种命令组合**恒退 1。恒红的闸门不会被读，
只会训练后面的 agent 去加 `--max-splits 99`。现在残差分三类，各自有明确出口：
1. 格式串拆分（页面字面量以 `%…` 开头）——自动识别，条数受 `--max-splits` 约束；
2. 已披露的非格式串拆分——必须在 DISCLOSED_RESIDUALS 里成对登记（页面原文 -> 表侧同形模板），
   登记不上或表侧找不到对应模板就退 1（白名单**会过期**，这是刻意的）；
3. 没登记的残差——退 1 并原样打出来，处置方式是"要么改代码，要么按 Ruling 20(d) 披露后再登记"。

`--disclose 页面原文=>表侧模板` 可以临时追加条目（用于新任务的自查），但**默认登记才是长期形态**：
它让下一个人在同一处看到"上一轮披露了什么"。
"""
import argparse
import io
import os
import re
import subprocess
import sys


def _force_utf8_streams():
    """把 stdout/stderr 钉在 UTF-8 上（终审 D-B：修 GBK 崩溃**这一类**，而不是把它写进文档当环境注）。

    本脚本打印中文与 `⟨$⟩` 这类非 ASCII 记号。**只要 stdout 不是真控制台**（管道给 `head`、
    重定向进日志文件、被 CI 的 step 收走），Python 就按 locale 编码写它——本机是 cp936/GBK，
    于是 `print(note)` 撞上 `⟨`（U+27E8）抛 UnicodeEncodeError、**闸门退 1**。症状是"等价性检查红了"，
    根因却是控制台编码，而一个会因环境而恒红的闸门正是本脚本 [DISCLOSED_RESIDUALS] 那段教义
    （"恒红的闸门不会被读"）要避免的形态。改成 UTF-8 后这条崩溃类不再存在，且与 CI 的
    Linux UTF-8 环境同形（同一个脚本、同一种字节）。

    `errors="replace"` 只可能碰到**孤立代理码位**——本脚本的输入都是按 `encoding="utf-8"` 严格读入的
    源文件与 `git show` 的 UTF-8 字节，产不出孤立代理；中文、`⟨$⟩`、emoji 一律原样输出。
    它不是归一化：strict 层的"不许把不一致藏起来"靠的是集合比较，与流的错误处理器无关。

    `reconfigure` 是 Python 3.7+ 的 `TextIOWrapper` 方法；更早的解释器、以及被测试框架换成没有该
    方法的假流（pytest 的 capture）都会走 hasattr 分支直接跳过——那只是退回护栏之前的行为。
    """
    for stream in (sys.stdout, sys.stderr):
        if hasattr(stream, "reconfigure"):
            try:
                stream.reconfigure(encoding="utf-8", errors="replace")
            except (OSError, ValueError, RuntimeError):
                pass


_force_utf8_streams()

SRC_ROOT = "composeApp/src/commonMain/kotlin/"
# 数值位记号：刻意用非汉字字符，否则"只剩插值"的排版串（`"${x}m"`）会被认成中文文案。
VALUE = "⟨$⟩"
CJK = re.compile(r"[一-鿿]")
FORMAT_SPEC = re.compile(r"%[-+ 0#]*[0-9]*(?:\.[0-9]+)?[a-zA-Z]")
FIELD_ASSIGN = re.compile(r"^\s*(\w+)\s*=", re.MULTILINE)

# 默认比对组：番茄钟 + 专注这一整组已迁页面（Task 7 三页 + Task 7b 三页），
# 每个页面取**自己迁移前**的那个 rev，表取工作区。两张表由这两批任务共同填写（Ruling 23），
# 所以页面必须一起带上，否则另一半的字段会被报成 invented。
TASK7_REV = "d336fb8"      # Task 7 迁移前的父提交
TASK7B_REV = "761b24e"     # Task 7b 迁移前的父提交（Ruling 23 的计划提交）
# 下面这些只在**整枝组**用到（见 BRANCH_PAGES），默认组一条都没有改动，所以老命令的行为不变。
TASK4_REV = "6f49951"      # Task 4（角色选择页）迁移前
TASK5_REV = "767711d"      # Task 5（设置页语言分区）迁移前：那一版还没有语言分区
TASK6_REV = "fdf8381"      # Task 6（公共控件）迁移前
TASK8_REV = "2304fa7"      # Task 8（孩子任务列表）迁移前
TASK9_REV = "20bc060"      # Task 9（孩子首页）迁移前
TASK10_HOME_REV = "0c34b79"   # Task 10 家长首页迁移前
TASK10_DASH_REV = "c77f91c"   # Task 10 数据看板迁移前（家长首页那一次已经把它迁走之前）
TASK11_REV = "ad6aa93"     # Task 11（设置页其余 64 条）迁移前
W1_REV = "4d28c8e"        # 阶段 2 W1（错题域）迁移前 = v3.1.0 发布提交
W2_REV = "5deed68"        # 阶段 2 W2（家长任务管理）迁移前 = W1 收口提交
W3_REV = "aae364e"        # 阶段 2 W3（家长积分/审批/奖励）迁移前 = W2 收口提交
W4_REV = "14c4d82"        # 阶段 2 W4（孩子商店/积分/抽奖/成就）迁移前 = W3 收口提交
W5_REV = "5d65128"        # 阶段 2 W5（习惯/目标/日程）迁移前 = W4 收口提交
W6_REV = "1e1e382"        # 阶段 2 W6（心愿/证明/报告/周报）迁移前 = W5 收口提交
W7_REV = "a5cd674"        # 阶段 2 W7（宠物页/预览卡/成长报告阶段名）迁移前 = W6 收口提交
DEFAULT_PAGES = [
    (TASK7_REV, "com/hstudy/app/ui/common/PomodoroTimer.kt"),
    (TASK7_REV, "com/hstudy/app/ui/focus/ChildFocusStatsScreen.kt"),
    (TASK7_REV, "com/hstudy/app/ui/focus/ChildFocusTrainingScreen.kt"),
    (TASK7B_REV, "com/hstudy/app/ui/focus/ChildPomodoroScreen.kt"),
    (TASK7B_REV, "com/hstudy/app/ui/focus/ChildMemoryTrainingScreen.kt"),
    (TASK7B_REV, "com/hstudy/app/ui/focus/ChildDailyPlanScreen.kt"),
]
DEFAULT_TABLES = [
    ("com/hstudy/app/i18n/PomodoroStrings.kt", "PomodoroStringsZh"),
    ("com/hstudy/app/i18n/FocusStrings.kt", "FocusStringsZh"),
]
WORKTREE = "WORKTREE"
REASON_FROM_CLI = "--disclose 追加的临时条目（没写进脚本，下一个人看不到它，也就不会被复验）"

# ---- 整枝级（branch-wide）比对组：本分支迁移过的**全部**页面 × **全部**文案表 ----
# 为什么需要它（终审第 9 条）：DISCLOSED_RESIDUALS 是在**各任务自己的窄组命令**里登记的，
# 当时那张表只有 18 条、且只有 6 条被默认组消耗，其余 12 条从来没有被任何一条持久命令复验过
# （docs/i18n-stage1-verification.md §6 只记了默认组与设置页那条）。
# 更糟的是原先的"成对登记缺一侧就失败"让**宽组天然不可能绿**：登记成页面单边的共享词
# （`取消` / `确定` / `确认` / `删除` / `分` / `+⟨$⟩分` —— 就是终审 D-A 第 4 条点名的那六条
# **两侧同形**登记）一旦把供值的那张表也带进来，表侧就成了 invented。
# 现在规则改成"两侧都在本次范围 → 两侧都要在；一侧不在本次范围 → 提示但不失败"
# （范围由 [_global_sets] 给出：整枝组的全部页面 / 全部表），于是这一条命令可以长期为绿。
#
# 登记表的当前实况读数（W7 Task 4 复算，改登记时必须同时改这里）：表里现在 **83 条**
# （9 条双侧成对 + 61 条只看页面 + 13 条只看表侧）。默认组碰到 **6** 条（3 条双侧命中 + 3 条
# "单边在范围"），另外 **77** 条默认组用不到；`--branch` 组碰到 **82** 条、只剩 **1** 条报
# "（未用到）"——那 1 条是 `None`/`分` 的 scoreUnit 登记被前一条 `分`/`分` 先 pop 掉而遮蔽的，
# 不是过期，见文件头「已知边界」的最后一 bullet。放宽的代价（窄组会遮蔽同形新值）也已写在那里。
# W6 那批 18 条 = 示例心愿 6 + 宠物阶段名 8（都只看页面）+ 周报通知正文 4（**只看表侧**：原文只在
# androidMain，闸门不读平台文件，理由逐条写在条目里）。
# W7 那批 9 条 = 账本 note 3（页面单边）+ 定值字面量 4（`积分不足，需要 10/5 分` 页面单边 2 +
# `🍎 喂养 (10分)` / `🎾 陪玩 (5分)` 定值→模板成对 2）+ 裸动词表侧 2（feedLabel/playLabel）。
#
# 每个页面取**自己那批任务迁移前**的 rev（`git log --format=%h -1 <迁走它的那次提交>^`），
# 同一页面被两批任务先后迁移的（ParentSettingsScreen）就带两个 rev，各取所需的一半原文。
# 出处：Task 4=6f49951、Task 5=767711d、Task 6=fdf8381、Task 7=d336fb8、Task 7b=761b24e、
# Task 8=2304fa7、Task 9=20bc060、Task 10=0c34b79/c77f91c、Task 11=ad6aa93、
# 终审 Ruling 41 的家长密码门=**c3bd94a 的父提交**（写这里时它恰好是 HEAD~1，HEAD 一动就不是了，
# 所以按"迁移那次提交的父提交"这个不变量表述；当前树上是 HEAD~4 = 41c3d39）。
# 这些常量现在全部是 CI 契约的一部分：android.yml 的等价性闸门带 fetch-depth: 0 且**阻塞**，
# 任何一个 rev 从历史里消失都会把构建变红（终审 D-A 的 rev 存在性核对逐条走过）。
BRANCH_GATE_REV = "41c3d39"   # 密码门迁移（Ruling 41，c3bd94a）之前的 AppNavGraph
BRANCH_PAGES = DEFAULT_PAGES + [
    (TASK4_REV, "com/hstudy/app/ui/role/RoleSelectScreen.kt"),
    (TASK5_REV, "com/hstudy/app/ui/parent/ParentSettingsScreen.kt"),
    (TASK11_REV, "com/hstudy/app/ui/parent/ParentSettingsScreen.kt"),
    (TASK6_REV, "com/hstudy/app/ui/common/CommonComponents.kt"),
    (TASK8_REV, "com/hstudy/app/ui/child/ChildTaskListScreen.kt"),
    (TASK9_REV, "com/hstudy/app/ui/child/ChildHomeScreen.kt"),
    (TASK10_HOME_REV, "com/hstudy/app/ui/parent/ParentHomeScreen.kt"),
    (TASK10_DASH_REV, "com/hstudy/app/ui/parent/ParentDashboardScreen.kt"),
    (BRANCH_GATE_REV, "com/hstudy/app/ui/navigation/AppNavGraph.kt"),
    # ---- 阶段 2 W1（错题域，W1_REV）----
    (W1_REV, "com/hstudy/app/ui/mistake/ChildMistakeListScreen.kt"),
    (W1_REV, "com/hstudy/app/ui/mistake/ChildMistakeDetailScreen.kt"),
    (W1_REV, "com/hstudy/app/ui/mistake/ChildMistakeAddScreen.kt"),
    (W1_REV, "com/hstudy/app/ui/mistake/ParentMistakeReviewScreen.kt"),
    (W1_REV, "com/hstudy/app/ui/mistake/SpacedRepetition.kt"),
    (W1_REV, "com/hstudy/app/ui/mistake/ReviewReminderReceiver.kt"),
    # ---- 阶段 2 W2（家长任务管理，W2_REV）----
    (W2_REV, "com/hstudy/app/ui/parent/ParentTaskManageScreen.kt"),
    (W2_REV, "com/hstudy/app/ui/parent/ParentTaskEditScreen.kt"),
    (W2_REV, "com/hstudy/app/ui/parent/ParentAssignTaskScreen.kt"),
    (W2_REV, "com/hstudy/app/ui/parent/ParentTaskTemplateScreen.kt"),
    # ---- 阶段 2 W3（家长积分/审批/奖励，W3_REV）----
    (W3_REV, "com/hstudy/app/ui/parent/ParentPointsScreen.kt"),
    (W3_REV, "com/hstudy/app/ui/parent/ParentApprovalScreen.kt"),
    (W3_REV, "com/hstudy/app/ui/parent/ParentRewardEditScreen.kt"),
    (W3_REV, "com/hstudy/app/ui/parent/ParentRewardManageScreen.kt"),
    # ---- 阶段 2 W4（孩子商店/积分/抽奖/成就，W4_REV）----
    (W4_REV, "com/hstudy/app/ui/child/ChildRewardStoreScreen.kt"),
    (W4_REV, "com/hstudy/app/ui/child/ChildLuckyDrawScreen.kt"),
    (W4_REV, "com/hstudy/app/ui/child/ChildPointsScreen.kt"),
    (W4_REV, "com/hstudy/app/ui/child/ChildAchievementsScreen.kt"),
    # ---- 阶段 2 W5（习惯/目标/日程，W5_REV）----
    (W5_REV, "com/hstudy/app/ui/target/ChildDailyTargetScreen.kt"),
    (W5_REV, "com/hstudy/app/ui/schedule/ParentScheduleManageScreen.kt"),
    (W5_REV, "com/hstudy/app/ui/schedule/ScheduleAlarmHelper.kt"),
    (W5_REV, "com/hstudy/app/ui/habit/ChildHabitChallengeScreen.kt"),
    (W5_REV, "com/hstudy/app/ui/habit/ChildGoalTrackerScreen.kt"),
    (W5_REV, "com/hstudy/app/ui/schedule/ChildScheduleTimelineScreen.kt"),
    # ---- 阶段 2 W6（心愿/证明/报告/周报，W6_REV）----
    # 7 个页面 + 1 个 commonMain 接收器。接收器沿用 W1 先例登记（ReviewReminderReceiver.kt 同样在
    # ui/ 下且只取词不渲染）；androidMain 的 platform/WeeklyReportReceiver.kt **不登记**：
    # 闸门只读 commonMain，平台文件里的正文四段（summary/encourage*/fallback）因此只能在
    # DISCLOSED_RESIDUALS 里按"表侧单边"披露，见下面 W6 那组登记条目。
    (W6_REV, "com/hstudy/app/ui/wish/ChildWishGoalScreen.kt"),
    (W6_REV, "com/hstudy/app/ui/wish/ParentWishGoalManageScreen.kt"),
    (W6_REV, "com/hstudy/app/ui/weekly/WeeklyTrackerScreen.kt"),
    (W6_REV, "com/hstudy/app/ui/report/GrowthReportScreen.kt"),
    (W6_REV, "com/hstudy/app/ui/proof/ParentProofReviewScreen.kt"),
    (W6_REV, "com/hstudy/app/ui/proof/ChildProofCaptureScreen.kt"),
    (W6_REV, "com/hstudy/app/ui/proof/PhotoViewerScreen.kt"),
    (W6_REV, "com/hstudy/app/ui/parent/WeeklyReportReceiver.kt"),
    # ---- 阶段 2 W7（宠物页/预览卡/成长报告阶段名，W7_REV）----
    # PetScenes.kt **不登记**：它是数据目录（场景名/价格/emoji 的 canonical 源），页内不渲染
    # 其中的字面量——场景名渲染走 petSceneCopy 注册表、由 PresetRegistryTest 钉覆盖。
    # GrowthReportScreen 已有 W6 条目（上面），再追加 W7 条目取它回接阶段名前的原文。
    (W7_REV, "com/hstudy/app/ui/pet/ChildPetScreen.kt"),
    (W7_REV, "com/hstudy/app/ui/pet/PetState.kt"),
    (W7_REV, "com/hstudy/app/ui/report/GrowthReportScreen.kt"),
]
BRANCH_TABLES = [
    ("com/hstudy/app/i18n/%s.kt" % n, "%sZh" % n) for n in (
        "RoleStrings", "SettingsStrings", "CommonStrings", "TaskListStrings",
        "PomodoroStrings", "FocusStrings", "ChildHomeStrings",
        "ParentHomeStrings", "ParentDashboardStrings", "ParentGateStrings",
        "MistakeStrings", "MistakeListStrings", "MistakeDetailStrings",
        "MistakeAddStrings", "ParentMistakeReviewStrings", "ReviewReminderStrings",
        "ParentTaskManageStrings", "ParentTaskEditStrings",
        "ParentAssignTaskStrings", "ParentTaskTemplateStrings",
        "ParentPointsStrings", "ParentApprovalStrings",
        "ParentRewardEditStrings", "ParentRewardManageStrings",
        "ChildRewardStoreStrings", "ChildLuckyDrawStrings",
        "ChildPointsStrings", "ChildAchievementsStrings",
        "ChildDailyTargetStrings", "ParentScheduleStrings", "ScheduleAlarmStrings",
        "ChildHabitChallengeStrings", "ChildGoalTrackerStrings", "ChildScheduleTimelineStrings",
        # ---- 阶段 2 W6（W6_REV）----
        "ChildWishGoalStrings", "ParentWishGoalStrings",
        "WeeklyTrackerStrings", "GrowthReportStrings",
        "ParentProofReviewStrings", "ChildProofCaptureStrings",
        "PhotoViewerStrings", "WeeklyReportStrings",
        # ---- 阶段 2 W7（W7_REV）----
        "PetStrings",
    )
]

# 已披露残差的登记处。每条 {"page": 页面原文或 None, "table": 表侧模板或 None, "reason": 披露出处}，
# page / table 至少给一个；两边的键都是**塌掉插值之后**的模板文本。
# 只有 Ruling 20(d) 已经在字段 KDoc 里点名披露过的位点才能进这里（任务报告不再是披露落点：`.superpowers/` 是 gitignore 的临时件，plan Ruling 35）。
# 为什么两边可以只写一边：抽取器只收"塌掉插值后仍含汉字"的字面量，
# 所以表侧 `"$minutes$unit"`（塌成 `⟨$⟩⟨$⟩`，一个汉字都没有）根本不会出现在表侧集合里——
# 那种位点只能登记页面侧那一条（单位来自另一张表），而"页面 `${if …}` 内部被子表达式吃掉的"
# 那一条只能登记表侧。两边都登记的那条若只剩一边，就是登记过期，退 1。
# 行号口径：W5 那批 reason 里的行号用迁移前 W5_REV（5d65128）坐标，W6 那批用 W6_REV（1e1e382）坐标，
# W7 那批用 W7_REV（a5cd674）坐标（同样是"迁移前"坐标，所以与 W5 同一套读法）；
# W1–W4 与更早的用写入时工作树坐标。
DISCLOSED_RESIDUALS = [
    {
        "page": "%s分" % VALUE, "table": None,
        "reason": "Task 7b Ruling 20 例外②：durationChip 的单位实参来自 CommonStrings.minuteLabel，"
                  "表侧那条 `$minutes$unit` 塌成 %s%s、不含汉字因此不入集合（见 PomodoroStrings 类注释）。"
                  "Task 11 复用同一条登记：ParentSettingsScreen.kt:275 的 `${cp.totalFocusMinutes}分` "
                  "走 SettingsStrings.progressFocusMinutesValue(n, common.minuteLabel)"
                  % (VALUE, VALUE),
    },
    {
        "page": "每专注 5 分钟 = +1 分（所有时长均有效）",
        "table": "每专注 %s 分钟 = +%s 分（所有时长均有效）" % (VALUE, VALUE),
        "reason": "Task 7b Ruling 20 例外③：两个数字进实参位，把同行两种 `分` 在结构上分槽"
                  "（见 PomodoroStrings 类注释）",
    },
    {
        "page": None, "table": "（超%s小时）" % VALUE,
        "reason": "Task 7b Ruling 20 例外①：宿主页 :145 的 `…${if (>=60) \"（超…小时）\" else \"\"}` "
                  "整段在页面的插值子表达式里，抽取器把它塌成一个数值位，"
                  "所以这条只在表侧可见（todayFocusSummary / overHoursNote 两个字段）",
    },
    # Task 7b 第 1 轮审查第 8 条：三条通用按钮词从 FocusStrings 上收到 CommonStrings，
    # 中文值逐字节不变，但本命令的表组只有 Pomodoro/Focus 两张表，所以只登记页面侧。
    {"page": "取消", "table": "取消",
     "reason": "审查第 8 条：上收为 CommonStrings.cancelButton（值未变，出处换了）。"
               "Task 11 复用同一条登记：ParentSettingsScreen.kt:192 与 :431 两处都取 common.cancelButton，"
               "本命令的表组没有 CommonStrings 所以只登记页面侧。"
               "终审 Ruling 41 之后登记成**两侧同形**：整枝组把 CommonStrings 带进表组，"
               "页面侧 AppNavGraph.kt:283（家长密码门）与表侧 cancelButton 都是『取消』，"
               "只登记单边会让宽组把表侧报成 invented（第 9 条点出的结构性恒红）"},
    {"page": "确定", "table": "确定",
     "reason": "终审 Ruling 41：家长密码门（AppNavGraph.kt:281）的提交按钮复用"
               "SettingsStrings.passwordOkButton（同一个中文值『确定』、同一个语义），不在新表里重开同值槽；"
               "表侧归 SettingsStrings，所以窄组（只带 ParentGateStrings）要靠这条双边登记说明它为什么只在页面侧。"
               "与 CommonStrings.confirmButton 的『确认』是**两个中文值**（差一个字、字节不同），"
               "两侧词形由 StringsCopyTest.settingsOkButtonIsNotTheCommonConfirmButton 钉住"},
    {"page": "确认", "table": "确认",
     "reason": "审查第 8 条：上收为 CommonStrings.confirmButton（值未变，出处换了）。"
               "终审第 9 条改成两侧同形登记，宽组（--branch）与窄组都能复验它"},
    {"page": "删除", "table": "删除",
     "reason": "审查第 8 条：上收为 CommonStrings.deleteContentDescription（值未变，出处换了）。终审第 9 条改成两侧同形登记"},
    # ---- Task 9（孩子首页）的 5 条残差 ----
    # 前四条是 Ruling 25 的存储边界：原文**不进文案表**，而是搬到 ChildHomeStrings.kt 里的
    # CanonicalChildHomeText 常量（该对象不属于 Zh/En 表，所以本命令的表侧集合里看不到它们）。
    # 登记成"页面单边"而不是"表侧同形"，是因为表侧取的是 `ChildHomeStringsZh` 变量段，
    # 常量对象在其外——于是残差条数恰好等于"落库原文条数"，多一条就说明有人把存储文案挪进了表。
    {"page": "今日学习表现优秀奖励", "table": None,
     "reason": "Task 9 Ruling 25：ChildHomeScreen.kt:317 传给 adjustPoints(points, note) 的 note 落库，"
               "值留在 CanonicalChildHomeText.EXCELLENT_BONUS_NOTE（逐字节不变、不本地化）"},
    {"page": "连续打卡%s天里程碑奖励" % VALUE, "table": None,
     "reason": "Task 9 Ruling 25：:478 的 adjustPoints note，值留在 CanonicalChildHomeText.streakMilestoneNote(days)"},
    {"page": "每周挑战奖励：本周打卡5天", "table": None,
     "reason": "Task 9 Ruling 25：:514 的 adjustPoints note，值留在 CanonicalChildHomeText.WEEKLY_CHALLENGE_NOTE"},
    {"page": "完成布置任务: %s" % VALUE, "table": None,
     "reason": "Task 9 Ruling 25：:586 跨行 checkIn(bizKey, description) 的 description 参与 "
               "PointRepository.kt:256 幂等判等，值留在 CanonicalChildHomeText.assignmentDescription(name)"},
    {"page": "+%s分" % VALUE, "table": "+%s分" % VALUE,
     "reason": "Task 9 跨表复用：ChildHomeScreen.kt:567 的 `+${a.points}分` 用 TaskListStrings.pointsEarnedLabel"
               "（同一中文值、同一语义 = 某件事值多少积分），本命令的表组只有 ChildHomeStrings，故只登记页面侧"},
    # ---- Task 10（家长首页 / 数据看板）的 2 条残差 ----
    # 家长首页那 59 条是逐字节整串搬进表的（strict 59/59，0 残差），只有仪表盘这一处需要登记。
    {"page": "分", "table": "分",
     "reason": "Task 10 Ruling 20 例外（与上面例外②同一类，单位来自另一张表）："
               "ParentDashboardScreen.kt:171 传给 ParentCompareRow(…, unit) 的 `分` 是**分钟**，"
               "迁移后由 CommonStrings.minuteLabel 供值；本命令的表组只有 ParentDashboardStrings，"
               "而那边没有这个值，所以只登记页面侧。评分单位那个 `分`（:142）是**另一个槽**，"
               "见下面那条表侧登记，两者不得合并（由 StringsCopyTest.parentDashboardScoreUnitIsNotPoints 钉住）"},
    {"page": None, "table": "分",
     "reason": "Task 10 Ruling 20 例外（与上面例外①同一类，只在表侧可见）："
               "ParentDashboardStrings.scoreUnit 是 :142 `${childScore.total}分` 的评分单位槽，"
               "页面写成 `strings.scoreValue(total, strings.scoreUnit)`，"
               "数值位与单位位塌成 `⟨$⟩⟨$⟩`（无汉字、不入页面集合），所以只有表侧有这一条"},
    # ---- Task 11（家长设置页剩余 64 条）----
    # 页面侧只有一条 `⟨$⟩分`（:275 `${cp.totalFocusMinutes}分`，单位来自 CommonStrings.minuteLabel），
    # 与上面 Task 7b 例外②同形，因此由那条登记复用；`取消`（:192 / :431）复用上面审查第 8 条那条。
    # 其余 62 条 strict 层逐字节相同、0 条 missing。
    # 下面五条是**只有表侧**的：语言分区那 6 个字段是 Task 5 为该功能**新写**的文案
    # （`git show 767711d` 的 ParentSettingsScreen 里根本没有语言分区，所以页面侧没有原文可比），
    # 由 Task 11 首次把这张表带进本命令时才暴露。`languageEn` = "English" 不含汉字、不入集合，
    # 所以新写的是 6 个字段里的这 5 条。理由同时写在 SettingsStrings.kt 类注释"新写而非搬来的五条"。
    {"page": None, "table": "🌐 语言",
     "reason": "Task 5 新写文案（迁移前该页没有语言分区，无页面原文可比）：SettingsStringsZh.languageTitle"},
    {"page": None, "table": "跟随系统",
     "reason": "Task 5 新写文案（同上）：SettingsStringsZh.languageSystem"},
    {"page": None, "table": "中文",
     "reason": "Task 5 新写文案 + 自称词（Ruling 6：两种 locale 下同值，见 EN_TABLE_ENDONYMS）："
               "SettingsStringsZh.languageZh"},
    {"page": None, "table": "选择界面显示语言，立即生效。",
     "reason": "Task 5 新写文案（同上）：SettingsStringsZh.languageNote"},
    {"page": None, "table": "部分页面仍在适配英文，会暂时保持中文显示。",
     "reason": "Task 5 新写文案（同上，且只在英文态渲染，spec §7）：SettingsStringsZh.partialTranslationNote"},
    # ---- 阶段 2 W1：Ruling 25 落库原文（页面单边；值不进表）----
    {"page": "主动回忆-做对", "table": None,
     "reason": "W1 Ruling 25：ChildMistakeDetailScreen.kt:259 reviewMistake(note) 落库，不本地化"},
    {"page": "主动回忆-没记住", "table": None,
     "reason": "W1 Ruling 25：ChildMistakeDetailScreen.kt:272 reviewMistake(note) 落库，不本地化"},
    {"page": "错题复习做对: %s" % VALUE, "table": None,
     "reason": "W1 Ruling 25：ChildMistakeDetailScreen.kt:260 submitApproval description 落库"},
    {"page": "错题复习: %s" % VALUE, "table": None,
     "reason": "W1 Ruling 25：ChildMistakeDetailScreen.kt:273 submitApproval description 落库"},
    {"page": "举一反三思考: %s" % VALUE, "table": None,
     "reason": "W1 Ruling 25：ChildMistakeDetailScreen.kt:339 submitApproval description 落库"},
    {"page": "间隔复习", "table": None,
     "reason": "W1 Ruling 25：ChildMistakeListScreen.kt:131 reviewMistake(note) 落库"},
    {"page": "复习错题：%s" % VALUE, "table": None,
     "reason": "W1 Ruling 25：ChildMistakeListScreen.kt:132 submitApproval description 落库"},
    {"page": "连续复习%s天里程碑奖励" % VALUE, "table": None,
     "reason": "W1 Ruling 25：ChildMistakeListScreen.kt:274 adjustPoints note 落库"},
    # ---- 阶段 2 W3：奖励分类 canonical 落库值（页面单边；值不进表，渲染走注册表）----
    # ParentRewardEditScreen 的 categoryOptions 与默认值"其他"是 RewardEntity.category 的落库值
    # （Ruling 25），留在页面；W0 注册表已收录全部 7 条译名，渲染包 localizedPresetText。
    # "其他"与 MistakeStrings.subjectOther 两侧同形自然配平，不需要披露；其余 6 条在此登记。
    {"page": "娱乐", "table": None,
     "reason": "W3 Ruling 25：ParentRewardEditScreen.kt:48 categoryOptions 落库值，不本地化；渲染走注册表"},
    {"page": "美食", "table": None,
     "reason": "W3 Ruling 25：同上（categoryOptions）"},
    {"page": "出行", "table": None,
     "reason": "W3 Ruling 25：同上（categoryOptions）"},
    {"page": "特权", "table": None,
     "reason": "W3 Ruling 25：同上（categoryOptions）"},
    {"page": "实物", "table": None,
     "reason": "W3 Ruling 25：同上（categoryOptions）"},
    {"page": "社交", "table": None,
     "reason": "W3 Ruling 25：同上（categoryOptions）"},
    # ---- 阶段 2 W4：账本 note 字面量（页面单边；值不进表，spec §1 非目标）----
    # adjustPoints / spendPoints 的 note 是落库数据，保持 canonical 中文原样（Ruling 25）。
    {"page": "每日幸运抽奖", "table": None,
     "reason": "W4 Ruling 25：ChildLuckyDrawScreen.kt:117 adjustPoints 账本 note，值留在 Canonical 层"},
    {"page": "兑换：⟨$⟩", "table": None,
     "reason": "W4 Ruling 25：ChildRewardStoreScreen.kt:231 spendPoints 账本 note，值留在 Canonical 层"},
    # ---- 阶段 2 W5：页面内预设 canonical 与账本/成就字面量（页面单边；值不进表，渲染走注册表/localizedPresetText）----
    # 日常目标标题 8 里只有 5 条是页面单边：「复习错题」「完成作业」「预习新课」与 PomodoroStrings 的
    # 规范专注目标同值同义、两侧同形自然配平（同 W3「其他」与 MistakeStrings.subjectOther 的先例），
    # 登记反而会把页面侧摘掉、让表侧报 invented，所以不登记——那三条继续由整枝组实际比对。
    # 复盘标签 6：落偏好 canonical（注册表 W5 组收录译文，渲染时本地化）。
    {"page": "专注2个番茄钟", "table": None, "reason": "W5 Ruling 25：ChildDailyTargetScreen.kt:23 dailyTargetDefs 标题，账本 description 用 canonical"},
    {"page": "阅读30分钟", "table": None, "reason": "W5 Ruling 25：同上（dailyTargetDefs）"},
    {"page": "英语听说训练", "table": None, "reason": "W5 Ruling 25：同上（dailyTargetDefs）"},
    {"page": "整理笔记", "table": None, "reason": "W5 Ruling 25：同上（dailyTargetDefs）"},
    {"page": "体育锻炼", "table": None, "reason": "W5 Ruling 25：同上（dailyTargetDefs）"},
    {"page": "今天很专注", "table": None, "reason": "W5 Ruling 25：ChildDailyTargetScreen.kt:310 tagOptions 落偏好 canonical"},
    {"page": "完成了难题", "table": None, "reason": "W5 Ruling 25：同上（tagOptions）"},
    {"page": "复习了错题", "table": None, "reason": "W5 Ruling 25：同上（tagOptions）"},
    {"page": "有点分心", "table": None, "reason": "W5 Ruling 25：同上（tagOptions）"},
    {"page": "时间不够用", "table": None, "reason": "W5 Ruling 25：同上（tagOptions）"},
    {"page": "明天会更好", "table": None, "reason": "W5 Ruling 25：同上（tagOptions）"},
    # 账本 description 模板（canonical，含目标标题/挑战标题）
    {"page": "完成每日目标：⟨$⟩", "table": None, "reason": "W5 Ruling 25：ChildDailyTargetScreen.kt:215 checkIn description（账本）"},
    {"page": "每日学习目标全部达成奖励", "table": None, "reason": "W5 Ruling 25：ChildDailyTargetScreen.kt:225 checkIn description（账本）"},
    {"page": "习惯打卡: ⟨$⟩", "table": None, "reason": "W5 Ruling 25：ChildHabitChallengeScreen.kt:87/:102 checkIn description（账本）"},
    # 日程示例标题 5：落库 canonical（注册表 W5 组收录译文）
    {"page": "起床啦", "table": None, "reason": "W5 Ruling 25：ParentScheduleManageScreen.kt:81 示例日程落库标题"},
    {"page": "吃早餐", "table": None, "reason": "W5 Ruling 25：同上（示例日程）"},
    {"page": "开始写作业", "table": None, "reason": "W5 Ruling 25：同上（示例日程）"},
    {"page": "阅读时间", "table": None, "reason": "W5 Ruling 25：同上（示例日程）"},
    {"page": "准备睡觉", "table": None, "reason": "W5 Ruling 25：同上（示例日程）"},
    # 成就解锁 canonical（tryUnlockAchievement 写库参数）
    {"page": "习惯达人", "table": None, "reason": "W5 Ruling 25：ChildHabitChallengeScreen.kt:94 tryUnlockAchievement 写库参数（achievementCopy canonical）"},
    {"page": "完成一个21天挑战", "table": None, "reason": "W5 Ruling 25：同上（成就描述）"},
    # ---- 阶段 2 W6：示例心愿 canonical（页面单边；值不进表，渲染走注册表）----
    # ParentWishGoalManageScreen 的「一键载入示例」把 3 条示例心愿**原样**交给 addGoal（name + description
    # 直接落库，Ruling 25），所以这 6 个值是 canonical、不进文案表；W6 Task 1 已把它们收进
    # PresetRegistry（W6 组 6 条），渲染时包 localizedPresetText —— 与 W3 奖励分类、W5 日程示例同一形态。
    # 登记成页面单边：表侧 ParentWishGoalStrings 只有 hint / nameLabel 里**整句**提到「一台新平板」，
    # 与这 6 个**整串**值不同形，所以表侧没有对应模板可配平（也不想为它造一条同值槽）。
    {"page": "一个小玩具", "table": None,
     "reason": "W6 Ruling 25：ParentWishGoalManageScreen 示例心愿落库值（T2 注册表已收录）"},
    {"page": "存够积分换一个喜欢的小玩具", "table": None,
     "reason": "W6 Ruling 25：ParentWishGoalManageScreen 示例心愿落库值（T2 注册表已收录）"},
    {"page": "一本新书", "table": None,
     "reason": "W6 Ruling 25：ParentWishGoalManageScreen 示例心愿落库值（T2 注册表已收录）"},
    {"page": "去书店选一本喜欢的书", "table": None,
     "reason": "W6 Ruling 25：ParentWishGoalManageScreen 示例心愿落库值（T2 注册表已收录）"},
    {"page": "一台新平板", "table": None,
     "reason": "W6 Ruling 25：ParentWishGoalManageScreen 示例心愿落库值（T2 注册表已收录）"},
    {"page": "存很久的大目标，加油！", "table": None,
     "reason": "W6 Ruling 25：ParentWishGoalManageScreen 示例心愿落库值（T2 注册表已收录）"},
    # ---- 阶段 2 W6：成长报告的宠物阶段名（页面单边；W7 归属，本波原样）----
    # GrowthReportScreen.petStageName 按累计积分在 8 个阶段名里分支，页面侧与分享文本都用它们；
    # 表侧 GrowthReportStrings 只有 reportText 的「🐾 学习宠物：」前缀（阶段名作为实参传入），
    # 所以这 8 条只有页面侧。W7 给出英文阶段名的同时要把它们从这里摘掉（白名单会过期）。
    {"page": "宠物蛋", "table": None,
     "reason": "W6 Ruling 3：GrowthReportScreen.petStageName 阶段名归 W7，本波原样（V-61）"},
    {"page": "刚破壳", "table": None,
     "reason": "W6 Ruling 3：GrowthReportScreen.petStageName 阶段名归 W7，本波原样（V-61）"},
    {"page": "小黄鸡", "table": None,
     "reason": "W6 Ruling 3：GrowthReportScreen.petStageName 阶段名归 W7，本波原样（V-61）"},
    {"page": "小猫咪", "table": None,
     "reason": "W6 Ruling 3：GrowthReportScreen.petStageName 阶段名归 W7，本波原样（V-61）"},
    {"page": "小狐狸", "table": None,
     "reason": "W6 Ruling 3：GrowthReportScreen.petStageName 阶段名归 W7，本波原样（V-61）"},
    {"page": "小狮子", "table": None,
     "reason": "W6 Ruling 3：GrowthReportScreen.petStageName 阶段名归 W7，本波原样（V-61）"},
    {"page": "独角兽", "table": None,
     "reason": "W6 Ruling 3：GrowthReportScreen.petStageName 阶段名归 W7，本波原样（V-61）"},
    {"page": "小神龙", "table": None,
     "reason": "W6 Ruling 3：GrowthReportScreen.petStageName 阶段名归 W7，本波原样（V-61）"},
    # ---- 阶段 2 W6：周报通知正文（**表侧单边**；原文在 androidMain，闸门读不到）----
    # 这四条是整张登记表里第二类"表侧唯一"：WeeklyReportStringsZh 的四个值在迁移前只存在于
    # androidMain/platform/WeeklyReportReceiver.kt（通知正文在**组合期之外**拼，见该表 KDoc:20-24），
    # 而本脚本的页面侧只读 commonMain（SRC_ROOT），平台文件按 plan Task 6「androidMain 不登记」不进来。
    # 于是一个"门"在这四条上是结构性的：commonMain 的 ui/parent/WeeklyReportReceiver.kt 只留下
    # title/body 两个常量（那两条与表侧逐字节相同、正常配对），正文四段没有任何 commonMain 原文可比。
    # 它们不是"新写文案"而是**逐字节搬运**：独立的三条（encourageLow/Good、fallback）与原文同一字节；
    # summary 在原文里是 buildString 两次 append 的拼接（:41-42），表里是一条整串——**拼接结果**逐字节
    # 相同，但模板形状不同（抽取器把两次 append 算两条），所以即使把平台文件登记成页面也对不上。
    # 登记形态沿用 Task 5 那五条"迁移前无页面原文可比"的表侧单边先例；验证落点是 WishCopyTest
    # 的两语整串钉死（weeklyReportSummaryPinsBothLanguages）+ 该表 KDoc 的逐行出处。
    {"page": None,
     "table": "📊 孩子本周学习周报：打卡⟨$⟩次 · 专注⟨$⟩分钟 · 复习⟨$⟩道错题 · 获得⟨$⟩积分",
     "reason": "W6 T5：正文主体原文在 androidMain/platform/WeeklyReportReceiver.kt:41-42（buildString 两次 "
               "append 的拼接），闸门只读 commonMain、平台文件不登记，故只在表侧可见；"
               "拼接结果逐字节相同，验证由 WishCopyTest.weeklyReportSummaryPinsBothLanguages 钉死"},
    {"page": None, "table": "。本周学习较少，多鼓励孩子加油！",
     "reason": "W6 T5：空周分支原文 androidMain/platform/WeeklyReportReceiver.kt:43（逐字节搬入），"
               "闸门读不到平台文件，故只在表侧可见"},
    {"page": None, "table": "。继续保持，稳步进步！",
     "reason": "W6 T5：非空周分支原文 androidMain/platform/WeeklyReportReceiver.kt:44（逐字节搬入），同上"},
    {"page": None, "table": "📊 孩子本周学习周报已生成，打开 App 查看详细报告。",
     "reason": "W6 T5：catch 兜底原文 androidMain/platform/WeeklyReportReceiver.kt:53（逐字节搬入），同上"},
    # ---- 阶段 2 W7：宠物页账本 note 与未参与模板塌缩的页面字面量 ----
    # Ruling 25：三条 spendPoints 账本 note 保持 canonical 中文原样、不进 PetStrings（V-65）。
    # 另四条是页面上的"定值数字"字面量（10/5 分、10分/5分按钮）：它们逐字节不进表，
    # 表侧对应的是带 ⟨$⟩ 数值位的函数型模板，strict 层塌缩不了 10/5 这类裸数字，
    # 所以只能在披露表里按"页面定值 → 表侧模板"或页面单边登记（同 W5「目标标题」的形态）。
    # reason 里的行号用迁移前 W7_REV（a5cd674）坐标。
    {"page": "喂养宠物", "table": None,
     "reason": "W7 Ruling 25：ChildPetScreen.kt:210 spendPoints(10, \"喂养宠物\") 账本 note，值留在 Canonical 层"},
    {"page": "陪宠物玩耍", "table": None,
     "reason": "W7 Ruling 25：ChildPetScreen.kt:225 spendPoints(5, \"陪宠物玩耍\") 账本 note，值留在 Canonical 层"},
    {"page": "购买宠物场景：⟨$⟩", "table": None,
     "reason": "W7 Ruling 25：ChildPetScreen.kt:361 spendPoints(scene.price, \"购买宠物场景：${scene.name}\") "
               "账本 note，内嵌 canonical 场景名，值留在 Canonical 层"},
    # 两条 snackbar 定值：表侧 notEnoughPoints 的 ⟨$⟩ 数值位与场景购买处（:358 的
    # "${scene.price}"）正常配对，所以这里只登记页面侧的两个定值，不能把表侧模板也摘掉。
    {"page": "积分不足，需要 10 分", "table": None,
     "reason": "W7：ChildPetScreen.kt:207 的定值字面量，迁移后走 PetStrings.notEnoughPoints(10)；"
               "strict 层不塌裸数字 10，故与带 ⟨$⟩ 位的表模板不同形（表侧模板由 :358 的动态版本配对）"},
    {"page": "积分不足，需要 5 分", "table": None,
     "reason": "W7：ChildPetScreen.kt:222 的定值字面量，迁移后走 PetStrings.notEnoughPoints(5)，同上"},
    {"page": "🍎 喂养 (10分)", "table": "🍎 喂养 (⟨$⟩分)",
     "reason": "W7：ChildPetScreen.kt:218 的定值按钮字面量 → PetStrings.feedButton(cost)；"
               "10 是裸数字、不被格式串塌缩，登记成页面定值与表侧模板成对"},
    {"page": "🎾 陪玩 (5分)", "table": "🎾 陪玩 (⟨$⟩分)",
     "reason": "W7：ChildPetScreen.kt:233 的定值按钮字面量 → PetStrings.playButton(cost)，同上"},
    # 两条表侧单边：裸动词槽没有被任何页面字面量渲染（按钮走带价格的 feedButton/playButton，
    # 见 PetStrings KDoc），页面侧没有同形原文，只能在表侧登记。
    {"page": None, "table": "喂养",
     "reason": "W7：PetStringsZh.feedLabel 裸动词槽（页面按钮走 feedButton，页内无同形原文），表侧单边"},
    {"page": None, "table": "陪玩",
     "reason": "W7：PetStringsZh.playLabel 裸动词槽（页面按钮走 playButton，页内无同形原文），表侧单边"},
]


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


def page_templates(page_specs):
    """页面侧：所有塌掉插值后仍含汉字的字面量 -> {模板: [(文件, 行, 原文是否数字格式串)]}。"""
    found = {}
    for rev, rel in page_specs:
        src = read(rev, rel)
        for offset, text in literals(src):
            if not CJK.search(text):
                continue
            lineno = src.count("\n", 0, offset) + 1
            # 以数字格式串开头的字面量 = 单位被写在格式串"里面"，正是 Ruling 20 拆分位点的形状
            found.setdefault(text, []).append((rel, lineno, bool(FORMAT_SPEC.match(text))))
    return found


def parse_page_spec(spec, default_rev):
    """`--page` 支持 `REV:路径` 与裸路径；裸路径需要 `--pages-rev`（页面必须取迁移前的原文）。"""
    head, sep, tail = spec.partition(":")
    if sep and "/" not in head and "\\" not in head:
        return (head, tail)
    if not default_rev:
        sys.exit("!! --page %r 没有带 rev，而 --pages-rev 也没给。"
                 "页面侧必须是**迁移前**的原文（`--pages-rev <rev>` 或写成 `<rev>:%s`），"
                 "拿工作区的已迁页面来比会把整张表报成 invented。" % (spec, spec))
    return (default_rev, spec)


def apply_disclosures(page, table, entries, scope_page=None, scope_table=None):
    """把已披露的残差从两侧集合里摘掉，并校验登记本身没有过期。

    返回 (是否成立, 说明行列表)。校验规则（终审第 9 条放宽了第 3 条，见下面粗体那段的括号）：
    * 两边都不在本次的集合里 -> 只提示，不判失败（命令可能只带了共享表的一部分页面）；
    * 只登记了一边的（page 或 table 为 None）：登记的那一边不在 -> 提示；在 -> 摘掉；
    * 两边都登记了却只找得到一边：**先看另一半是不是根本不在这次命令的范围里**。
      scope_page / scope_table 给出"整枝范围"（全部已迁页面 × 全部文案表，见 [_global_sets]）：
      缺失的那一侧若**在整枝范围里存在**，就只是"这次没带它"——提示 + 摘掉能找到的一侧，不失败；
      若连整枝范围里都不存在，那才是"披露与代码不再成对"（有人改了拆分形态却没改登记），**失败**。
      原先不看范围就一律失败，后果是共享词的成对登记让宽组天然不可能退 0
      （`取消` 登记在页面侧、CommonStrings 进了表组之后就成了 invented）。
      白名单仍然会过期：过期判定的参照系从"这次命令带了什么"换成"整个分支还有什么"。
    """
    notes = []
    ok = True
    for entry in entries:
        page_text = entry.get("page")
        table_text = entry.get("table")
        reason = entry.get("reason", "")
        on_page = page_text in page if page_text else False
        on_table = table_text in table if table_text else False
        if not on_page and not on_table:
            notes.append("    （未用到）%r / %r 都不在本次集合里——命令没带对应的页面或表，"
                         "不算失败，但这条披露没被复验：%s" % (page_text, table_text, reason))
            continue
        if page_text and table_text and on_page != on_table:
            # 只找到一侧：另一半是"这次命令没带"，还是"整枝范围里已经不存在了"？
            if not on_page:
                stale = scope_page is not None and page_text not in scope_page
            else:
                stale = scope_table is not None and table_text not in scope_table
            if stale:
                notes.append("    （登记过期）%r / %r 只找得到%s一边，另一侧在**整枝范围**里也不存在了"
                             "（那是有人改了拆分形态却没改登记）：%s"
                             % (page_text, table_text, "页面" if on_page else "表", reason))
                ok = False
                continue
            notes.append("    （单边在范围）%r / %r 这次只带到了%s那一侧，另一侧在整枝范围里存在、"
                         "只是这条命令没带它（宽窄组合天然如此，不算过期）：%s"
                         % (page_text, table_text, "页面" if on_page else "表", reason))
            if page_text and on_page:
                page.pop(page_text)
            if table_text and on_table:
                table.pop(table_text)
            continue
        if page_text and on_page:
            page.pop(page_text)
        if table_text and on_table:
            table.pop(table_text)
        notes.append("    （已披露）page=%r table=%r：%s" % (page_text, table_text, reason))
    return ok, notes


def _global_sets(tables_rev):
    """整枝范围：全部已迁页面（各取自己迁移前的原文）× 全部文案表。

    只用来判断"成对登记里缺失的那一侧到底是过期了还是这条命令没带它"（见 [apply_disclosures]
    的第 3 条），所以它**不参与**任何等价性判定，结果按 tables_rev 缓存一次。
    """
    return page_templates(BRANCH_PAGES), table_templates(tables_rev, BRANCH_TABLES)


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
    ap.add_argument("--pages-rev", default=None,
                    help="给**不带 rev 的** --page 用的 git rev；默认组的每个页面自带自己的 rev")
    ap.add_argument("--tables-rev", default=WORKTREE,
                    help="文案表取的位置（默认 WORKTREE）。**只对已经含全部四十三张表的 rev 可用**"
                         "（本分支上阶段 2 W7 提交起，含之后的 HEAD）：[_global_sets] 会按这个 rev 读满"
                         " BRANCH_TABLES 四十三张，早于它的 rev 直接 `git show … 失败` 退 1。"
                         "理由与实测退出码见文件头「哪些东西今天能钉回历史位置」那一段")
    ap.add_argument("--page", action="append", dest="pages",
                    help="可重复；相对 %s 的页面路径，也可写成 <rev>:路径（同一次里可以混用不同 rev）" % SRC_ROOT)
    ap.add_argument("--table", action="append", dest="tables", help="可重复；表文件#Zh 变量名")
    ap.add_argument("--max-splits", type=int, default=1,
                    help="允许的 Ruling 20 格式串拆分位点条数（默认 1 = Task 7 披露的那一处）；"
                         "出现第二条未披露的同形拆分时退出码变 1")
    ap.add_argument("--disclose", action="append", dest="disclose", default=[],
                    help="临时追加已披露残差：`页面原文` （表侧不再有整串）或 `页面原文=>表侧模板`；"
                         "长期形态是写进 DISCLOSED_RESIDUALS，好让下一个人看到上一轮披露了什么")
    ap.add_argument("--branch", action="store_true",
                    help="用**整枝级**的页面组与表组（本分支迁过的全部页面 × 全部文案表，"
                         "见 BRANCH_PAGES / BRANCH_TABLES）；与 --page/--table 同时给出时以后者为准。"
                         "docs/i18n-stage1-verification.md §6 记录的那条长期复验命令用的就是它")
    args = ap.parse_args()

    if args.pages:
        pages = [parse_page_spec(p, args.pages_rev) for p in args.pages]
    elif args.branch:
        pages = list(BRANCH_PAGES)
    else:
        pages = list(DEFAULT_PAGES)
    if args.tables:
        for t in args.tables:
            if "#" not in t:
                sys.exit("!! --table 需要 `<相对路径>#<Zh 变量名>` 两段形态，收到 %r。\n"
                         "   路径相对 %s（**不要**带 composeApp/src/commonMain/kotlin/ 前缀），例如\n"
                         "   --table com/hstudy/app/i18n/SettingsStrings.kt#SettingsStringsZh\n"
                         "   （docs/i18n-stage1-verification.md §6 原先记录的那条命令就是因为缺 #Variable 段、\n"
                         "    又带了完整前缀，直接 ValueError: not enough values to unpack。）"
                         % (t, SRC_ROOT))
        tables = [t.split("#") for t in args.tables]
    else:
        tables = list(BRANCH_TABLES if args.branch else DEFAULT_TABLES)

    page = page_templates(pages)
    table = table_templates(args.tables_rev, tables)
    scope_page, scope_table = _global_sets(args.tables_rev)

    entries = list(DISCLOSED_RESIDUALS)
    for spec in args.disclose:
        head, sep, tail = spec.partition("=>")
        if sep:
            entries.append({"page": head, "table": tail, "reason": REASON_FROM_CLI})
        else:
            entries.append({"page": head, "table": None, "reason": REASON_FROM_CLI})

    print("页面组（各页取自己的迁移前 rev）: %s" % ", ".join("%s:%s" % (r, os.path.basename(p)) for r, p in pages))
    print("文案表 rev=%s（%d 张表）" % (args.tables_rev, len(tables)))
    occ = {}
    for rev, rel in pages:
        src = read(rev, rel)
        hits = [t for _, t in literals(src) if CJK.search(t)]
        occ[os.path.basename(rel)] = (len(hits), len(set(hits)))
    print("每页（塌掉插值后的中文文案出现次数, 去重）: %s" % occ)
    print("distinct templates pages / tables        : %d / %d" % (len(page), len(table)))

    # 已披露的残差先摘掉（摘之前先校验登记本身没过期），三层比对都在摘掉之后进行。
    disclosed_ok, disclosure_notes = apply_disclosures(page, table, entries, scope_page, scope_table)
    print("\n[disclose] 已披露残差是否仍然成立: %s" % ("YES" if disclosed_ok else "NO（登记过期，见下面）"))
    for note in disclosure_notes:
        print(note)
    print("distinct templates after disclosures      : %d / %d" % (len(page), len(table)))

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

    # strict 残差是否**全部**由上面点名的拆分位点解释：解释不了的那几条就是"第二条拆分"或
    # "某个 token 既不在页面也不在表里"，原先只有 [A]/[B] 参与退出码，它们会被归一化掉、闸门看不见。
    split_page = {t for t in strict_only_page if any(fmt for _rel, _line, fmt in page[t])}
    unexplained_page = sorted(set(strict_only_page) - split_page)
    explained_table = {collapse_formats(t) for t in split_page}
    unexplained_table = sorted(set(strict_only_table) - explained_table)
    strict_clean = not unexplained_page and not unexplained_table
    splits_allowed = len(splits) <= args.max_splits
    print("[strict] 残差能否全部由拆分位点解释: %s（解释不了: 页面 %d / 表里 %d）"
          % ("YES" if strict_clean else "NO", len(unexplained_page), len(unexplained_table)))
    for text in unexplained_page:
        print("    strict 残差、非拆分位点（页面有）: %r  来自 %s"
              % (text, ["%s:%d" % site[:2] for site in page[text]]))
    for text in unexplained_table:
        print("    strict 残差、非拆分位点（表里有）: %r  字段 %s" % (text, table[text]))
    print("[strict] 拆分位点条数是否在上限内: %s（%d 条，--max-splits=%d）"
          % ("YES" if splits_allowed else "NO", len(splits), args.max_splits))

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
    exit_ok = (not missing and not invented and not diff and strict_clean and splits_allowed and disclosed_ok)
    print("\n退出码: %s（五个条件 %s 都成立才 0；残差可以是格式串拆分、也可以是 DISCLOSED_RESIDUALS "
          "里登记的已披露拆分，两者都不成立的那条就是要改的代码或要补的披露）"
          % (0 if exit_ok else 1,
             "[A] / [B] / [strict] 残差可解释 / 拆分条数在上限内 / 披露登记未过期"))
    return 0 if exit_ok else 1


def miss_raw(page, norm_text):
    """把归一化后的模板反查回页面原始模板（用于打印来源行号）。"""
    for raw in page:
        if collapse_formats(raw) == norm_text:
            return raw
    return None


if __name__ == "__main__":
    sys.exit(main())
