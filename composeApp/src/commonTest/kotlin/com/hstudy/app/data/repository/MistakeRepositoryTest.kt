package com.hstudy.app.data.repository

import com.hstudy.app.data.local.dao.MistakeDao
import com.hstudy.app.data.local.entity.MistakeEntity
import com.hstudy.app.data.local.entity.MistakeReviewEntity
import com.hstudy.app.data.local.entity.MistakeSubject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * MistakeDao 的内存 Fake 实现，用于单元测试 MistakeRepository 的业务逻辑。
 */
class FakeMistakeDao : MistakeDao {

    private val mistakes = MutableStateFlow<List<MistakeEntity>>(emptyList())
    private val reviews = mutableListOf<MistakeReviewEntity>()
    private var nextId = 1L

    override suspend fun insert(mistake: MistakeEntity): Long {
        val id = nextId++
        val entity = mistake.copy(id = id)
        mistakes.value = mistakes.value + entity
        return id
    }

    override suspend fun update(mistake: MistakeEntity) {
        mistakes.value = mistakes.value.map { if (it.id == mistake.id) mistake else it }
    }

    override suspend fun delete(id: Long) {
        mistakes.value = mistakes.value.filter { it.id != id }
    }

    override fun getUnmasteredMistakes(): Flow<List<MistakeEntity>> =
        mistakes.map { list -> list.filter { !it.isMastered }.sortedByDescending { it.createdAt } }

    override fun getAllMistakes(): Flow<List<MistakeEntity>> =
        mistakes.map { list -> list.sortedByDescending { it.createdAt } }

    override fun getMistakesBySubject(subject: MistakeSubject): Flow<List<MistakeEntity>> =
        mistakes.map { list -> list.filter { it.subject == subject }.sortedByDescending { it.createdAt } }

    override suspend fun getMistakeById(id: Long): MistakeEntity? =
        mistakes.value.find { it.id == id }

    override suspend fun setMastered(id: Long, mastered: Boolean, masteredAt: Long?) {
        mistakes.value = mistakes.value.map {
            if (it.id == id) it.copy(isMastered = mastered, masteredAt = masteredAt) else it
        }
    }

    override suspend fun updateReviewProgress(id: Long, lastReviewAt: Long, reviewCount: Int) {
        mistakes.value = mistakes.value.map {
            if (it.id == id) it.copy(lastReviewAt = lastReviewAt, reviewCount = reviewCount) else it
        }
    }

    override fun getMasteredCount(): Flow<Int> =
        mistakes.map { list -> list.count { it.isMastered } }

    override suspend fun insertReview(review: MistakeReviewEntity): Long {
        reviews.add(review.copy(id = nextId++))
        return nextId - 1
    }

    override fun getReviewsForMistake(mistakeId: Long): Flow<List<MistakeReviewEntity>> =
        MutableStateFlow(reviews.filter { it.mistakeId == mistakeId }.sortedByDescending { it.reviewedAt })

    override fun getTotalReviewCount(): Flow<Int> = MutableStateFlow(reviews.size)

    override fun getVariationCount(): Flow<Int> =
        mistakes.map { list -> list.count { it.hasVariation } }

    override fun getReviewCountSince(since: Long): Flow<Int> =
        MutableStateFlow(reviews.count { it.reviewedAt >= since })

    override fun getReviewsSince(since: Long): Flow<List<MistakeReviewEntity>> =
        MutableStateFlow(reviews.filter { it.reviewedAt >= since }.sortedByDescending { it.reviewedAt })
}

class MistakeRepositoryTest {

    private fun newRepo(): Pair<FakeMistakeDao, MistakeRepository> {
        val dao = FakeMistakeDao()
        return dao to MistakeRepository(dao)
    }

    private fun makeMistake(
        subject: MistakeSubject = MistakeSubject.MATH,
        title: String = "test",
        reviewCount: Int = 0,
        isMastered: Boolean = false
    ) = MistakeEntity(
        subject = subject,
        title = title,
        reviewCount = reviewCount,
        isMastered = isMastered
    )

    @Test
    fun addMistake_returns_valid_id(): Unit = runBlocking {
        val (_, repo) = newRepo()
        val id = repo.addMistake(makeMistake(title = "错题1"))
        assertTrue(id > 0)
    }

    @Test
    fun getMistakeById_returns_inserted_mistake(): Unit = runBlocking {
        val (_, repo) = newRepo()
        val id = repo.addMistake(makeMistake(title = "错题A"))
        val found = repo.getMistakeById(id)
        assertNotNull(found)
        assertEquals("错题A", found.title)
    }

    @Test
    fun getMistakeById_returns_null_for_missing_id(): Unit = runBlocking {
        val (_, repo) = newRepo()
        assertNull(repo.getMistakeById(999))
    }

    @Test
    fun deleteMistake_removes_the_mistake(): Unit = runBlocking {
        val (_, repo) = newRepo()
        val id = repo.addMistake(makeMistake())
        repo.deleteMistake(id)
        assertNull(repo.getMistakeById(id))
    }

    @Test
    fun setMastered_marks_mistake_as_mastered(): Unit = runBlocking {
        val (_, repo) = newRepo()
        val id = repo.addMistake(makeMistake())
        repo.setMastered(id, true)
        val mistake = repo.getMistakeById(id)
        assertTrue(mistake!!.isMastered)
        assertNotNull(mistake.masteredAt)
    }

    @Test
    fun setMastered_false_clears_masteredAt(): Unit = runBlocking {
        val (_, repo) = newRepo()
        val id = repo.addMistake(makeMistake(isMastered = true))
        repo.setMastered(id, false)
        val mistake = repo.getMistakeById(id)
        assertFalse(mistake!!.isMastered)
        assertNull(mistake.masteredAt)
    }

    @Test
    fun reviewMistake_increments_reviewCount_and_sets_lastReviewAt(): Unit = runBlocking {
        val (_, repo) = newRepo()
        val id = repo.addMistake(makeMistake(reviewCount = 2))
        repo.reviewMistake(id, points = 5, note = "复习笔记")

        val mistake = repo.getMistakeById(id)
        assertNotNull(mistake)
        assertEquals(3, mistake.reviewCount)
        assertTrue(mistake.lastReviewAt > 0)
    }

    @Test
    fun reviewMistake_does_nothing_for_missing_id(): Unit = runBlocking {
        val (_, repo) = newRepo()
        // Should not throw
        repo.reviewMistake(999)
    }

    @Test
    fun saveVariation_updates_note_and_hasVariation_flag(): Unit = runBlocking {
        val (_, repo) = newRepo()
        val id = repo.addMistake(makeMistake())
        val mistake = repo.getMistakeById(id)!!
        repo.saveVariation(mistake, "举一反三：换一种解法")

        val updated = repo.getMistakeById(id)
        assertTrue(updated!!.hasVariation)
        assertEquals("举一反三：换一种解法", updated.variationNote)
    }

    @Test
    fun updateMistake_persists_changes(): Unit = runBlocking {
        val (_, repo) = newRepo()
        val id = repo.addMistake(makeMistake(title = "原标题"))
        val mistake = repo.getMistakeById(id)!!
        repo.updateMistake(mistake.copy(title = "新标题"))

        assertEquals("新标题", repo.getMistakeById(id)!!.title)
    }
}