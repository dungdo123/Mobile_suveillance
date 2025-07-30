package com.firedetection.app.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.firedetection.app.ml.DetectionResult

class CameraViewModel : ViewModel() {

    private val _detectionResults = MutableLiveData<List<DetectionResult>>(emptyList())
    val detectionResults: LiveData<List<DetectionResult>> = _detectionResults

    private val _isFireDetected = MutableLiveData<Boolean>(false)
    val isFireDetected: LiveData<Boolean> = _isFireDetected

    private val _isSmokeDetected = MutableLiveData<Boolean>(false)
    val isSmokeDetected: LiveData<Boolean> = _isSmokeDetected

    private val _detectionCount = MutableLiveData<Int>(0)
    val detectionCount: LiveData<Int> = _detectionCount

    fun updateDetectionResults(results: List<DetectionResult>) {
        _detectionResults.value = results
        
        val fireDetected = results.any { it.label == "fire" && it.confidence > 0.7f }
        val smokeDetected = results.any { it.label == "smoke" && it.confidence > 0.7f }
        
        _isFireDetected.value = fireDetected
        _isSmokeDetected.value = smokeDetected
        
        if (fireDetected || smokeDetected) {
            _detectionCount.value = (_detectionCount.value ?: 0) + 1
        }
    }

    fun clearDetections() {
        _detectionResults.value = emptyList()
        _isFireDetected.value = false
        _isSmokeDetected.value = false
        _detectionCount.value = 0
    }

    fun getDetectionSummary(): String {
        val results = _detectionResults.value ?: emptyList()
        val fireCount = results.count { it.label == "fire" }
        val smokeCount = results.count { it.label == "smoke" }
        
        return "Fire: $fireCount, Smoke: $smokeCount"
    }
} 