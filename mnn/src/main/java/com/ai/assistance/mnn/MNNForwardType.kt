package com.ai.assistance.mnn

/**
 * MNN Forward Type
 * 推理执行的硬件类型
 */
enum class MNNForwardType(val type: Int) {
    /**
     * CPU
     */
    FORWARD_CPU(0),

    /**
     * OPENCL
     */
    FORWARD_OPENCL(3),

    /**
     * AUTO
     */
    FORWARD_AUTO(4),

    /**
     * OPENGL
     */
    FORWARD_OPENGL(6),

    /**
     * VULKAN
     */
    FORWARD_VULKAN(7)
}

