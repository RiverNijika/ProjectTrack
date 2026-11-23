package com.projecttrack.app.domain

import com.projecttrack.app.data.model.ProgressNode

/**
 * 选出项目的“最新节点”。项目当前状态由最新节点表达，不使用固定状态枚举或完成百分比。
 *
 * 排序规则：发生时间倒序，发生时间相同则创建时间更晚者优先，仍相同则 id 更大者优先。
 * 该函数只依赖纯数据，方便单元测试，也用于项目列表摘要计算。
 */
object LatestNodeSelector {
    fun select(nodes: List<ProgressNode>): ProgressNode? =
        nodes.maxWithOrNull(
            compareBy<ProgressNode> { it.occurredAt }
                .thenBy { it.createdAt }
                .thenBy { it.id },
        )
}
