# HStudy v3.3.3 发布说明

**版本**：v3.3.3（versionCode 30303）
**日期**：2026-11-28
**状态**：本地发布物与精确版本运行时验收完成；共享状态发布动作等待用户明确确认。

## 数据完整性与并发

- 修复 Android Room 在 BundledSQLiteDriver 上调用 `withTransaction` 导致的真实崩溃，改用 Room KMP 的 `useWriterConnection` / `immediateTransaction`。
- 积分审批、打卡、撤回、消费、奖励兑换、心愿存取、宠物购买、模板应用与预设初始化统一走事务、CAS 或唯一键保护；中途失败整体回滚，避免半提交、重复入账和超额消费。
- 任务频次、审批状态与撤销使用稳定的周期键；历史重复数据保留，只建立唯一规范键，不删除账本历史。

## 任务、奖励、心愿与宠物

- 修复模板应用、愿望进度/退款、宠物场景购买和喂养的双击/重入窗口。
- 存入、扣款、进度、完成、退款与 ownership 写入要么一起成功，要么一起回滚。
-  UI 只在真实业务成功后更新；失败、余额不足或 ownership 尚未恢复时不会误报成功。

## 报表与历史

- 日/周/历史积分来自 awarded ledger，pending、rejected、penalty、refund 等不再污染收入口径。
- 撤销在原周期冲抵，不把 reversal 当作新收入；任务编辑或软删除不再改写历史得分。
- 完成率使用每日快照或明确标注的 legacy fallback，并夹紧到 0–100%。
- 周汇总、表格和每日摘要共享同一个周锚点；跨午夜时日期、查询和高亮一起刷新。

## Android 通知、导航与日程

- 修复周报 receiver 不重复排程、周末规则、一次性日程、精确闹钟失败回退和提醒取消范围。
- 深链路由在授权前安全保存，密码通过后只消费一次；冷启动、warm intent、Activity recreation 与返回栈行为一致。
- 通知权限、系统返回和密码门等待流程加入真实 instrumentation 覆盖。

## 家长密码安全

- 家长密码改为 PBKDF2-HMAC-SHA256（600000 次迭代）、随机 128-bit 盐、版本化格式和恒定时间比较。
- 旧明文在验证成功后原子迁移；DataStore 不再持久化明文，异常格式 fail-closed。

## 备份与家庭同步

- 备份、恢复和导入采用分阶段文件操作、SQLite header/schema/integrity/对象类型校验、失败回滚和启动恢复。
- 控制配置导入要求显式 `allowEmpty`；任务/奖励替换与日程解绑同事务，空文件或畸形 DTO 不会清空现有数据。
- 分享结果区分 Presented、Completed、Cancelled、Failed，UI 不再把“已展示”误报为“已完成”。

## 性能

- 积分列表分组和错题列表派生统计改为单遍计算，消除每条记录的重复 Calendar 创建、日期格式化和列表复制。
- Pixel 5 / API 30 六组真实 Room benchmark 全部低于门槛：最高 p95 99.33ms，最大 retained-heap delta 6,016 bytes（阈值 100ms / 64MiB）。

## 验证证据

- JVM：404 tests，0 failures，0 errors，0 skipped。
- Firebase Test Lab：精确 v3.3.3 / 30303 版本矩阵 `matrix-1heuy9sin13r8` 在 Pixel 5 / API 30 通过 212/212；六组 benchmark 均为 `NOT_REPRODUCED`。Robo 真实崩溃复现后重试通过。
- Android lint、analytics/i18n/duplicate-API 静态闸门与自测、iOS 三目标 Kotlin 编译通过。
- 版本闸门：Android、Info.plist、Xcode 四处版本戳统一为 3.3.3 / 30303。
- Android Test Orchestrator 在 Firebase 运行时仍未激活；全部测试在无进程隔离条件下稳定通过。

## 已知限制

- iOS 原生运行时、API 33+ 实体设备通知/精确闹钟行为、真实系统分享投递仍未验证。
- 本地 release 签名环境变量未设置，因此本地不声称已生成签名 APK；现有 GitHub Actions release workflow 会在获得 push/tag 授权后使用仓库 secrets 完成签名与密钥轮换。
- 不提供实时设备发现/同步承诺；家庭配置同步仍为手动 JSON 导出、系统分享和手动导入。
- 原生 iOS 上架需要 Apple Developer Program 与相应证书。

## 升级提示

- 已有 v2（debug 签名）或 v3.x 用户可按发布 workflow 的签名轮换策略覆盖升级；Android 8.x 且已安装 v3.0–v3.2.0 的设备如需更换签名，请先导出备份，再卸载、安装、恢复。
- 数据库 schema 保持 v14；本版本不删除历史数据。
