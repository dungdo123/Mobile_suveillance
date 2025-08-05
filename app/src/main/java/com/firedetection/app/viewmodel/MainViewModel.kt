package com.firedetection.app.viewmodel

import android.content.Context
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.firedetection.app.ml.FireDetectionModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainViewModel(private val context: Context) : ViewModel() {

    private val _isModelLoaded = MutableLiveData<Boolean>(false)
    val isModelLoaded: LiveData<Boolean> = _isModelLoaded

    private val _detectionStatus = MutableLiveData<String>("Ready to detect")
    val detectionStatus: LiveData<String> = _detectionStatus

    private var fireDetectionModel: FireDetectionModel? = null

    fun loadModel() {
        if (_isModelLoaded.value == true) return

        viewModelScope.launch {
            try {
                _detectionStatus.value = "Loading model..."
                fireDetectionModel = withContext(Dispatchers.IO) {
                    FireDetectionModel(context)
                }
                _isModelLoaded.value = true
                _detectionStatus.value = "Model loaded successfully"
            } catch (e: Exception) {
                _detectionStatus.value = "Failed to load model: ${e.message}"
                _isModelLoaded.value = false
            }
        }
    }

    fun getModel(): FireDetectionModel? {
        return fireDetectionModel
    }

    override fun onCleared() {
        super.onCleared()
        fireDetectionModel?.close()
    }

    class Factory(private val context: Context) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(MainViewModel::class.java)) {
                @Suppress("UNCHECKED_CAST")
                return MainViewModel(context) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
} 