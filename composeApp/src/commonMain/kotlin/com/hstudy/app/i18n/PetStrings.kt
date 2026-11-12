package com.hstudy.app.i18n

/**
 * 学习宠物文案表（阶段 2 W7）：孩子端宠物页（ui/pet/ChildPetScreen.kt）与首页预览卡
 * （ui/pet/PetState.kt 的 `PetPreviewCard`）共用。迁移前两处 UI 字面量全部入表，
 * ZH 逐字节照录（行尾注释是迁移前坐标），页面逻辑零变化。
 *
 * **存储边界（Ruling 25，本页最敏感的地方）**：宠物页有三条 `spendPoints(…)` 的账本 note
 * 保持 canonical 中文原样、**不进本表**——`"喂养宠物"`（ChildPetScreen :210）、
 * `"陪宠物玩耍"`（:225）、`"购买宠物场景：${scene.name}"`（:361，内嵌 canonical 场景名）。
 * 它们是 point_records 的历史数据，由 W7 T4 的等价闸门登记披露；本表只持有 UI 显示文案。
 * `ownedPetScenes` 只存 `scene.id`，场景名与阶段名都是显示目录、不入库。
 *
 * **场景名/阶段名不在本表**：场景名走 W0 的 [petSceneCopy]（键 = PetScene.id，渲染点
 * `petSceneCopy[displayLanguage]?.get(scene.id) ?: scene.name`）；阶段名走 W7 T1 的
 * [localizedPetStageName]（键控表 [petStageCopy]，索引对齐 ui.pet.petStages）。
 * `petStages`/`petScenes` 是 canonical 目录（数据源），保持原样不动；页面只做渲染期取词。
 *
 * **`petStatusMessage` 签名形变**：预览卡的状态语函数由 4 参改为 5 参（增加
 * `strings: PetStrings`），when 分支改用 [statusCritical]…[statusNewDay] 与 [statusProgress]；
 * 调用点只有 `PetPreviewCard` 一处，判定条件（饱食度/心情/今日完成度）不变。
 *
 * **[moodCritical] 与 [statusCritical] 是两组独立取值**：宠物页心情气泡（ChildPetScreen :91-96）
 * 用自己的版本，预览卡状态语（PetState :67-74）用 status* 组。页面版与预览版的 critical 措辞
 * 不同（页面强调"喂养/陪玩救救我"），moodProgress/moodNewDay 也各多一截（"我陪着你"/
 * "让我开心一下吧"）；moodHungry/moodBored/moodAllDone 的源串两组恰好逐字节相同，
 * 仍保留独立槽位（两处可独立演进）。两组都进表，英文按各自源串的强弱分别给出。
 *
 * [feedLabel]/[playLabel] 是裸动词（页面按钮走带价格的 [feedButton]/[playButton]）。
 */
data class PetStrings(
    val title: String,
    val careTitle: String,
    val fullnessLabel: String,
    val moodLabel: String,
    val feedLabel: String,
    val playLabel: String,
    val feedButton: (cost: Int) -> String,
    val playButton: (cost: Int) -> String,
    val notEnoughPoints: (cost: Int) -> String,
    val fedMessage: String,
    val playedMessage: String,
    val careTip: String,
    val maxLevel: String,
    val progressTitle: String,
    val nextStage: (points: Int, emoji: String, name: String) -> String,
    val stageUnlock: (points: Int) -> String,
    val currentBadge: String,
    val growthPathTitle: String,
    val levelLine: (level: Int, lifetime: Int) -> String,
    val criticalAll: String,
    val criticalHungry: String,
    val criticalBored: String,
    val shopTitle: String,
    val shopHint: String,
    val defaultScene: String,
    val scenePrice: (price: Int) -> String,
    val equippedBadge: String,
    val equipButton: String,
    val buyButton: String,
    val purchaseSuccess: (name: String) -> String,
    val previewTitle: (name: String) -> String,
    val fullnessShort: (value: Int) -> String,
    val moodShort: (value: Int) -> String,
    val previewTip: String,
    val statusCritical: String,
    val statusHungry: String,
    val statusBored: String,
    val statusAllDone: String,
    val statusProgress: (done: Int) -> String,
    val statusNewDay: String,
    val moodCritical: String,
    val moodHungry: String,
    val moodBored: String,
    val moodAllDone: String,
    val moodProgress: (done: Int) -> String,
    val moodNewDay: String,
)

