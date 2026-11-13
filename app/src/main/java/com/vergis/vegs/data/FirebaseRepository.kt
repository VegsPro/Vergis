package com.vergis.vegs.data

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import java.util.UUID

class FirebaseRepository {
    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseDatabase.getInstance().reference.child("vergis")

    val currentUser: FirebaseUser? get() = auth.currentUser

    suspend fun register(email: String, password: String, displayName: String): Result<FirebaseUser> {
        return try {
            val result = auth.createUserWithEmailAndPassword(email, password).await()
            val user = result.user!!
            val profile = UserProfile(uid = user.uid, email = email, displayName = displayName)
            db.child("users").child(user.uid).setValue(profile).await()
            Result.success(user)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun login(email: String, password: String): Result<FirebaseUser> {
        return try {
            val result = auth.signInWithEmailAndPassword(email, password).await()
            Result.success(result.user!!)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun logout() {
        auth.signOut()
    }

    suspend fun getDisplayName(uid: String): String {
        return try {
            val snap = db.child("users").child(uid).child("displayName").get().await()
            snap.getValue(String::class.java) ?: "Игрок"
        } catch (_: Exception) {
            "Игрок"
        }
    }

    fun observeRooms(): Flow<List<RoomInfo>> = callbackFlow {
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val rooms = mutableListOf<RoomInfo>()
                for (child in snapshot.children) {
                    val room = parseRoom(child) ?: continue
                    if (room.status == "waiting" || room.status == "playing") {
                        rooms.add(room)
                    }
                }
                trySend(rooms.sortedByDescending { it.createdAt })
            }
            override fun onCancelled(error: DatabaseError) {
                close(error.toException())
            }
        }
        db.child("rooms").addValueEventListener(listener)
        awaitClose { db.child("rooms").removeEventListener(listener) }
    }

    fun observeRoom(roomId: String): Flow<RoomInfo?> = callbackFlow {
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                trySend(parseRoom(snapshot))
            }
            override fun onCancelled(error: DatabaseError) {
                close(error.toException())
            }
        }
        db.child("rooms").child(roomId).addValueEventListener(listener)
        awaitClose { db.child("rooms").child(roomId).removeEventListener(listener) }
    }

    suspend fun createRoom(
        creatorName: String,
        maxPlayers: Int,
        theme: String,
        password: String
    ): Result<String> {
        val user = currentUser ?: return Result.failure(Exception("Не авторизован"))
        return try {
            val roomId = UUID.randomUUID().toString().take(8)
            val player = PlayerInfo(uid = user.uid, name = creatorName)
            val room = mapOf(
                "id" to roomId,
                "name" to creatorName,
                "creatorId" to user.uid,
                "maxPlayers" to maxPlayers,
                "theme" to theme,
                "password" to password,
                "status" to "waiting",
                "currentRound" to 0,
                "currentQuestion" to "",
                "createdAt" to System.currentTimeMillis(),
                "players" to mapOf(user.uid to player),
                "answers" to emptyMap<String, String>(),
                "readyForNext" to emptyMap<String, Boolean>()
            )
            db.child("rooms").child(roomId).setValue(room).await()
            Result.success(roomId)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun joinRoom(roomId: String, password: String, playerName: String): Result<Unit> {
        val user = currentUser ?: return Result.failure(Exception("Не авторизован"))
        return try {
            val snap = db.child("rooms").child(roomId).get().await()
            val room = parseRoom(snap) ?: return Result.failure(Exception("Комната не найдена"))
            if (room.status != "waiting") return Result.failure(Exception("Игра уже началась"))
            if (room.password.isNotEmpty() && room.password != password) {
                return Result.failure(Exception("Неверный пароль"))
            }
            if (room.players.size >= room.maxPlayers) {
                return Result.failure(Exception("Комната заполнена"))
            }
            if (room.players.containsKey(user.uid)) {
                return Result.success(Unit)
            }
            val player = PlayerInfo(uid = user.uid, name = playerName)
            db.child("rooms").child(roomId).child("players").child(user.uid).setValue(player).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun leaveRoom(roomId: String) {
        val user = currentUser ?: return
        try {
            db.child("rooms").child(roomId).child("players").child(user.uid).removeValue().await()
            // If creator left or empty, optionally delete, but keep for simplicity
            val snap = db.child("rooms").child(roomId).child("players").get().await()
            if (!snap.exists() || snap.childrenCount == 0L) {
                db.child("rooms").child(roomId).removeValue().await()
            }
        } catch (_: Exception) {}
    }

    suspend fun startGame(roomId: String, question: String) {
        val updates = mapOf(
            "status" to "playing",
            "currentRound" to 1,
            "currentQuestion" to question,
            "answers" to emptyMap<String, String>(),
            "readyForNext" to emptyMap<String, Boolean>()
        )
        db.child("rooms").child(roomId).updateChildren(updates).await()
    }

    suspend fun submitAnswer(roomId: String, answer: String) {
        val user = currentUser ?: return
        db.child("rooms").child(roomId).child("answers").child(user.uid).setValue(answer).await()
    }

    suspend fun setReadyForNext(roomId: String) {
        val user = currentUser ?: return
        db.child("rooms").child(roomId).child("readyForNext").child(user.uid).setValue(true).await()
    }

    suspend fun advanceRound(roomId: String, nextRound: Int, question: String) {
        val updates = mapOf(
            "currentRound" to nextRound,
            "currentQuestion" to question,
            "answers" to emptyMap<String, String>(),
            "readyForNext" to emptyMap<String, Boolean>()
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
            readyForNext = ready
        )
    }
}
