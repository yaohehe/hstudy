package com.hstudy.app.platform

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import okio.FileSystem

actual val systemFileSystem: FileSystem get() = FileSystem.SYSTEM

actual val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
