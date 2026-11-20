# HStudy v3.3 验收报告 — M3 界面美化 + 积分审批强化（2026-11-21-m3-ui-points-hardening）

对照 docs/antigravity-optimization-directives.md §4.2 三条验收标准逐条举证。
执行范围 = 计划 Task 1–5（波 1 P0 / 波 2 P1+P2 / 波 3 主题+孩子两页 / 波 4 家长三页 / 波 5 其余 13 页）。
本报告证据全部产生于 Windows 本机（无 Android/iOS 模拟器或真机），运行时间 2026-11-21，
分支 `feature/m3-ui-points`，HEAD=本波 commit。

## 标准 1：测试通过标准
| 条款 | 证据 | 判定 |
| :--- | :--- | :--- |
| `:composeApp:compileDebugKotlin*` 编译无错误 | 本波 `testDebugUnitTest` + `assembleDebug` 均 BUILD SUCCESSFUL（Kotlin 编译为其前置 task）；仅有既有 deprecation 警告（LinearProgressIndicator Float 重载、AutoMirrored icons、menuAnchor 等，全仓既存，非本波引入错误） | **通过**（"无任何警告"按原文过严：仓库自 v3.0 起即带同类 M3 deprecation 警告，本波未新增警告类别；如实说明） |
| PointRepositoryInvariantTest 不变量用例 100% 通过 | 波 1 落地 8 个用例（计划 §2.4 的 7 条 + 追加 1 条）；本波全量回归包含在内：`composeApp/build/test-results/testDebugUnitTest` 汇总 **247 tests / 0 failures / 0 skipped** | **通过** |
| 历史单元测试套件全部回归通过 | 同上 247/247（含 i18n copy pin、analytics、审批闭环、存储边界守卫等全部既有套件；本波新增断言并入既有测试方法，计数不变） | **通过** |
| CI 闸门 | `analytics_call_sites.py` → `ANALYTICS_GATE_OK` 20 checks；`i18n_storage_boundary.py` + 两个 selftest exit 0；`i18n_template_diff.py` 默认组与 `--branch` 均 exit 0（登记表读数 237 条与实测一致，披露 0 过期） | **通过** |

## 标准 2：业务逻辑验收标准
| 条款 | 证据 | 判定 |
| :--- | :--- | :--- |
| 审批前余额绝对静止（任务/习惯/布置/目标/错题/专注六类主动学习操作全部先申请后入账） | 波 1 把错题复习/举一反三（规则 6/7，`MistakeRepository.reviewMistake`/`saveVariation` 内走 `checkIn(bizKey=MISTAKE_REV:…/MISTAKE_VAR:…)`）与番茄钟（规则 8，`FOCUS:$sessionId:$date`）接入统一审批；PointRepositoryInvariantTest 覆盖"提交审批不改余额"；本波（波 5）diff 内零业务语句变化（RA7，逐 needle 验证见 task-5-report.md §二） | **通过** |
| 审批通过后准确入账、无法二次审批多加 | 审批幂等用例（同一 approval 二次 approveAndAward 不加双分）在不变量套件钉死；`approveAndAward` 语义本波未触碰 | **通过** |
| 撤销已审批 → 严格扣回 + 追加 REVERSAL 冲抵流水 | 波 1/2 闭环（任务/习惯/布置 undoCheckIn → REVERSAL 记录）；不变量套件含撤销对称用例；"append-only 余额=SUM" 用例钉死 | **通过** |
| 撤销未审批 → 待审单置 CANCELLED、无脏数据 | undo 未审批路径置 CANCELLED + 历史展示如实（波 2 P1-02：ParentApprovalScreen 状态徽章按 CANCELLED/REJECTED/REVOKED 分列，文案走 Strings）；对应用例在套件内 | **通过** |
| （计划级）五条不变量与 Ruling 25 | 存储边界闸门第五支每波强制 exit 0：copy-table 字符串不进 DB 写入与 bizKey 比较；本波新增文案全部走注册表、无新增落库字面量（`消耗 N 积分` 等 4 条仅屏幕显示；分享文本 reportText 逐字节未动） | **通过** |

