package com.alaa.universe

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.AtomicFile
import java.io.File
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** All persisted provider material is authenticated ciphertext, excluded from Android backup. */
class CredentialVault internal constructor(context: Context, private val testKeyProvider: (() -> SecretKey)?, private val namespace: String) {
    internal constructor(context: Context, testKeyProvider: (() -> SecretKey)?) : this(context, testKeyProvider, "provider-v1")
    constructor(context: Context) : this(context, null, "provider-v1")
    companion object { fun named(context: Context, namespace: String) = CredentialVault(context, null, namespace) }
    private val root = File(context.noBackupFilesDir, namespace).apply { mkdirs() }
    private val alias = context.packageName + if (namespace == "provider-v1") ".provider.v1" else "." + namespace
    private fun file(name: String): AtomicFile {
        require(Regex("[a-zA-Z0-9_-]{1,100}").matches(name))
        return AtomicFile(File(root, name))
    }
    private fun key(): SecretKey {
        testKeyProvider?.let { return it() }
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        return (store.getKey(alias, null) as? SecretKey) ?: KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore"
        ).apply {
            init(KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
        }.generateKey()
    }
    @Synchronized fun read(name: String): String? {
        val f = file(name)
        if (!f.baseFile.exists() && !File(f.baseFile.path + ".bak").exists()) return null
        try {
            val bytes = f.openRead().use { input ->
                val out = java.io.ByteArrayOutputStream()
                val buffer = ByteArray(8192)
                while (true) {
                    val n = input.read(buffer); if (n < 0) break
                    require(out.size() + n <= 64 * 1024 * 1024)
                    out.write(buffer, 0, n)
                }
                out.toByteArray()
            }
            require(bytes.size >= 29 && bytes[0] == 1.toByte())
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, bytes.copyOfRange(1, 13)))
            cipher.updateAAD(name.toByteArray(Charsets.UTF_8))
            return String(cipher.doFinal(bytes.copyOfRange(13, bytes.size)), Charsets.UTF_8)
        } catch (_: Exception) { throw IllegalStateException("Stored provider data unavailable. Please reconnect.") }
    }
    @Synchronized fun write(name: String, value: String) {
        try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, key())
            cipher.updateAAD(name.toByteArray(Charsets.UTF_8))
            val bytes = byteArrayOf(1) + cipher.iv + cipher.doFinal(value.toByteArray(Charsets.UTF_8))
            require(bytes.size <= 64 * 1024 * 1024)
            val f = file(name)
            val stream = f.startWrite()
            try { stream.write(bytes); f.finishWrite(stream) }
            catch (e: Exception) { f.failWrite(stream); throw e }
        } catch (_: Exception) { throw IllegalStateException("Could not securely save provider data.") }
    }
    @Synchronized internal fun delete(name: String) { file(name).delete() }
    @Synchronized fun clear() {
        root.listFiles()?.forEach { it.delete() }
        if (testKeyProvider == null) {
            val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
            if (store.containsAlias(alias)) store.deleteEntry(alias)
        }
    }
}
