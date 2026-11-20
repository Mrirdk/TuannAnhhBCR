package com.stats.baccaratoverlay

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

class AccountCoinManager(context: Context) {

    private val prefs = context.getSharedPreferences("autorobot_v5_accounts", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_USERS_JSON = "KEY_USERS_JSON"
        private const val KEY_CURRENT_USER = "KEY_CURRENT_USER"
        private const val KEY_REDEEMED_CODES = "KEY_REDEEMED_CODES"
        const val KEY_ADMIN_CONTACT = "Zalo/Telegram: @TuannAnhhBCR"
    }

    init {
        ensureDefaultAccounts()
    }

    private fun ensureDefaultAccounts() {
        val raw = prefs.getString(KEY_USERS_JSON, null)
        val arr = if (raw.isNullOrBlank()) JSONArray() else JSONArray(raw)
        var hasDemo = false
        var hasAdmin = false

        for (i in 0 until arr.length()) {
            val obj = arr.getJSONObject(i)
            when (obj.optString("user").lowercase()) {
                "vip01" -> hasDemo = true
                "admin" -> hasAdmin = true
            }
        }

        if (!hasDemo) {
            arr.put(JSONObject().apply {
                put("user", "vip01")
                put("pass", "123456")
                put("coins", 999)
                put("role", "vip")
            })
        }

        if (!hasAdmin) {
            arr.put(JSONObject().apply {
                put("user", "admin")
                put("pass", "admin123")
                put("coins", 99999)
                put("role", "admin")
            })
        }

        prefs.edit().putString(KEY_USERS_JSON, arr.toString()).apply()
    }

    fun login(username: String, password: String): Pair<Boolean, String> {
        val cleanUser = username.trim()
        val cleanPass = password.trim()
        if (cleanUser.isEmpty() || cleanPass.isEmpty()) {
            return false to "Vui lòng nhập đầy đủ tài khoản và mật khẩu!"
        }

        val arr = JSONArray(prefs.getString(KEY_USERS_JSON, "[]"))
        for (i in 0 until arr.length()) {
            val obj = arr.getJSONObject(i)
            if (obj.optString("user").equals(cleanUser, ignoreCase = true)) {
                return if (obj.optString("pass") == cleanPass) {
                    val exactUser = obj.optString("user")
                    prefs.edit().putString(KEY_CURRENT_USER, exactUser).apply()
                    true to "Chào mừng $exactUser đến với TuannAnhh BCR V9!"
                } else {
                    false to "Sai mật khẩu! Vui lòng kiểm tra lại."
                }
            }
        }
        return false to "Tài khoản không tồn tại! Liên hệ $KEY_ADMIN_CONTACT"
    }

    fun logout() {
        prefs.edit().remove(KEY_CURRENT_USER).apply()
    }

    fun getCurrentUser(): String? = prefs.getString(KEY_CURRENT_USER, null)

    fun isAdmin(username: String? = getCurrentUser()): Boolean {
        return username.equals("admin", ignoreCase = true)
    }

    fun getCoins(username: String? = getCurrentUser()): Int {
        if (username.isNullOrBlank()) return 0
        if (isAdmin(username)) return 99999
        val arr = JSONArray(prefs.getString(KEY_USERS_JSON, "[]"))
        for (i in 0 until arr.length()) {
            val obj = arr.getJSONObject(i)
            if (obj.optString("user").equals(username, ignoreCase = true)) {
                return obj.optInt("coins", 0)
            }
        }
        return 0
    }

    fun addCoins(username: String, delta: Int): Int {
        val arr = JSONArray(prefs.getString(KEY_USERS_JSON, "[]"))
        var updatedCoins = 0
        for (i in 0 until arr.length()) {
            val obj = arr.getJSONObject(i)
            if (obj.optString("user").equals(username, ignoreCase = true)) {
                val oldCoins = obj.optInt("coins", 0)
                updatedCoins = (oldCoins + delta).coerceAtLeast(0)
                obj.put("coins", updatedCoins)
                break
            }
        }
        prefs.edit().putString(KEY_USERS_JSON, arr.toString()).apply()
        return updatedCoins
    }

    fun consumeOneCoinForTable(): Boolean {
        val user = getCurrentUser() ?: return false
        if (isAdmin(user)) return true
        val current = getCoins(user)
        if (current <= 0) return false
        addCoins(user, -1)
        return true
    }

    fun redeemKey(codeRaw: String): Pair<Boolean, String> {
        val user = getCurrentUser() ?: return false to "Bạn cần đăng nhập trước!"
        val code = codeRaw.trim().uppercase()
        if (code.isEmpty()) return false to "Vui lòng nhập mã Key!"

        val bonus = when (code) {
            "VIP10" -> 10
            "PRO30" -> 30
            "V8VIP" -> 100
            "TUANNANHH9" -> 150
            else -> return false to "Mã Key không hợp lệ!"
        }

        val redeemedSet = prefs.getStringSet(KEY_REDEEMED_CODES, emptySet())?.toMutableSet() ?: mutableSetOf()
        val userCodeToken = "${user.lowercase()}_$code"
        if (redeemedSet.contains(userCodeToken)) {
            return false to "Tài khoản này đã sử dụng mã $code rồi!"
        }

        redeemedSet.add(userCodeToken)
        prefs.edit().putStringSet(KEY_REDEEMED_CODES, redeemedSet).apply()
        val total = addCoins(user, bonus)
        return true to "Nạp thành công +$bonus Xu! Số dư: $total Xu"
    }

    fun createOrAddCoinsToUser(targetUserRaw: String, targetPassRaw: String, coinsToAdd: Int): Pair<Boolean, String> {
        val targetUser = targetUserRaw.trim()
        if (targetUser.isEmpty()) return false to "Tên tài khoản không được để trống!"
        val finalPass = targetPassRaw.trim().ifEmpty { "123456" }
        val safeCoins = coinsToAdd.coerceAtLeast(0)

        val arr = JSONArray(prefs.getString(KEY_USERS_JSON, "[]"))
        for (i in 0 until arr.length()) {
            val obj = arr.getJSONObject(i)
            if (obj.optString("user").equals(targetUser, ignoreCase = true)) {
                val newTotal = obj.optInt("coins", 0) + safeCoins
                obj.put("coins", newTotal)
                if (targetPassRaw.trim().isNotEmpty()) {
                    obj.put("pass", finalPass)
                }
                prefs.edit().putString(KEY_USERS_JSON, arr.toString()).apply()
                return true to "Đã cộng +$safeCoins Xu cho $targetUser (Tổng: $newTotal Xu)"
            }
        }

        arr.put(JSONObject().apply {
            put("user", targetUser)
            put("pass", finalPass)
            put("coins", safeCoins)
            put("role", "vip")
        })
        prefs.edit().putString(KEY_USERS_JSON, arr.toString()).apply()
        return true to "Đã tạo mới $targetUser (Pass: $finalPass | $safeCoins Xu)"
    }
}
