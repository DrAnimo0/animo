package eu.kanade.tachiyomi.animeextension.en.hianimez

import android.app.Application
import android.content.SharedPreferences
import androidx.preference.ListPreference
import androidx.preference.PreferenceScreen
import eu.kanade.tachiyomi.animesource.ConfigurableAnimeSource
import eu.kanade.tachiyomi.animesource.model.AnimeFilter
import eu.kanade.tachiyomi.animesource.model.AnimeFilterList
import eu.kanade.tachiyomi.animesource.model.SAnime
import eu.kanade.tachiyomi.animesource.model.SEpisode
import eu.kanade.tachiyomi.animesource.model.Video
import eu.kanade.tachiyomi.animesource.online.ParsedAnimeHttpSource
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.util.asJsoup
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class HiAnimeZ : ParsedAnimeHttpSource(), ConfigurableAnimeSource {

    override val name = "HiAnimeZ"
    override val baseUrl = "https://hianimez.to"
    override val lang = "en"
    override val supportsLatest = true
    
    private val preferences: SharedPreferences by lazy {
        Injekt.get<Application>().getSharedPreferences("source_$id", 0x0000)
    }

    // Popular Anime
    override fun popularAnimeRequest(page: Int): Request = GET("$baseUrl/trending?page=$page")
    
    override fun popularAnimeSelector() = "div.film_list-wrap div.flw-item"
    
    override fun popularAnimeFromElement(element: Element): SAnime {
        val anime = SAnime.create()
        anime.setUrlWithoutDomain(element.selectFirst("div.film-poster a")!!.attr("href"))
        anime.thumbnail_url = element.selectFirst("div.film-poster img")!!.attr("data-src")
        anime.title = element.selectFirst("div.film-detail h3.film-name a")!!.text()
        return anime
    }
    
    override fun popularAnimeNextPageSelector() = "div.pagination li.page-item a[title=Next]"

    // Latest Anime
    override fun latestUpdatesRequest(page: Int): Request = GET("$baseUrl/latest-added?page=$page")
    
    override fun latestUpdatesSelector() = popularAnimeSelector()
    
    override fun latestUpdatesFromElement(element: Element) = popularAnimeFromElement(element)
    
    override fun latestUpdatesNextPageSelector() = popularAnimeNextPageSelector()

    // Search Anime
    override fun searchAnimeRequest(page: Int, query: String, filters: AnimeFilterList): Request {
        return GET("$baseUrl/search?keyword=$query&page=$page")
    }
    
    override fun searchAnimeSelector() = popularAnimeSelector()
    
    override fun searchAnimeFromElement(element: Element) = popularAnimeFromElement(element)
    
    override fun searchAnimeNextPageSelector() = popularAnimeNextPageSelector()

    // Anime Details
    override fun animeDetailsParse(document: Document): SAnime {
        val anime = SAnime.create()
        val infoElement = document.selectFirst("div.detail_page-watch")!!
        
        anime.title = infoElement.selectFirst("h2.film-name")!!.text()
        anime.thumbnail_url = infoElement.selectFirst("div.film-poster img")!!.attr("src")
        anime.description = infoElement.selectFirst("div.film-description p.description")?.text()
        anime.genre = infoElement.select("div.film-stats div.meta div:contains(Genre) a").joinToString { it.text() }
        anime.status = parseStatus(infoElement.selectFirst("div.film-stats div.meta div:contains(Status) span")?.text())
        
        return anime
    }
    
    private fun parseStatus(statusString: String?): Int {
        return when (statusString) {
            "Completed" -> SAnime.COMPLETED
            "Ongoing" -> SAnime.ONGOING
            else -> SAnime.UNKNOWN
        }
    }

    // Episodes
    override fun episodeListParse(response: Response): List<SEpisode> {
        val document = response.asJsoup()
        val episodeList = mutableListOf<SEpisode>()
        
        document.select("div.ss-list a.ssl-item").forEach { element ->
            val episode = SEpisode.create()
            val episodeNumber = element.text().trim().substringAfter("Ep ").toFloatOrNull() ?: 0f
            
            episode.episode_number = episodeNumber
            episode.name = "Episode $episodeNumber"
            episode.setUrlWithoutDomain(element.attr("href"))
            episodeList.add(episode)
        }
        
        return episodeList.reversed()
    }
    
    override fun episodeListSelector() = "div.ss-list a.ssl-item"
    
    override fun episodeFromElement(element: Element): SEpisode {
        val episode = SEpisode.create()
        val episodeNumber = element.text().trim().substringAfter("Ep ").toFloatOrNull() ?: 0f
        
        episode.episode_number = episodeNumber
        episode.name = "Episode $episodeNumber"
        episode.setUrlWithoutDomain(element.attr("href"))
        
        return episode
    }

    // Video URLs
    override fun videoListParse(response: Response): List<Video> {
        val document = response.asJsoup()
        val videoList = mutableListOf<Video>()
        
        // This is a simplified implementation
        // You may need to add JavaScript evaluation to extract actual sources
        document.select("div.player-servers ul li").forEach { serverElement ->
            val serverUrl = baseUrl + serverElement.attr("data-server-id")
            // For each server, you'd fetch the actual video URL
            // This is a placeholder - real implementation would get actual video URLs
            videoList.add(Video(serverUrl, "Server ${serverElement.text()}", serverUrl))
        }
        
        return videoList
    }
    
    override fun videoListSelector() = throw UnsupportedOperationException("Not used")
    
    override fun videoFromElement(element: Element) = throw UnsupportedOperationException("Not used")
    
    override fun videoUrlParse(document: Document) = throw UnsupportedOperationException("Not used")

    // Filters
    override fun getFilterList() = AnimeFilterList(
        AnimeFilter.Header("Note: Filters are not implemented yet")
    )

    // Preferences
    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        val videoQualityPref = ListPreference(screen.context).apply {
            key = PREF_QUALITY_KEY
            title = "Preferred quality"
            entries = QUALITY_ENTRIES
            entryValues = QUALITY_VALUES
            setDefaultValue(QUALITY_DEFAULT)
            summary = "%s"
            
            setOnPreferenceChangeListener { _, newValue ->
                val selected = newValue as String
                val index = findIndexOfValue(selected)
                val entry = entryValues[index] as String
                preferences.edit().putString(key, entry).commit()
            }
        }
        
        screen.addPreference(videoQualityPref)
    }
    
    companion object {
        private const val PREF_QUALITY_KEY = "preferred_quality"
        private val QUALITY_ENTRIES = arrayOf("1080p", "720p", "480p", "360p")
        private val QUALITY_VALUES = arrayOf("1080", "720", "480", "360")
        private const val QUALITY_DEFAULT = "1080"
    }
}
