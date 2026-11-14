package com.vergis.vegs.data

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.UserProfileChangeRequest
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.google.firebase.storage.FirebaseStorage
import android.net.Uri
import java.io.ByteArrayOutputStream
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Request
import okhttp3.OkHttpClient
import okhttp3.MultipartBody
import okhttp3.MediaType.Companion.toMediaType

class FirebaseRepository {
    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseDatabase.getInstance().reference.child("vergis")
    private val storage = FirebaseStorage.getInstance().reference.child("vergis")

    val currentUser get() = auth.currentUser

    suspend fun register(email: String, password: String, displayName: String): Result<Unit> {
        return try {
            val result = auth.createUserWithEmailAndPassword(email, password).await()
            val profile = UserProfileChangeRequest.Builder().setDisplayName(displayName).build()
            result.user?.updateProfile(profile)?.await()
            val uid = result.user?.uid ?: return Result.failure(Exception("Нет uid"))
            db.child("users").child(uid).setValue(
                mapOf(
                    "uid" to uid,
                    "email" to email,
                    "displayName" to displayName,
                    "avatarUrl" to "",
                    "nameLower" to displayName.trim().lowercase()
                )
            ).await()
            if (displayName.isNotBlank()) {
                db.child("nameIndex").child(displayName.trim().lowercase()).setValue(uid).await()
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun login(email: String, password: String): Result<Unit> {
        return try {
            auth.signInWithEmailAndPassword(email, password).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun logout() = auth.signOut()

    fun observeRooms(): Flow<List<RoomInfo>> = callbackFlow {
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val now = System.currentTimeMillis()
                val list = mutableListOf<RoomInfo>()
                for (child in snapshot.children) {
                    val room = parseRoom(child) ?: continue
                    // Автоудаление: idle > 5 мин в playing, или waiting > 30 мин
                    val last = room.lastActivityAt.takeIf { it > 0 } ?: room.createdAt
                    val idle = now - last
                    if (room.status == "playing" && idle > 5 * 60 * 1000) {
                        child.ref.removeValue()
                        continue
                    }
                    if (room.status == "waiting" && idle > 30 * 60 * 1000) {
                        child.ref.removeValue()
                        continue
                    }
                    if (room.status == "waiting" || room.status == "playing") {
                        val me = currentUser?.uid
                        // скрытые — только создателю / участнику
                        if (room.hidden && me != room.creatorId && me !in room.players) continue
                        list.add(room)
                    }
                }
                trySend(list.sortedByDescending { it.createdAt })
            }
            override fun onCancelled(error: DatabaseError) {}
        }
        db.child("rooms").addValueEventListener(listener)
        awaitClose { db.child("rooms").removeEventListener(listener) }
    }

    fun observeRoom(roomId: String): Flow<RoomInfo?> = callbackFlow {
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (!snapshot.exists()) {
                    trySend(null)
                    return
                }
                val room = parseRoom(snapshot)
                val now = System.currentTimeMillis()
                if (room != null && room.status == "playing") {
                    val last = room.lastActivityAt.takeIf { it > 0 } ?: room.createdAt
                    if (now - last > 5 * 60 * 1000) {
                        snapshot.ref.removeValue()
                        trySend(null)
                        return
                    }
                }
                trySend(room)
            }
            override fun onCancelled(error: DatabaseError) { trySend(null) }
        }
        db.child("rooms").child(roomId).addValueEventListener(listener)
        awaitClose { db.child("rooms").child(roomId).removeEventListener(listener) }
    }

    suspend fun createRoom(
        name: String,
        maxPlayers: Int,
        theme: String,
        password: String,
        hidden: Boolean = false,
        friendsOnly: Boolean = false
    ): Result<String> {
        return try {
            val user = currentUser ?: return Result.failure(Exception("Не авторизован"))
            val profile = getUserProfile(user.uid)
            val playerName = profile?.displayName?.takeIf { it.isNotBlank() }
                ?: user.displayName?.takeIf { it.isNotBlank() }
                ?: "Игрок"
            val roomTitle = name.takeIf { it.isNotBlank() } ?: "Комната $playerName(a)"
            val avatar = profile?.avatarUrl ?: ""
            val ref = db.child("rooms").push()
            val id = ref.key ?: return Result.failure(Exception("Ошибка id"))
            val now = System.currentTimeMillis()
            val room = mapOf(
                "name" to roomTitle,
                "creatorId" to user.uid,
                "maxPlayers" to maxPlayers,
                "theme" to theme,
                "password" to password,
                "hidden" to hidden,
                "friendsOnly" to friendsOnly,
                "status" to "waiting",
                "currentRound" to 0,
                "currentQuestion" to "",
                "createdAt" to now,
                "lastActivityAt" to now,
                "players" to mapOf(
                    user.uid to mapOf(
                        "uid" to user.uid,
                        "name" to playerName,
                        "avatarUrl" to avatar,
                        "joinedAt" to now,
                        "lastSeenAt" to now
                    )
                ),
                "answers" to emptyMap<String, String>(),
                "readyForNext" to emptyMap<String, Boolean>(),
                "usedQuestions" to emptyList<String>()
            )
            ref.setValue(room).await()
            Result.success(id)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun joinRoom(roomId: String, password: String, playerName: String): Result<Unit> {
        return try {
            val user = currentUser ?: return Result.failure(Exception("Не авторизован"))
            val snap = db.child("rooms").child(roomId).get().await()
            if (!snap.exists()) return Result.failure(Exception("Комната не найдена"))
            val room = parseRoom(snap) ?: return Result.failure(Exception("Ошибка комнаты"))
            if (room.status != "waiting") return Result.failure(Exception("Игра уже началась"))
            if (room.password.isNotEmpty() && room.password != password) {
                return Result.failure(Exception("Неверный пароль"))
            }
            if (room.friendsOnly && user.uid != room.creatorId) {
                val ok = isFriend(user.uid, room.creatorId)
                if (!ok) return Result.failure(Exception("Только для друзей создателя"))
            }
            if (room.players.size >= room.maxPlayers && !room.players.containsKey(user.uid)) {
                return Result.failure(Exception("Комната заполнена"))
            }
            if (room.players.containsKey(user.uid)) {
                return Result.success(Unit)
            }
            val profile = getUserProfile(user.uid)
            val now = System.currentTimeMillis()
            val player = mapOf(
                "uid" to user.uid,
                "name" to (profile?.displayName?.takeIf { it.isNotBlank() } ?: playerName),
                "avatarUrl" to (profile?.avatarUrl ?: ""),
                "joinedAt" to now,
                "lastSeenAt" to now
            )
            db.child("rooms").child(roomId).child("players").child(user.uid).setValue(player).await()
            touchActivity(roomId)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun leaveRoom(roomId: String, reason: String = "left") {
        val user = currentUser ?: return
        try {
            val profile = getUserProfile(user.uid)
            val name = profile?.displayName?.takeIf { it.isNotBlank() } ?: user.displayName ?: "Игрок"
            // event for others
            val ev = db.child("rooms").child(roomId).child("events").push()
            ev.setValue(
                mapOf(
                    "type" to reason,
                    "uid" to user.uid,
                    "name" to name,
                    "timestamp" to System.currentTimeMillis()
                )
            ).await()
            db.child("rooms").child(roomId).child("players").child(user.uid).removeValue().await()
            db.child("rooms").child(roomId).child("answers").child(user.uid).removeValue().await()
            db.child("rooms").child(roomId).child("readyForNext").child(user.uid).removeValue().await()
            val snap = db.child("rooms").child(roomId).child("players").get().await()
            if (!snap.exists() || snap.childrenCount == 0L) {
                db.child("rooms").child(roomId).removeValue().await()
            } else {
                touchActivity(roomId)
            }
        } catch (_: Exception) {}
    }

    suspend fun heartbeat(roomId: String) {
        val user = currentUser ?: return
        try {
            val now = System.currentTimeMillis()
            db.child("rooms").child(roomId).child("players").child(user.uid)
                .child("lastSeenAt").setValue(now).await()
            touchActivity(roomId)
        } catch (_: Exception) {}
    }

    suspend fun kickAfk(roomId: String, uid: String, name: String) {
        try {
            val ev = db.child("rooms").child(roomId).child("events").push()
            ev.setValue(
                mapOf(
                    "type" to "afk",
                    "uid" to uid,
                    "name" to name,
                    "timestamp" to System.currentTimeMillis()
                )
            ).await()
            db.child("rooms").child(roomId).child("players").child(uid).removeValue().await()
            db.child("rooms").child(roomId).child("answers").child(uid).removeValue().await()
            db.child("rooms").child(roomId).child("readyForNext").child(uid).removeValue().await()
            val snap = db.child("rooms").child(roomId).child("players").get().await()
            if (!snap.exists() || snap.childrenCount == 0L) {
                db.child("rooms").child(roomId).removeValue().await()
            }
        } catch (_: Exception) {}
    }

    private suspend fun touchActivity(roomId: String) {
        try {
            db.child("rooms").child(roomId).child("lastActivityAt")
                .setValue(System.currentTimeMillis()).await()
        } catch (_: Exception) {}
    }

    suspend fun startGame(roomId: String, question: String) {
        val usedSnap = db.child("rooms").child(roomId).child("usedQuestions").get().await()
        val used = usedSnap.children.mapNotNull { it.getValue(String::class.java) }.toMutableList()
        if (question.isNotBlank() && question !in used) used.add(question)
        val now = System.currentTimeMillis()
        val updates = mapOf(
            "status" to "playing",
            "currentRound" to 1,
            "currentQuestion" to question,
            "answers" to emptyMap<String, String>(),
            "readyForNext" to emptyMap<String, Boolean>(),
            "usedQuestions" to used,
            "lastActivityAt" to now
        )
        db.child("rooms").child(roomId).updateChildren(updates).await()
    }

    suspend fun submitAnswer(roomId: String, answer: String) {
        val user = currentUser ?: return
        db.child("rooms").child(roomId).child("answers").child(user.uid).setValue(answer).await()
        touchActivity(roomId)
    }

    suspend fun setReadyForNext(roomId: String) {
        val user = currentUser ?: return
        db.child("rooms").child(roomId).child("readyForNext").child(user.uid).setValue(true).await()
        touchActivity(roomId)
    }

    suspend fun advanceRound(roomId: String, nextRound: Int, question: String) {
        val usedSnap = db.child("rooms").child(roomId).child("usedQuestions").get().await()
        val used = usedSnap.children.mapNotNull { it.getValue(String::class.java) }.toMutableList()
        if (question.isNotBlank() && question !in used) used.add(question)
        // keep only last 80 to avoid huge nodes
        val trimmed = if (used.size > 80) used.takeLast(80) else used
        val now = System.currentTimeMillis()
        val updates = mapOf(
            "currentRound" to nextRound,
            "currentQuestion" to question,
            "nextQuestion" to "",
            "answers" to emptyMap<String, String>(),
            "readyForNext" to emptyMap<String, Boolean>(),
            "usedQuestions" to trimmed,
            "lastActivityAt" to now
        )
        db.child("rooms").child(roomId).updateChildren(updates).await()
    }


    suspend fun getUserProfile(uid: String): UserProfile? {
        return try {
            val snap = db.child("users").child(uid).get().await()
            if (!snap.exists()) {
                val u = currentUser ?: return null
                return UserProfile(
                    uid = u.uid,
                    email = u.email.orEmpty(),
                    displayName = u.displayName.orEmpty(),
                    avatarUrl = u.photoUrl?.toString().orEmpty()
                )
            }
            UserProfile(
                uid = uid,
                email = snap.child("email").getValue(String::class.java) ?: "",
                displayName = snap.child("displayName").getValue(String::class.java) ?: "",
                avatarUrl = snap.child("avatarUrl").getValue(String::class.java) ?: ""
            )
        } catch (_: Exception) {
            null
        }
    }

    suspend fun updateDisplayName(name: String): Result<Unit> {
        return try {
            val user = currentUser ?: return Result.failure(Exception("Не авторизован"))
            val profile = UserProfileChangeRequest.Builder().setDisplayName(name).build()
            user.updateProfile(profile).await()
            db.child("users").child(user.uid).child("displayName").setValue(name).await()
            db.child("users").child(user.uid).child("nameLower").setValue(name.trim().lowercase()).await()
            db.child("nameIndex").child(name.trim().lowercase()).setValue(user.uid).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun uploadAvatar(bytes: ByteArray): Result<String> = withContext(Dispatchers.IO) {
        try {
            val user = currentUser ?: return@withContext Result.failure(Exception("Не авторизован"))
            if (bytes.isEmpty()) return@withContext Result.failure(Exception("Пустой файл"))

            val mediaType = "image/jpeg".toMediaType()
            val body = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("key", "be4f9cf74bf01c7e56308e8b707a00f1")
                .addFormDataPart(
                    "image",
                    "avatar.jpg",
                    bytes.toRequestBody(mediaType)
                )
                .build()

            val request = Request.Builder()
                .url("https://api.imgbb.com/1/upload")
                .post(body)
                .build()

            val client = OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .writeTimeout(60, TimeUnit.SECONDS)
                .build()

            client.newCall(request).execute().use { response ->
                val raw = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    return@withContext Result.failure(
                        Exception("ImgBB ${response.code}: ${raw.take(120)}")
                    )
                }
                val json = org.json.JSONObject(raw)
                if (!json.optBoolean("success", false)) {
                    val err = json.optJSONObject("error")?.optString("message") ?: raw.take(120)
                    return@withContext Result.failure(Exception("ImgBB: $err"))
                }
                val data = json.getJSONObject("data")
                val url = data.optString("display_url").ifBlank { data.optString("url") }
                if (url.isBlank()) return@withContext Result.failure(Exception("ImgBB: нет URL"))

                db.child("users").child(user.uid).child("avatarUrl").setValue(url).await()
                try {
                    val profile = UserProfileChangeRequest.Builder()
                        .setPhotoUri(android.net.Uri.parse(url))
                        .build()
                    user.updateProfile(profile).await()
                } catch (_: Exception) {}
                Result.success(url)
            }
        } catch (e: Exception) {
            Result.failure(Exception(e.message ?: e.javaClass.simpleName))
        }
    }



    suspend fun setNextQuestion(roomId: String, question: String) {
        db.child("rooms").child(roomId).child("nextQuestion").setValue(question).await()
        touchActivity(roomId)
    }

    suspend fun sendChatMessage(roomId: String, text: String, name: String, avatarUrl: String) {
        val user = currentUser ?: return
        val ref = db.child("rooms").child(roomId).child("chat").push()
        val msg = mapOf(
            "uid" to user.uid,
            "name" to name,
            "avatarUrl" to avatarUrl,
            "text" to text,
            "timestamp" to System.currentTimeMillis()
        )
        ref.setValue(msg).await()
        touchActivity(roomId)
    }

    fun observeChat(roomId: String): Flow<List<ChatMessage>> = callbackFlow {
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val list = mutableListOf<ChatMessage>()
                for (c in snapshot.children) {
                    val id = c.key ?: continue
                    list.add(
                        ChatMessage(
                            id = id,
                            uid = c.child("uid").getValue(String::class.java) ?: "",
                            name = c.child("name").getValue(String::class.java) ?: "",
                            avatarUrl = c.child("avatarUrl").getValue(String::class.java) ?: "",
                            text = c.child("text").getValue(String::class.java) ?: "",
                            timestamp = c.child("timestamp").getValue(Long::class.java) ?: 0L
                        )
                    )
                }
                trySend(list.sortedBy { it.timestamp }.takeLast(100))
            }
            override fun onCancelled(error: DatabaseError) {}
        }
        db.child("rooms").child(roomId).child("chat").addValueEventListener(listener)
        awaitClose { db.child("rooms").child(roomId).child("chat").removeEventListener(listener) }
    }


    suspend fun searchUsers(query: String): List<UserProfile> {
        val q = query.trim().lowercase()
        if (q.length < 2) return emptyList()
        return try {
            val snap = db.child("users").get().await()
            val me = currentUser?.uid
            snap.children.mapNotNull { c ->
                val uid = c.key ?: return@mapNotNull null
                if (uid == me) return@mapNotNull null
                val name = c.child("displayName").getValue(String::class.java) ?: ""
                val lower = c.child("nameLower").getValue(String::class.java) ?: name.lowercase()
                if (!lower.contains(q) && !name.lowercase().contains(q)) return@mapNotNull null
                UserProfile(
                    uid = uid,
                    email = c.child("email").getValue(String::class.java) ?: "",
                    displayName = name,
                    avatarUrl = c.child("avatarUrl").getValue(String::class.java) ?: ""
                )
            }.take(30)
        } catch (_: Exception) {
            emptyList()
        }
    }

    suspend fun isFriend(a: String, b: String): Boolean {
        return try {
            val snap = db.child("friends").child(a).child(b).get().await()
            snap.exists() && (snap.child("status").getValue(String::class.java) == "accepted")
        } catch (_: Exception) {
            false
        }
    }

    suspend fun sendFriendRequest(toUid: String): Result<Unit> {
        val user = currentUser ?: return Result.failure(Exception("Не авторизован"))
        if (toUid == user.uid) return Result.failure(Exception("Нельзя добавить себя"))
        return try {
            if (isFriend(user.uid, toUid)) return Result.failure(Exception("Уже в друзьях"))
            val me = getUserProfile(user.uid)
            val now = System.currentTimeMillis()
            db.child("friendRequests").child(toUid).child(user.uid).setValue(
                mapOf(
                    "fromUid" to user.uid,
                    "fromName" to (me?.displayName ?: "Игрок"),
                    "fromAvatar" to (me?.avatarUrl ?: ""),
                    "timestamp" to now
                )
            ).await()
            db.child("friends").child(user.uid).child(toUid).setValue(
                mapOf("status" to "pending_out", "since" to now)
            ).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun acceptFriend(fromUid: String): Result<Unit> {
        val user = currentUser ?: return Result.failure(Exception("Не авторизован"))
        return try {
            val now = System.currentTimeMillis()
            val other = getUserProfile(fromUid)
            val me = getUserProfile(user.uid)
            db.child("friends").child(user.uid).child(fromUid).setValue(
                mapOf(
                    "status" to "accepted",
                    "since" to now,
                    "name" to (other?.displayName ?: ""),
                    "avatarUrl" to (other?.avatarUrl ?: "")
                )
            ).await()
            db.child("friends").child(fromUid).child(user.uid).setValue(
                mapOf(
                    "status" to "accepted",
                    "since" to now,
                    "name" to (me?.displayName ?: ""),
                    "avatarUrl" to (me?.avatarUrl ?: "")
                )
            ).await()
            db.child("friendRequests").child(user.uid).child(fromUid).removeValue().await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun rejectFriend(fromUid: String) {
        val user = currentUser ?: return
        try {
            db.child("friendRequests").child(user.uid).child(fromUid).removeValue().await()
            db.child("friends").child(fromUid).child(user.uid).removeValue().await()
        } catch (_: Exception) {}
    }

    suspend fun removeFriend(friendUid: String) {
        val user = currentUser ?: return
        try {
            db.child("friends").child(user.uid).child(friendUid).removeValue().await()
            db.child("friends").child(friendUid).child(user.uid).removeValue().await()
        } catch (_: Exception) {}
    }

    fun observeFriends(): Flow<List<FriendEntry>> = callbackFlow {
        val uid = currentUser?.uid
        if (uid == null) {
            trySend(emptyList()); close(); return@callbackFlow
        }
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val list = mutableListOf<FriendEntry>()
                for (c in snapshot.children) {
                    val fuid = c.key ?: continue
                    val status = c.child("status").getValue(String::class.java) ?: "accepted"
                    if (status != "accepted") continue
                    list.add(
                        FriendEntry(
                            uid = fuid,
                            name = c.child("name").getValue(String::class.java) ?: "",
                            avatarUrl = c.child("avatarUrl").getValue(String::class.java) ?: "",
                            status = status,
                            since = c.child("since").getValue(Long::class.java) ?: 0L
                        )
                    )
                }
                trySend(list.sortedBy { it.name.lowercase() })
            }
            override fun onCancelled(error: DatabaseError) {}
        }
        db.child("friends").child(uid).addValueEventListener(listener)
        awaitClose { db.child("friends").child(uid).removeEventListener(listener) }
    }

    fun observeFriendRequests(): Flow<List<FriendEntry>> = callbackFlow {
        val uid = currentUser?.uid
        if (uid == null) {
            trySend(emptyList()); close(); return@callbackFlow
        }
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val list = mutableListOf<FriendEntry>()
                for (c in snapshot.children) {
                    val fuid = c.key ?: continue
                    list.add(
                        FriendEntry(
                            uid = fuid,
                            name = c.child("fromName").getValue(String::class.java) ?: "",
                            avatarUrl = c.child("fromAvatar").getValue(String::class.java) ?: "",
                            status = "pending_in",
                            since = c.child("timestamp").getValue(Long::class.java) ?: 0L
                        )
                    )
                }
                trySend(list)
            }
            override fun onCancelled(error: DatabaseError) {}
        }
        db.child("friendRequests").child(uid).addValueEventListener(listener)
        awaitClose { db.child("friendRequests").child(uid).removeEventListener(listener) }
    }

    suspend fun sendRoomInvite(toUid: String, roomId: String, roomName: String): Result<Unit> {
        val user = currentUser ?: return Result.failure(Exception("Не авторизован"))
        return try {
            val me = getUserProfile(user.uid)
            val ref = db.child("invites").child(toUid).push()
            ref.setValue(
                mapOf(
                    "roomId" to roomId,
                    "roomName" to roomName,
                    "fromUid" to user.uid,
                    "fromName" to (me?.displayName ?: "Игрок"),
                    "fromAvatar" to (me?.avatarUrl ?: ""),
                    "timestamp" to System.currentTimeMillis()
                )
            ).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun observeInvites(): Flow<List<RoomInvite>> = callbackFlow {
        val uid = currentUser?.uid
        if (uid == null) {
            trySend(emptyList()); close(); return@callbackFlow
        }
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val list = mutableListOf<RoomInvite>()
                for (c in snapshot.children) {
                    list.add(
                        RoomInvite(
                            id = c.key ?: continue,
                            roomId = c.child("roomId").getValue(String::class.java) ?: "",
                            roomName = c.child("roomName").getValue(String::class.java) ?: "",
                            fromUid = c.child("fromUid").getValue(String::class.java) ?: "",
                            fromName = c.child("fromName").getValue(String::class.java) ?: "",
                            fromAvatar = c.child("fromAvatar").getValue(String::class.java) ?: "",
                            timestamp = c.child("timestamp").getValue(Long::class.java) ?: 0L
                        )
                    )
                }
                trySend(list.sortedByDescending { it.timestamp })
            }
            override fun onCancelled(error: DatabaseError) {}
        }
        db.child("invites").child(uid).addValueEventListener(listener)
        awaitClose { db.child("invites").child(uid).removeEventListener(listener) }
    }

    suspend fun dismissInvite(inviteId: String) {
        val uid = currentUser?.uid ?: return
        try {
            db.child("invites").child(uid).child(inviteId).removeValue().await()
        } catch (_: Exception) {}
    }

    fun observeRoomEvents(roomId: String): Flow<List<RoomEvent>> = callbackFlow {
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val list = mutableListOf<RoomEvent>()
                for (c in snapshot.children) {
                    list.add(
                        RoomEvent(
                            id = c.key ?: continue,
                            type = c.child("type").getValue(String::class.java) ?: "",
                            uid = c.child("uid").getValue(String::class.java) ?: "",
                            name = c.child("name").getValue(String::class.java) ?: "",
                            timestamp = c.child("timestamp").getValue(Long::class.java) ?: 0L
                        )
                    )
                }
                trySend(list.sortedBy { it.timestamp }.takeLast(20))
            }
            override fun onCancelled(error: DatabaseError) {}
        }
        db.child("rooms").child(roomId).child("events").addValueEventListener(listener)
        awaitClose { db.child("rooms").child(roomId).child("events").removeEventListener(listener) }
    }

    private fun parseRoom(snapshot: DataSnapshot): RoomInfo? {
        if (!snapshot.exists()) return null
        val id = snapshot.key ?: return null
        val name = snapshot.child("name").getValue(String::class.java) ?: ""
        val creatorId = snapshot.child("creatorId").getValue(String::class.java) ?: ""
        val maxPlayers = snapshot.child("maxPlayers").getValue(Int::class.java) ?: 2
        val theme = snapshot.child("theme").getValue(String::class.java) ?: "Для друзей"
        val password = snapshot.child("password").getValue(String::class.java) ?: ""
        val status = snapshot.child("status").getValue(String::class.java) ?: "waiting"
        val currentRound = snapshot.child("currentRound").getValue(Int::class.java) ?: 0
        val currentQuestion = snapshot.child("currentQuestion").getValue(String::class.java) ?: ""
        val nextQuestion = snapshot.child("nextQuestion").getValue(String::class.java) ?: ""
        val createdAt = snapshot.child("createdAt").getValue(Long::class.java) ?: 0L
        val lastActivityAt = snapshot.child("lastActivityAt").getValue(Long::class.java) ?: createdAt
        val hidden = snapshot.child("hidden").getValue(Boolean::class.java) ?: false
        val friendsOnly = snapshot.child("friendsOnly").getValue(Boolean::class.java) ?: false

        val players = mutableMapOf<String, PlayerInfo>()
        for (p in snapshot.child("players").children) {
            val uid = p.key ?: continue
            val pname = p.child("name").getValue(String::class.java) ?: "Игрок"
            val avatar = p.child("avatarUrl").getValue(String::class.java) ?: ""
            val joined = p.child("joinedAt").getValue(Long::class.java) ?: 0L
            val lastSeen = p.child("lastSeenAt").getValue(Long::class.java) ?: joined
            players[uid] = PlayerInfo(uid, pname, avatar, joined, lastSeen)
        }

        val answers = mutableMapOf<String, String>()
        for (a in snapshot.child("answers").children) {
            val uid = a.key ?: continue
            answers[uid] = a.getValue(String::class.java) ?: ""
        }

        val ready = mutableMapOf<String, Boolean>()
        for (r in snapshot.child("readyForNext").children) {
            val uid = r.key ?: continue
            ready[uid] = r.getValue(Boolean::class.java) ?: false
        }

        val usedQuestions = mutableListOf<String>()
        for (u in snapshot.child("usedQuestions").children) {
            u.getValue(String::class.java)?.let { usedQuestions.add(it) }
        }

        return RoomInfo(
            id = id,
            name = name,
            creatorId = creatorId,
            maxPlayers = maxPlayers,
            theme = theme,
            password = password,
            status = status,
            currentRound = currentRound,
            currentQuestion = currentQuestion,
            nextQuestion = nextQuestion,
            createdAt = createdAt,
            players = players,
            answers = answers,
            readyForNext = ready,
            usedQuestions = usedQuestions,
            lastActivityAt = lastActivityAt,
            hidden = hidden,
            friendsOnly = friendsOnly
        )
    }
}
