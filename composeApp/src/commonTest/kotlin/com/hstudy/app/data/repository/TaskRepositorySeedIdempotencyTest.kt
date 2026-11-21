package com.hstudy.app.data.repository

import com.hstudy.app.data.local.dao.TaskDao
import com.hstudy.app.data.local.entity.TaskCategory
import com.hstudy.app.data.local.entity.TaskEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * TaskDao 的内存 Fake 实现，用于验证播种幂等（D-02）。
 * 语义对齐真实 Room：insert 自增 id、isActive 默认 true；softDelete 置 isActive=0；
 * 活跃任务查询仅统计 isActive=true 的行。
 */
class FakeTaskDao : TaskDao {

    private val tasks = MutableStateFlow<List<TaskEntity>>(emptyList())
    private var nextId = 1L

    override suspend fun insert(task: TaskEntity): Long {
        val id = nextId++
        tasks.value = tasks.value + task.copy(id = id)
        return id
    }

    override suspend fun update(task: TaskEntity) {
        tasks.value = tasks.value.map { if (it.id == task.id) task else it }
    }

    override suspend fun softDelete(id: Long) {
        tasks.value = tasks.value.map { if (it.id == id) it.copy(isActive = false) else it }
    }

    override suspend fun delete(task: TaskEntity) {
        tasks.value = tasks.value.filter { it.id != task.id }
    }

    override suspend fun getTaskById(id: Long): TaskEntity? = tasks.value.find { it.id == id }

    override suspend fun getActiveTaskCount(): Int = tasks.value.count { it.isActive }

    override suspend fun getActiveTaskByTitle(title: String): TaskEntity? =
        tasks.value.firstOrNull { it.isActive && it.title == title }

    override suspend fun getAllActiveTasksSnapshot(): List<TaskEntity> =
        tasks.value.filter { it.isActive }

    override fun getAllActiveTasks(): Flow<List<TaskEntity>> =
        tasks.map { list -> list.filter { it.isActive } }

    override fun getTasksByCategory(category: TaskCategory): Flow<List<TaskEntity>> =
        tasks.map { list -> list.filter { it.isActive && it.category == category } }

    /** 测试辅助：返回全部（含软删）行，便于断言物理行数与重复标题。 */
    fun allRows(): List<TaskEntity> = tasks.value
}

class TaskRepositorySeedIdempotencyTest {

    /** 首启用默认模板（primary_high）任务标题集合。 */
    private fun defaultTitles(): List<String> =
        TaskRepository(FakeTaskDao()).getPresets()[1].tasks.map { it.title }

    @Test
    fun initDefaultTasks_calledTwice_doesNotCreateDuplicateTasks(): Unit = runBlocking {
        val dao = FakeTaskDao()
        val repo = TaskRepository(dao)

        repo.initDefaultTasks()
        val afterFirst = dao.getActiveTaskCount()
        repo.initDefaultTasks() // 第二次调用必须按 title 幂等，不产生重复卡
        val afterSecond = dao.getActiveTaskCount()

        val expected = defaultTitles().distinct().size
        assertEquals(expected, afterFirst, "首次播种应写入模板的每个唯一标题")
        assertEquals(expected, afterSecond, "第二次播种不得新增任何活跃任务")

        val activeTitles = dao.getAllActiveTasksSnapshot().map { it.title }
        assertEquals(activeTitles.size, activeTitles.distinct().size, "活跃任务标题不允许重复")
    }

    @Test
    fun ensureDefaultTasks_withExistingTasks_doesNotInsert(): Unit = runBlocking {
        val dao = FakeTaskDao()
        val repo = TaskRepository(dao)

        // 模拟 v2 老用户覆盖升级：表内已存在与默认模板同名的活跃任务
        defaultTitles().forEach { title -> repo.addTask(TaskEntity(title = title, category = TaskCategory.BASIC, points = 5)) }
        val before = dao.getActiveTaskCount()
        assertTrue(before > 0)

        repo.ensureDefaultTasks()

        assertEquals(before, dao.getActiveTaskCount(), "已有活跃任务时 ensureDefaultTasks 不应插入")
        val activeTitles = dao.getAllActiveTasksSnapshot().map { it.title }
        assertEquals(activeTitles.size, activeTitles.distinct().size, "ensureDefaultTasks 不应制造重复标题")
    }

    @Test
    fun ensureDefaultTasks_withEmptyTable_seedsDefaults(): Unit = runBlocking {
        val dao = FakeTaskDao()
        val repo = TaskRepository(dao)

        assertEquals(0, dao.getActiveTaskCount())
        repo.ensureDefaultTasks()

        assertEquals(defaultTitles().distinct().size, dao.getActiveTaskCount(), "空表应补全默认任务")
    }
}
