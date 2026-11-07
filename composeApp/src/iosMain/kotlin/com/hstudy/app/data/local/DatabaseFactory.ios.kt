package com.hstudy.app.data.local

import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * iOS 侧 Room builder：Room 的 KMP（native）实现直接把名字当文件路径处理，
 * 因此传入 commonMain 给的绝对路径 `<dataDir>/databases/hstudy_db`
 * （dataDir = NSHomeDirectory()/Documents，见 IosPlatform.initIosPlatform）。
 *
 * 注意：本文件依赖 Room 为各 iOS target 生成的 `actual object AppDatabaseConstructor`
 * （由 @ConstructedBy + KSP 产生），Windows 上无法编译验证，需在 macOS/CI 上确认。
 */
actual fun appDatabaseBuilder(dbFilePath: String): RoomDatabase.Builder<AppDatabase> =
    Room.databaseBuilder<AppDatabase>(name = dbFilePath)
