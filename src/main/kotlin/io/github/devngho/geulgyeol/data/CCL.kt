package io.github.devngho.geulgyeol.data

import kotlinx.serialization.Serializable

@Serializable
data class CCL(
    val conditions: List<CCLConditions>,
    val url: String
) {
    @Serializable
    enum class CCLConditions {
        BY,
        SA,
        NC,
        ND;
    }

    fun contains(condition: CCLConditions) = conditions.contains(condition)
    fun isPublishable() = !this.contains(CCLConditions.ND) // ND는 형태 변경 금지 -> 데이터셋 공개
}