package com.ya.praticecamera2

import android.hardware.camera2.CameraCharacteristics

/**
 * @author  : 周广亚
 * @version :
 * @Date    :  2023/5/3 16:59
 * @Desc    :
 * 判断相机的 Hard Level 是否大于等于执行的 Level
 *
 */
fun CameraCharacteristics.isHardwareLevelSupported(requireLevel: Int): Boolean {
    val sortedLevels = intArrayOf(CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_LEGACY, CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_LIMITED, CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_FULL, CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_3)

    val deviceLevel = this[CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL]
    if(requireLevel == deviceLevel) {
        return true
    }
    for (sortLevel in sortedLevels) {
        if(requireLevel == sortLevel) {
            return true
        }
        if(deviceLevel == sortLevel) {
            return false
        }

    }
    return false
}