 package com.github.andreyasadchy.xtra.util
 
 import android.content.Context
 import android.content.SharedPreferences
 import android.os.Build
 import android.util.Log
 import androidx.security.crypto.EncryptedSharedPreferences
 import androidx.security.crypto.MasterKey
 
 object SecureTokenStorage {
     private const val TAG = "SecureTokenStorage"
     private const val SECURE_PREFS_NAME = "secure_token_prefs"
     private const val LEGACY_PREFS_NAME = "prefs2"
     private const val MIGRATION_COMPLETE_KEY = "migration_complete"
 
     @Volatile
     private var securePrefsInstance: SharedPreferences? = null
 
     fun getSecurePrefs(context: Context): SharedPreferences {
         return securePrefsInstance ?: synchronized(this) {
             securePrefsInstance ?: createSecurePrefs(context).also {
                 securePrefsInstance = it
                 migrateFromLegacyPrefs(context, it)
             }
         }
     }
 
     private fun createSecurePrefs(context: Context): SharedPreferences {
         return try {
             val masterKey = MasterKey.Builder(context)
                 .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                 .build()
 
             EncryptedSharedPreferences.create(
                 context,
                 SECURE_PREFS_NAME,
                 masterKey,
                 EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                 EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
             )
         } catch (e: Exception) {
             Log.e(TAG, "Failed to create EncryptedSharedPreferences, falling back to regular prefs", e)
             context.getSharedPreferences(LEGACY_PREFS_NAME, Context.MODE_PRIVATE)
         }
     }
 
     private fun migrateFromLegacyPrefs(context: Context, securePrefs: SharedPreferences) {
         if (securePrefs.getBoolean(MIGRATION_COMPLETE_KEY, false)) {
             return
         }
 
         val legacyPrefs = context.getSharedPreferences(LEGACY_PREFS_NAME, Context.MODE_PRIVATE)
         val allEntries = legacyPrefs.all
 
         if (allEntries.isEmpty()) {
             securePrefs.edit().putBoolean(MIGRATION_COMPLETE_KEY, true).apply()
             return
         }
 
         try {
             securePrefs.edit().apply {
                 for ((key, value) in allEntries) {
                     when (value) {
                         is String -> putString(key, value)
                         is Int -> putInt(key, value)
                         is Long -> putLong(key, value)
                         is Float -> putFloat(key, value)
                         is Boolean -> putBoolean(key, value)
                         is Set<*> -> {
                             @Suppress("UNCHECKED_CAST")
                             putStringSet(key, value as Set<String>)
                         }
                     }
                 }
                 putBoolean(MIGRATION_COMPLETE_KEY, true)
                 apply()
             }
 
             legacyPrefs.edit().clear().apply()
             Log.i(TAG, "Successfully migrated ${allEntries.size} entries to secure storage")
         } catch (e: Exception) {
             Log.e(TAG, "Failed to migrate legacy prefs to secure storage", e)
         }
     }
 }