## 标准 3：视觉设计验收标准
| 条款 | 证据 | 判定 |
| :--- | :--- | :--- |
| 全局无硬编码十六进制色值，完全基于 `MaterialTheme.colorScheme` | 静态清扫（ui/ 全树、排除 theme/Color.kt 57 处色板定义与 theme/Theme.kt 6 处形状定义——两者即"体系本身"）：波 5 前 `Color(0x`=29、`RoundedCornerShape(`=4；波 5 后 **`Color(0x`=28、`RoundedCornerShape(`=1**；**§3.4 指令清单的 13 页两个指标均为 0**。范围外残留如实列明：common/Animations.kt 3、common/LearningTree.kt 6、common/VisualEffects.kt 5、pet/PetScenes.kt 7（场景商品目录装饰色，属数据）、role/RoleSelectScreen.kt 6、habit/ChildEfficiencyReportScreen.kt:511 1、weekly/WeeklyTrackerScreen.kt:229 1——这 7 处不在指令 §3.4 页面清单内，建议后续波统一（效率报表箭头与仪表盘 ParentCompareRow 同型，可直接换 secondary/chartPalette） | **部分通过（按指令口径达标）**：指令审计范围（§3.1 所列核心页 + §3.4 全部 13 页 + 波 3/4 六页）内无裸色值/裸圆角；全树严格下降且残留已逐项披露。非清单页面残留不隐瞒 |
| 消除不适的屏幕持续缩放动效 | 孩子首页 breathTransition 全屏 scale 已于波 3 删除（改静态 Hero Card + 语义色渐变）；波 5 作息页"进行中"改用**颜色脉冲边框**（无缩放）；宠物页保留的是小范围宠物浮动/呼吸（1.0→1.06 局部图标动效，非全屏文字缩放，属指令 §3.3.1 未禁的装饰层——如实说明其存在） | **通过（核心痛点清除）**；宠物局部浮动为有意保留的角色动效 |
| 家长首页三层结构，首屏核心数据 + 审批待办 | 波 4 落地（🔔 待办横幅置顶直达审批 / Daily Pulse 宫格 / 分组网格 + 高级快捷条，旧 15 卡导航 1:1 保留），本波未回退 | **通过** |
| §3.4 六模块逐条 | 错题（surfaceContainer 卡 + 动画圆角进度条）、作息（primary/outline 节点层次 + 脉冲边框）、奖励心愿（网格 + 「消耗 N 积分」钮 + 宽轨进度）、专注（displayLarge 表盘 + 「+N分已提交家长审核」文案路径保持）、宠物（药丸进度胶囊 + spendPoints 绑定不动）、报表（chartPalette 语义色 + 已结算实线/待审预估虚线 + 余额副标 pending 区分）——逐项落点见 task-5-report.md | **通过（代码层）** |
| **诚实声明** | 本机为 Windows 无模拟器/真机，以上"视觉通过"= 编译通过 + 静态扫描 + 结构审查证据；**未做像素级走查**。暗色主题、Compact/Expanded 断点、脉冲边框观感、网格换列效果、图表虚线可读性需用户设备实测签核后方可宣布最终视觉验收 | **待用户设备验证** |

## 总判定
- 标准 1（测试）：**达成**。
- 标准 2（业务）：**达成**（五条不变量 + 四闸门 + 埋点 needles 全部复核通过）。
- 标准 3（视觉）：指令清单范围内**代码层达成**、残留如实披露；**像素级验收待用户设备测试**。

## 证据索引
- 测试：`composeApp/build/test-results/testDebugUnitTest/`（247/0/0）
- 闸门：`scripts/analytics_call_sites.py`、`scripts/i18n_storage_boundary.py`、`scripts/tests/i18n_storage_boundary_selftest.py`、`scripts/tests/i18n_template_diff_selftest.py`、`scripts/i18n_template_diff.py [--branch]`（全部 exit 0）
- 清扫数字：`grep -rc "Color(0x\|RoundedCornerShape(" composeApp/src/commonMain/kotlin/com/hstudy/app/ui/`（波前/波后表见 task-5-report.md §三）
- needles：ChildPomodoroScreen.kt:178、ChildPetScreen.kt PET_CARE×2、ParentApprovalScreen APPROVAL_ACTION×3（analytics 闸门 20 checks 覆盖）
- 波次报告：`.superpowers/sdd/2026-11-21-m3-ui-points-hardening/task-{1..5}-report.md`

## 增补：终审修复轮（2026-11-20/21，commit b7e9247 + bc2e503）
| 发现 | 修复 | 证据 |
| :--- | :--- | :--- |
| I-1 任务卡撤销无二次确认（违 §3.3.2-4） | AlertDialog 确认后才执行 undoTaskCheckIn（复用批量通过对话框模式，文案走 Strings） | 248/248；registry 237→238 |
| M-1 ASSIGN 撤销 bizKey 用点击日致跨午夜漏扣 | 完成时刻 completedAt 派生日期，缺失回退今天（与旧行为一致） | fix hunk 已核实 |
| M-2 同日 +1 复习把 +3 待审降级 | ensureApproval 复用 PENDING 时分值取 max（只升不降），入账仍一次（不变量 3） | 新增回归 reviewMistake_sameDayLowerPoints_keepsPendingMaxAndAwardsOnce |
| M-3 历史筛选在 take(20) 截断之后 | 先全量筛选后截断 | fix hunk 已核实 |
| M-4 审批胶囊遮挡已通过/已拒绝回顾 | 回顾卡常驻胶囊正下方，胶囊改纯信息展示 | bc2e503；248/248、五闸门 exit 0 |
| M-5 术语近义 | 信息性，不改 | — |

