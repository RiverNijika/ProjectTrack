package com.projecttrack.app.domain

import com.projecttrack.app.data.model.ProgressNode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LatestNodeSelectorTest {

    private fun node(
        id: Long,
        occurredAt: Long,
        createdAt: Long = 0L,
        title: String = "节点$id",
    ) = ProgressNode(
        id = id,
        projectId = 1L,
        title = title,
        description = "",
        occurredAt = occurredAt,
        createdAt = createdAt,
    )

    @Test
    fun noNodesMeansNoCurrentState() {
        assertNull(LatestNodeSelector.select(emptyList()))
    }

    @Test
    fun latestNodeIsChosenByOccurredAt() {
        val nodes = listOf(
            node(id = 1, occurredAt = 100),
            node(id = 2, occurredAt = 300),
            node(id = 3, occurredAt = 200),
        )
        assertEquals(2L, LatestNodeSelector.select(nodes)?.id)
    }

    @Test
    fun tiesAreResolvedByCreatedAtThenId() {
        val sameTime = listOf(
            node(id = 1, occurredAt = 100, createdAt = 10),
            node(id = 2, occurredAt = 100, createdAt = 20),
            node(id = 3, occurredAt = 100, createdAt = 20),
        )
        assertEquals(3L, LatestNodeSelector.select(sameTime)?.id)
    }

    @Test
    fun inputOrderDoesNotMatter() {
        val nodes = listOf(
            node(id = 9, occurredAt = 10),
            node(id = 4, occurredAt = 99),
            node(id = 7, occurredAt = 50),
        )
        val shuffled = listOf(nodes[2], nodes[0], nodes[1])
        assertEquals(
            LatestNodeSelector.select(nodes)?.id,
            LatestNodeSelector.select(shuffled)?.id,
        )
    }
}
