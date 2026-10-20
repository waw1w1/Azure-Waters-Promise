package com.ainovel.local.ui

import com.ainovel.local.data.model.CandidateEntity
import com.ainovel.local.data.model.CandidateGroupEntity
import com.ainovel.local.data.model.CandidateGroupStatus
import com.ainovel.local.data.model.TaskType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkspaceCandidateStateTest {

    private fun createGroup(
        id: String = "group-1",
        taskType: TaskType = TaskType.BODY,
        novelId: String = "novel-1",
        status: CandidateGroupStatus = CandidateGroupStatus.GENERATING,
    ): CandidateGroupEntity = CandidateGroupEntity(
        id = id,
        taskType = taskType,
        novelId = novelId,
        frozenRequestSnapshotId = "req-$id",
        status = status,
    )

    private fun createCandidate(
        id: String = "cand-1",
        groupId: String = "group-1",
        rollNumber: Int = 1,
        text: String = "Generated body text candidate",
    ): CandidateEntity = CandidateEntity(
        id = id,
        candidateGroupId = groupId,
        rollNumber = rollNumber,
        rawOutput = text,
        editedText = text,
        requestSnapshotId = "req-$groupId",
    )

    @Test
    fun resolveActiveCandidates_whenActiveGroupsEmpty_returnsEmptyListEvenIfCandidatesNonEmpty() {
        val candidates = listOf(
            createCandidate("c1", "group-1"),
            createCandidate("c2", "group-1"),
        )
        val result = resolveActiveCandidates(
            activeGroups = emptyList(),
            candidates = candidates,
        )
        assertTrue("Should return empty list when activeGroups is empty", result.isEmpty())
    }

    @Test
    fun resolveActiveCandidates_whenActiveGroupMatches_returnsMatchingCandidates() {
        val group = createGroup("group-1")
        val c1 = createCandidate("c1", "group-1", rollNumber = 1)
        val c2 = createCandidate("c2", "group-1", rollNumber = 2)
        val candidates = listOf(c1, c2)

        val result = resolveActiveCandidates(
            activeGroups = listOf(group),
            candidates = candidates,
        )

        assertEquals(2, result.size)
        assertEquals(listOf(c1, c2), result)
    }

    @Test
    fun resolveActiveCandidates_filtersOutStaleCandidatesFromPreviousGroup() {
        val activeGroup = createGroup("group-2")
        val staleCandidate = createCandidate("c1", "group-1")
        val freshCandidate = createCandidate("c2", "group-2")

        val result = resolveActiveCandidates(
            activeGroups = listOf(activeGroup),
            candidates = listOf(staleCandidate, freshCandidate),
        )

        assertEquals(1, result.size)
        assertEquals("c2", result.first().id)
        assertEquals("group-2", result.first().candidateGroupId)
    }

    @Test
    fun resolveActiveCandidates_whenCandidatesEmpty_returnsEmptyList() {
        val activeGroup = createGroup("group-1")
        val result = resolveActiveCandidates(
            activeGroups = listOf(activeGroup),
            candidates = emptyList(),
        )
        assertTrue(result.isEmpty())
    }

    @Test
    fun resolveActiveCandidates_usesFirstActiveGroupWhenMultipleExist() {
        val group1 = createGroup("group-1")
        val group2 = createGroup("group-2")
        val c1 = createCandidate("c1", "group-1")
        val c2 = createCandidate("c2", "group-2")

        val result = resolveActiveCandidates(
            activeGroups = listOf(group1, group2),
            candidates = listOf(c1, c2),
        )

        assertEquals(1, result.size)
        assertEquals("c1", result.first().id)
    }

    @Test
    fun acceptanceTransition_simulatesThreeStagesOfCandidateAcceptance() {
        val group1 = createGroup("group-1", status = CandidateGroupStatus.PENDING)
        val candidate = createCandidate("c1", "group-1")

        // Stage 1: Active group exists with candidate
        val stage1 = resolveActiveCandidates(
            activeGroups = listOf(group1),
            candidates = listOf(candidate),
        )
        assertEquals(1, stage1.size)
        assertEquals("c1", stage1.first().id)

        // Stage 2: Immediate accept in Room - observeActiveGroups emits emptyList()
        // while observeCandidates hasn't updated yet (intermediate combine state)
        val stage2 = resolveActiveCandidates(
            activeGroups = emptyList(),
            candidates = listOf(candidate),
        )
        assertTrue(
            "During acceptance transition when activeGroups emits empty before candidates, result must be empty",
            stage2.isEmpty(),
        )

        // Stage 3: observeCandidates catches up and emits emptyList()
        val stage3 = resolveActiveCandidates(
            activeGroups = emptyList(),
            candidates = emptyList(),
        )
        assertTrue(stage3.isEmpty())
    }
}
