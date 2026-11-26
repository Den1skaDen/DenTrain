package com.dentrain.data

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.util.AtomicFile
import android.util.Log
import com.dentrain.ui.components.decodeAvatar
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.MetadataChanges
import java.io.File
import java.io.IOException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch

internal class ProfileAvatarCache(context: Context, uid: String, scope: CoroutineScope) {
    private val file = AtomicFile(File(context.cacheDir, "profile-avatars/${Uri.encode(uid)}"))
    private val currentBitmap = MutableStateFlow<Bitmap?>(null)
    val bitmap = currentBitmap.asStateFlow()

    init {
        scope.launch(Dispatchers.IO) {
            var cachedSource = try {
                file.readFully().decodeToString()
            } catch (_: IOException) {
                null
            }
            currentBitmap.value = cachedSource?.let(::decodeAvatar)
            var displayedSource = cachedSource.takeIf { currentBitmap.value != null }

            callbackFlow {
                val listener = FirebaseFirestore.getInstance().collection("profiles").document(uid)
                    .addSnapshotListener(MetadataChanges.INCLUDE) { snapshot, _ ->
                        if (snapshot != null && !snapshot.metadata.hasPendingWrites()) {
                            val source = snapshot.getString("avatarBase64").orEmpty()
                            trySend(source to !snapshot.metadata.isFromCache)
                        }
                    }
                awaitClose { listener.remove() }
            }.collect { (source, confirmed) ->
                if (!confirmed && (currentBitmap.value != null || source.isBlank())) return@collect

                if (source != displayedSource) {
                    currentBitmap.value = source.takeIf { it.isNotBlank() }?.let(::decodeAvatar)
                    displayedSource = source
                }
                if (source == cachedSource) return@collect
                try {
                    if (currentBitmap.value == null) {
                        file.delete()
                    } else {
                        val stream = file.startWrite()
                        try {
                            stream.write(source.encodeToByteArray())
                            file.finishWrite(stream)
                        } catch (error: IOException) {
                            file.failWrite(stream)
                            throw error
                        }
                    }
                    cachedSource = source
                } catch (error: IOException) {
                    Log.w("ProfileAvatarCache", "Could not update the avatar cache", error)
                }
            }
        }
    }
}
