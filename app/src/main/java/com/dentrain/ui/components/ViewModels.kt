package com.dentrain.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.dentrain.AppContainer
import com.dentrain.DenTrainApp

/** Создаёт ViewModel, передавая ей зависимости из AppContainer (без Hilt — проще для курсовой). */
@Composable
inline fun <reified VM : ViewModel> appViewModel(
    key: String? = null,
    crossinline builder: (AppContainer) -> VM
): VM {
    val container = (LocalContext.current.applicationContext as DenTrainApp).container
    return viewModel(
        key = key,
        factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = builder(container) as T
        }
    )
}