val PetStringsZh = PetStrings(
    title = "🐾 我的学习宠物",                                                    // ChildPetScreen :113
    careTitle = "❤️ 照顾宠物",                                                    // :196
    fullnessLabel = "🍎 饱食度",                                                  // :199
    moodLabel = "😊 心情",                                                       // :201
    feedLabel = "喂养",                                                          // :218（裸动词，按钮见 feedButton）
    playLabel = "陪玩",                                                          // :233（裸动词，按钮见 playButton）
    feedButton = { cost -> "🍎 喂养 (${cost}分)" },                               // :218
    playButton = { cost -> "🎾 陪玩 (${cost}分)" },                               // :233
    notEnoughPoints = { cost -> "积分不足，需要 $cost 分" },                        // :207/:222/:358
    fedMessage = "🍎 宠物吃得很开心！",                                            // :213
    playedMessage = "🎾 宠物玩得很开心！",                                         // :228
    careTip = "提示：宠物每天没人照顾会饿、会无聊哦，记得常来看看它～",                  // :235
    maxLevel = "👑 已满级！你是最棒的学习小主人！",                                  // :246
    progressTitle = "进化进度",                                                   // :254
    nextStage = { points, emoji, name -> "再获得 $points 分，就能进化成 $emoji $name 啦！" }, // :263
    stageUnlock = { points -> "$points 分解锁" },                                 // :303
    currentBadge = "当前",                                                       // :307
    growthPathTitle = "🌟 成长之路",                                              // :272
    levelLine = { level, lifetime -> "Lv.$level · 累计获得 $lifetime 分" },        // :156
    criticalAll = "宠物又饿又无聊，快要昏迷了！快用积分喂养和陪玩救它！",                // :171
    criticalHungry = "宠物饿坏了！快用积分喂养它！",                                // :172
    criticalBored = "宠物非常无聊！快用积分陪它玩！",                                // :173
    shopTitle = "🛍️ 宠物装饰商店",                                               // :319
    shopHint = "用积分给宠物换一个漂亮的家吧！",                                     // :322
    defaultScene = "默认场景",                                                    // :345
    scenePrice = { price -> "$price 积分" },                                     // :345
    equippedBadge = "已装扮",                                                    // :349
    equipButton = "装扮",                                                        // :354
    buyButton = "购买",                                                          // :369
    purchaseSuccess = { name -> "🎉 购买成功，已装扮$name！" },                     // :364
    previewTitle = { name -> "我的学习宠物 · $name" },                             // PetState :112
    fullnessShort = { value -> "🍖 饱食 $value" },                               // PetState :117
    moodShort = { value -> "😊 心情 $value" },                                   // PetState :119
    previewTip = "💡 复习错题、专注学习能让小宠物心情变好哦～",                        // PetState :122
    statusCritical = "😵 我快支撑不住了…快用积分救救我（喂养/陪玩）！",                // PetState :68
    statusHungry = "🥺 我好饿…快用积分喂喂我吧！",                                  // PetState :69
    statusBored = "😔 我好无聊…花点积分陪我玩一会儿吧！",                            // PetState :70
    statusAllDone = "🎉 今天任务全部完成啦，我为你骄傲！",                            // PetState :71
    statusProgress = { done -> "💪 已经完成 $done 个任务了，继续加油！" },           // PetState :72
    statusNewDay = "🌞 新的一天开始啦，快去完成任务吧！",                             // PetState :73
    moodCritical = "😵 我快支撑不住了…快用积分喂养/陪玩救救我！",                     // ChildPetScreen :91
    moodHungry = "🥺 我好饿…快用积分喂喂我吧！",                                   // :92
    moodBored = "😔 我好无聊…花点积分陪我玩一会儿吧！",                              // :93
    moodAllDone = "🎉 今天任务全部完成啦，我为你骄傲！",                              // :94
    moodProgress = { done -> "💪 已经完成 $done 个任务了，继续加油，我陪着你！" },     // :95
    moodNewDay = "🌞 新的一天开始啦，快去完成任务，让我开心一下吧！",                   // :96
)

val PetStringsEn = PetStrings(
    title = "🐾 My study pet",
    careTitle = "❤️ Take care of your pet",
    fullnessLabel = "🍎 Fullness",
    moodLabel = "😊 Mood",
    feedLabel = "Feed",
    playLabel = "Play",
    feedButton = { cost -> "🍎 Feed ($cost ${pointsWord(cost)})" },
    playButton = { cost -> "🎾 Play ($cost ${pointsWord(cost)})" },
    notEnoughPoints = { cost -> "Not enough points — $cost needed" },
    fedMessage = "🍎 Your pet enjoyed the food!",
    playedMessage = "🎾 Your pet had fun!",
    careTip = "Tip: your pet gets hungry and bored without care — visit often!",
    maxLevel = "👑 Max level! You're the best study buddy!",
    progressTitle = "Evolution progress",
    nextStage = { points, emoji, name -> "Earn $points more ${pointsWord(points)} to evolve into $emoji $name!" },
    stageUnlock = { points -> "Unlocks at $points ${pointsWord(points)}" },
    currentBadge = "Current",
    growthPathTitle = "🌟 Growth path",
    levelLine = { level, lifetime -> "Lv.$level · $lifetime ${pointsWord(lifetime)} earned in total" },
    criticalAll = "Your pet is starving and bored — feed and play with it now!",
    criticalHungry = "Your pet is starving — feed it now!",
    criticalBored = "Your pet is bored — play with it now!",
    shopTitle = "🛍️ Pet decoration store",
    shopHint = "Spend points to give your pet a beautiful home!",
    defaultScene = "Default scene",
    scenePrice = { price -> "$price ${pointsWord(price)}" },
    equippedBadge = "Equipped",
    equipButton = "Equip",
    buyButton = "Buy",
    purchaseSuccess = { name -> "🎉 Purchased and equipped: $name!" },
    previewTitle = { name -> "My study pet · $name" },
    fullnessShort = { value -> "🍖 Fullness $value" },
    moodShort = { value -> "😊 Mood $value" },
    previewTip = "💡 Reviewing mistakes and focused study cheer your pet up!",
    statusCritical = "😵 I can't hold on… please save me with points (feed/play)!",
    statusHungry = "🥺 I'm so hungry… please feed me!",
    statusBored = "😔 I'm so bored… spend some points playing with me!",
    statusAllDone = "🎉 All tasks done today — I'm proud of you!",
    statusProgress = { done -> "💪 $done ${tasksWord(done)} done — keep going!" },
    statusNewDay = "🌞 A new day begins — go finish your tasks!",
    moodCritical = "😵 I can't hold on… feed and play with me to save me!",
    moodHungry = "🥺 I'm so hungry… please feed me!",
    moodBored = "😔 I'm so bored… spend some points playing with me!",
    moodAllDone = "🎉 All tasks done today — I'm proud of you!",
    moodProgress = { done -> "💪 $done ${tasksWord(done)} done — keep going, I'm with you!" },
    moodNewDay = "🌞 A new day begins — go finish your tasks and make me happy!",
)