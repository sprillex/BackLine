package com.example.offlinebrowser.data.network

import android.content.Context
import com.example.offlinebrowser.data.local.OfflineDatabase
import com.example.offlinebrowser.data.local.TrustedServerDao
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import java.net.Socket
import java.security.MessageDigest
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocket
import javax.net.ssl.X509ExtendedTrustManager
import javax.net.ssl.X509TrustManager

class FingerprintTrustManager(private val trustedServerDao: TrustedServerDao) : X509ExtendedTrustManager() {

    override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?, socket: Socket?) {
        // We don't verify client certs
    }

    override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?, engine: javax.net.ssl.SSLEngine?) {
        // We don't verify client certs
    }

    override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {
        // We don't verify client certs
    }

    override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?, socket: Socket?) {
        verifyServer(chain, socket?.inetAddress?.hostAddress, socket?.port ?: -1)
    }

    override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?, engine: javax.net.ssl.SSLEngine?) {
        verifyServer(chain, engine?.peerHost, engine?.peerPort ?: -1)
    }

    override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {
        // Fallback for when socket/engine is not available.
        // We can't verify against DB without IP/Port.
        // For security, strictly we should fail if we mandate pinning,
        // OR we can try to rely on standard CA check if strictly needed.
        // But since we are targeting self-signed local servers, we likely fail here.
        throw CertificateException("Cannot verify server without hostname/port information.")
    }

    private fun verifyServer(chain: Array<out X509Certificate>?, ip: String?, port: Int) {
        if (chain.isNullOrEmpty()) {
            throw CertificateException("Certificate chain is empty")
        }
        if (ip == null) {
             throw CertificateException("Server IP is null")
        }

        // 1. Get Leaf Certificate
        val leaf = chain[0]

        // 2. Calculate SHA-256 Fingerprint
        val digest = MessageDigest.getInstance("SHA-256")
        val hashBytes = digest.digest(leaf.encoded)
        val fingerprint = bytesToHex(hashBytes)

        // 3. Check DB
        // Blocking call is required here as TrustManager is synchronous.
        // Ideally we cache this or use a memory store populated at app start,
        // but for now runBlocking is acceptable on network threads.
        val trustedServer = runBlocking {
             // Try to match specific port first, or default
             trustedServerDao.getTrustedServer(ip, port)
                 ?: trustedServerDao.getTrustedServer(ip, 0) // Optional: allow wildcard port if stored as 0
        }

        if (trustedServer != null) {
            // Check if fingerprint matches
            if (!trustedServer.fingerprint.equals(fingerprint, ignoreCase = true)) {
                throw CertificateException("Certificate pinning failure. Expected: ${trustedServer.fingerprint}, Found: $fingerprint")
            }
            // If matches, we trust it.
        } else {
            // Not in DB.
            // If this is a self-signed cert, we fail.
            // If we wanted to allow valid CAs, we would delegate to system TrustManager here.
            // But the requirement implies we are dealing with self-signed.
            // However, allowing system trusted CAs is good practice.
            // I'll skip system delegation for simplicity unless requested, to ensure we catch self-signed cases.
            // Actually, "The app is not able to connect to an insecure server" implies we ONLY want to support this secure flow
            // OR standard secure servers.
            // Let's throw for now to force the QR flow.
            throw CertificateException("Server $ip:$port is not trusted. Please scan QR code to add trust.")
        }
    }

    private fun bytesToHex(bytes: ByteArray): String {
        val hexChars = "0123456789ABCDEF"
        val result = StringBuilder(bytes.size * 2)
        for (byte in bytes) {
            val i = byte.toInt()
            result.append(hexChars[i shr 4 and 0x0f])
            result.append(hexChars[i and 0x0f])
            result.append(":")
        }
        return result.toString().dropLast(1) // Remove trailing colon
    }
}

object SafeClientFactory {
    fun create(context: Context): OkHttpClient {
        val database = OfflineDatabase.getDatabase(context)
        val trustManager = FingerprintTrustManager(database.trustedServerDao())

        val sslContext = SSLContext.getInstance("TLS")
        sslContext.init(null, arrayOf(trustManager), null)

        return OkHttpClient.Builder()
            .sslSocketFactory(sslContext.socketFactory, trustManager)
            .hostnameVerifier { _, _ -> true } // We verify via Pinning in TrustManager, so HostnameVerifier can be lenient for IP/Self-Signed
            .build()
    }
}