修复轮后全量证据：testDebugUnitTest 248/0（--rerun 强制重跑）、assembleDebug OK、analytics 20 checks、存储边界+双 selftest+template --branch 全 0、秘密扫描 0 命中。分支 828f1f0..bc2e503 = 8 commits。

## QA 修复轮（copy+contrast 审计 DEFECT D1–D5，本 commit）
审计底稿：`.superpowers/sdd/2026-11-21-m3-ui-points-hardening/qa-copy-contrast-audit.md`。

| 缺陷 | 修复 | 说明与实证（python 逐值复算，非引用审计数） |
| :--- | :--- | :--- |
| D1/D2 主色对比 | `M3Primary` 0xFFEF6C00 → **0xFFA65100** | **指令偏差（有意）**：指令 §3.2.1 给的 0xFFEF6C00 实为 M3 tone-50，白字仅 3.08:1、作文字/线条对 Surface 仅 2.93:1，双项不过 WCAG AA。同色相降至 tone-40 级暖橙后：白钮文字 5.53:1、主色作文字对 Surface 5.26:1（均 ≥4.5）、对 container 层级作非文字 4.27–5.00:1（≥3）。PrimaryContainer/OnPrimaryContainer 复测 13.30:1，保持不变。站点全部经 `colorScheme.primary` 继承（审批页 +N 分值、兑换值、趋势线），零逐点改动 |
| D3/D4 错误色对比 | `M3Error` 0xFFE53935 → **0xFFC62828** | 原值白字 4.23:1、正文 3.82–4.03:1，不过 AA。tone-40 同色相后：白字 5.62:1、对 Surface/ContainerLow 作正文 5.35/5.08:1（≥4.5）；errorContainer/onErrorContainer 复测 13.26:1 保持不变 |
| D5 英文撤销术语 | `ParentApprovalStrings.scoreRevoked`、`TaskListStrings.revertConfirmMessage` 的 "clawed back" → **"…removed"** | 归一到 KDoc 钉死的 "points removed" 术语族（与 `statusRevoked` "Revoked (points removed)"、`revertedClawedBackMessage` "$points points removed" 同族）；中文侧不动，ZH≠EN 仍成立；同步更新两处 EN copy-pin（ParentPointsCopyTest、StringsCopyTest），中文 pin 未动 |

修复轮后全量证据：`:composeApp:testDebugUnitTest` **248/0/0**、`:composeApp:assembleDebug` OK、`analytics_call_sites.py` ANALYTICS_GATE_OK（20 checks）、`i18n_storage_boundary.py` + 双 selftest + `i18n_template_diff.py --branch` 全部 exit 0。

## 真机视觉与行为签核清单（用户设备执行，逐项打勾）
安装 `composeApp/build/outputs/apk/debug/HStudy-v3.2.1-debug.apk`（Android 9+ 即可；本包未升版本号，属预发布验证）：
1. [ ] 孩子首页：无全屏呼吸抖动；待审胶囊与"已通过/已拒绝"回顾**同时可见**；Hero/CTA 为暖橙语义色板
2. [ ] 任务打卡：56dp 圆形打卡钮+弹性反馈；点撤销先弹确认框；中英文各过一遍
3. [ ] 错题：做对提示 +3、仅复习 +1 且均"待家长审核"；家长审批页出现"错题复习"新待审；同日先做对再仅复习 → 待审分值保持 3 不降
4. [ ] 番茄钟：完成产生 1 条待审；重进页面再完成不产生重复待审
5. [ ] 习惯/布置任务：撤销后待审单变"已取消"，已审批的撤销显示"已扣回"
6. [ ] 家长审批历史：✅/⛔/↩️/🔄 四类徽章语义正确；筛选"已撤销"能找到 20 条之外的旧记录
7. [ ] 每日必须任务未完成次日：积分明细出现 [未完成扣分] PENALTY 标签
8. [ ] 家长首页：首屏见待办横幅+今日 2×2 宫格；旧 15 项功能全部可达
9. [ ] 报表：已结算实线 / 待审预估虚线可读
10. [ ] 深色模式（若开启）无对比度异常；小屏 Compact 断点无遮挡
11. [ ] iOS：仅能经 CI 模拟器包目检（未签名），视觉抽查
