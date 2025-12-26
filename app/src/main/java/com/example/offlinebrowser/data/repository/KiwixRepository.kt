package com.example.offlinebrowser.data.repository

import com.example.offlinebrowser.data.model.KiwixBook
import com.example.offlinebrowser.data.network.KiwixService
import com.rometools.rome.io.SyndFeedInput
import org.xml.sax.InputSource
import retrofit2.Retrofit
import retrofit2.converter.scalars.ScalarsConverterFactory
import java.io.StringReader

class KiwixRepository {
    private val kiwixService: KiwixService

    init {
        val retrofit = Retrofit.Builder()
            .baseUrl("https://library.kiwix.org/")
            .addConverterFactory(ScalarsConverterFactory.create())
            .build()

        kiwixService = retrofit.create(KiwixService::class.java)
    }

    suspend fun search(query: String): Result<List<KiwixBook>> {
        return try {
            val xml = kiwixService.search(query)
            val inputSource = InputSource(StringReader(xml))
            val feed = SyndFeedInput().build(inputSource)

            val books = feed.entries.mapNotNull { entry ->
                // OPDS uses link rel="http://opds-spec.org/acquisition" for acquisition
                // type should be "application/x-zim"

                val downloadLink = entry.links.find { link ->
                    link.type == "application/x-zim" ||
                    link.href.endsWith(".zim") ||
                    link.rel.contains("acquisition")
                }

                if (downloadLink != null) {
                    KiwixBook(
                        title = entry.title,
                        description = entry.description?.value ?: "",
                        downloadUrl = downloadLink.href,
                        size = if (downloadLink.length > 0) formatSize(downloadLink.length) else "Unknown",
                        language = entry.foreignMarkup.find { it.name == "language" }?.text ?: ""
                    )
                } else {
                    null
                }
            }
            Result.success(books)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun formatSize(bytes: Long): String {
        val kb = bytes / 1024
        val mb = kb / 1024
        val gb = mb / 1024
        return if (gb > 0) "$gb GB" else if (mb > 0) "$mb MB" else "$kb KB"
    }
}
