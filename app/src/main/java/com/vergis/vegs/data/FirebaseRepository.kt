package com.vergis.vegs.data

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.UserProfileChangeRequest
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

class FirebaseRepository {
    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseDatabase.getInstance().reference.child("vergis")

    val currentUser get() = auth.currentUser

    suspend fun register(email: String, password: String, displayName: String): Result<Unit> {
        return try {
            val result = auth.createUserWithEmailAndPassword(email, password).await()
            val profile = UserProfileChangeRequest.Builder().setDisplayName(displayName).build()
            result.user?.updateProfile(profile)?.await()
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
        password: String
    ): Result<String> {
        return try {
            val user = currentUser ?: return Result.failure(Exception("Не авторизован"))
            val ref = db.child("rooms").push()
            val id = ref.key ?: return Result.failure(Exception("Ошибка id"))
            val now = System.currentTimeMillis()
            val room = mapOf(
                "name" to name,
                "creatorId" to user.uid,
                "maxPlayers" to maxPlayers,
                "theme" to theme,
                "password" to password,
                "status" to "waiting",
                "currentRound" to 0,
                "currentQuestion" to "",
                "createdAt" to now,
                "lastActivityAt" to now,
                "players" to mapOf(
                    user.uid to mapOf(
                        "uid" to user.uid,
                        "name" to name,
                        "joinedAt" to now
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
            if (room.players.size >= room.maxPlayers && !room.players.containsKey(user.uid)) {
                return Result.failure(Exception("Комната заполнена"))
            }
            if (room.players.containsKey(user.uid)) {
                return Result.success(Unit)
            }
            val player = mapOf(
                "uid" to user.uid,
                "name" to playerName,
                "joinedAt" to System.currentTimeMillis()
            )
            db.child("rooms").child(roomId).child("players").child(user.uid).setValue(player).await()
            touchActivity(roomId)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun leaveRoom(roomId: String) {
        val user = currentUser ?: return
        try {
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
        val now = System.currentTimeMillis()
        val updates = mapOf(
            "currentRound" to nextRound,
            "currentQuestion" to question,
            "answers" to emptyMap<String, String>(),
            "readyForNext" to emptyMap<String, Boolean>(),
            "usedQuestions" to used,
            "lastActivityAt" to now
        )
        db.child("rooms").child(roomId).updateChildren(updates).await()
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
        val createdAt = snapshot.child("createdAt").getValue(Long::class.java) ?: 0L
        val lastActivityAt = snapshot.child("lastActivityAt").getValue(Long::class.java) ?: createdAt

        val players = mutableMapOf<String, PlayerInfo>()
        for (p in snapshot.child("players").children) {
            val uid = p.key ?: continue
            val pname = p.child("name").getValue(String::class.java) ?: "Игрок"
            val joined = p.child("joinedAt").getValue(Long::class.java) ?: 0L
            players[uid] = PlayerInfo(uid, pname, joined)
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
            createdAt = createdAt,
            players = players,
            answers = answers,
            readyForNext = ready,
            usedQuestions = usedQuestions,
            lastActivityAt = lastActivityAt
        )
    }
}
