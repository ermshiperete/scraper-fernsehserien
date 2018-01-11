/*
 * Copyright 2017-2018 Eberhard Beilharz
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.tinymediamanager.scraper.fernsehserien;

import static org.tinymediamanager.scraper.fernsehserien.FernsehserienMetadataProvider.cleanString;
import static org.tinymediamanager.scraper.fernsehserien.FernsehserienMetadataProvider.providerInfo;

import java.net.InterfaceAddress;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.Charset;
import java.util.*;
import java.util.concurrent.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.google.gson.Gson;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.LocaleUtils;
import org.apache.commons.lang3.StringUtils;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tinymediamanager.scraper.MediaMetadata;
import org.tinymediamanager.scraper.MediaScrapeOptions;
import org.tinymediamanager.scraper.MediaSearchOptions;
import org.tinymediamanager.scraper.MediaSearchResult;
import org.tinymediamanager.scraper.entities.*;
import org.tinymediamanager.scraper.http.CachedUrl;
import org.tinymediamanager.scraper.util.MetadataUtil;

/**
 * The class FernsehserienTvShowParser is used to parse TV show site of fernsehserien.com
 *
 */
public class FernsehserienTvShowParser {
	private static final Logger LOGGER = LoggerFactory.getLogger(org.tinymediamanager.scraper.fernsehserien.FernsehserienTvShowParser.class);
	private static final ExecutorService executor = Executors.newFixedThreadPool(4);

	private FernsehserienSiteDefinition fernsehserienSite;

	private final MediaType type;

	public FernsehserienTvShowParser(FernsehserienSiteDefinition fernsehserienSite) {
		this.type = MediaType.TV_SHOW;
		this.fernsehserienSite = fernsehserienSite;
	}

	protected Logger getLogger() {
		return LOGGER;
	}

	protected FernsehserienSiteDefinition getFernsehserienSite() {
		return fernsehserienSite;
	}

	/**
	 * do the search according to the type
	 *
	 * @param query the search params
	 * @return the found results
	 */
	protected List<MediaSearchResult> search(MediaSearchOptions query) throws Exception {
		String searchTerm = "";

		if (StringUtils.isNotEmpty(query.getQuery())) {
			searchTerm = query.getQuery();
		}

		if (StringUtils.isEmpty(searchTerm)) {
			searchTerm = query.getQuery();
		}

		if (StringUtils.isEmpty(searchTerm)) {
			return new ArrayList<>();
		}

		searchTerm = MetadataUtil.removeNonSearchCharacters(searchTerm);

		getLogger().debug("========= BEGIN FERNSEHSERIEN Scraper Search for: " + searchTerm);

		List<MediaSearchResult> result = tryFastSearch(query, searchTerm);

		if (result.size() >= 10) {
			// we got too many results. Try the extended search
			result = tryFullSearch(query, searchTerm);
		}

		Collections.sort(result);
		Collections.reverse(result);

		getLogger().debug("========= END FERNSEHSERIEN Scraper Search for: " + searchTerm);
		return result;
	}

	private List<MediaSearchResult> tryFastSearch(MediaSearchOptions query, String searchTerm) throws Exception {
		List<MediaSearchResult> result = new ArrayList<>();
		SearchResult[] searchResults;
		String country = query.getCountry().getAlpha2(); // for passing the country to the scrape

		StringBuilder sb = new StringBuilder(getFernsehserienSite().getSite());
		sb.append("fastsearch?suchwort=");
		sb.append(URLEncoder.encode(searchTerm, "UTF-8"));

		try {
			URI url = new URI(sb.toString());
			//url.addHeader("Accept-Language", getAcceptLanguage(language, country));

			String searchResultString = IOUtils.toString(url, (Charset) null);
			Gson gson = new Gson();
			searchResults = gson.fromJson(searchResultString, SearchResult[].class);

		} catch (Exception e) {
			getLogger().debug("tried to fetch search response", e);
			return result;
		}

		for (SearchResult singleResult : searchResults) {
			MediaScrapeOptions options = new MediaScrapeOptions(MediaType.TV_SHOW);
			options.setId("fernsehserien", singleResult.getSeries());
			options.setLanguage(query.getLanguage());
			options.setCountry(CountryCode.valueOf(country));

			MediaSearchResult sr = new MediaSearchResult(FernsehserienMetadataProvider.providerInfo.getId(), MediaType.TV_SHOW);
			sr.setTitle(singleResult.getTitle());
			sr.setId(singleResult.getSeries());
			sr.setScore(1);
			sr.setPosterUrl(singleResult.getBannerUrl());
			sr.setYear(singleResult.getYear());
			MediaMetadata metadata = getMetadata(singleResult.getSeries(), options);
			sr.setOriginalTitle(metadata.getOriginalTitle());
			sr.setMetadata(metadata);
			result.add(sr);

			// only get 40 results
			if (result.size() >= 40) {
				break;
			}
		}
		return result;
	}

	private List<MediaSearchResult> tryFullSearch(MediaSearchOptions query, String searchTerm) throws Exception {
		List<MediaSearchResult> result = new ArrayList<>();

		StringBuilder sb = new StringBuilder(getFernsehserienSite().getSite());
		sb.append("suche/");
		sb.append(URLEncoder.encode(searchTerm, "UTF-8"));

		try {
			CachedUrl url = new CachedUrl(sb.toString());
			url.addHeader("Accept-Language", getAcceptLanguage(query.getLanguage().getLanguage(), query.getCountry().getAlpha2()));
			Document doc = Jsoup.parse(url.getInputStream(), fernsehserienSite.getCharset().displayName(), "");

			for (Element elem : doc.getElementsByClass("suchergebnis"))
			{
				String series = elem.getElementsByTag("a").first().
						attr("href").substring(1); // trim leading /
				String title = elem.getElementsByClass("suchergebnis-titel").first().text();
				String banner = elem.getElementsByClass("suchergebnis-bild").first().
						getElementsByTag("img").first().attr("src");
				String wannwo = elem.getElementsByClass("suchergebnis-wannwo").first().ownText();
				String year = extractNumber(wannwo);

				MediaSearchResult searchResult = new MediaSearchResult(FernsehserienMetadataProvider.providerInfo.getId(), MediaType.TV_SHOW);
				searchResult.setTitle(title);
				searchResult.setId(series);
				searchResult.setScore(1);
				searchResult.setPosterUrl(banner);
				searchResult.setYear(Integer.parseInt(year));
				result.add(searchResult);

				// only get 40 results
				if (result.size() >= 40) {
					break;
				}
			}

		} catch (Exception e) {
			getLogger().debug("tried to fetch search response", e);
		}

		return result;
	}

	private String extractNumber(String str)
	{
		str = trimStart(str);
		for (int i = 0; i < str.length(); i++) {
			if (!Character.isDigit(str.charAt(i)))
				return str.substring(0, i);
		}
		return str;
	}

	private String trimStart(String str)
	{
		for (int i = 0; i < 10; i++) {
			if (str.startsWith(Integer.toString(i))) {
				return str;
			}
		}
		return trimStart(str.substring(1));
	}

	private void addOtherProvider(MediaSearchOptions options, MediaMetadata md) throws Exception {
		try {
			MediaSearchOptions movieOptions = new MediaSearchOptions(MediaType.MOVIE, options.getQuery());
			movieOptions.setCountry(options.getCountry());
			movieOptions.setLanguage(options.getLanguage());
			movieOptions.setYear(md.getYear());

			Future<List<MediaSearchResult>> futureTheTvDb = getFutureTvShow("useTheTvDb", "tvdb", options);
			Future<List<MediaSearchResult>> futureTmdb = getFutureTvShow("useTmdb", "tmdb", options);
			Future<List<MediaSearchResult>> futureTmdbMovie = getFutureMovie("useTmdb", "tmdb", movieOptions);
			Future<List<MediaSearchResult>> futureImdb = getFutureTvShow("useImdb", "imdb", options);

			String providerName = md.getId("GenreProvider").toString();
			if (StringUtils.isBlank(providerName)) {
				if (searchSingleProvider(futureTheTvDb, "tvdb", options, md))
					return;
				if (searchSingleProvider(futureTmdb, "tmdb", options, md)) {
					md.setId("tmdbKind", "tvshow");
					return;
				 }
				if (searchSingleProvider(futureTmdbMovie, "tmdb", movieOptions, md)) {
					md.setId("tmdbKind", "movie");
					return;
				}
				searchSingleProvider(futureImdb, "imdb", options, md);
			} else {
				switch (providerName)
				{
					case "tvdb":
						searchSingleProvider(futureTheTvDb, "tvdb", options, md);
						return;
					case "tmdb":
						if (searchSingleProvider(futureTmdb, "tmdb", options, md))
							return;
						searchSingleProvider(futureTmdbMovie, "tmdb", movieOptions, md);
						return;
					case "imdb":
						searchSingleProvider(futureImdb, "imdb", options, md);
						return;
				}
			}
		} catch (Exception e) {
			getLogger().debug("Got exception: " + e);
		}
	}

	private Future<List<MediaSearchResult>> getFutureTvShow(String key, String providerName, MediaSearchOptions options) {
		if (FernsehserienMetadataProvider.providerInfo.getConfig().getValueAsBool(key)) {
			ExecutorCompletionService<List<MediaSearchResult>> completionService = new ExecutorCompletionService<>(executor);
			Callable<List<MediaSearchResult>> worker = new OtherTvShowSearchWorker(providerName, options);
			return completionService.submit(worker);
		}
		return null;
	}

	private Future<List<MediaSearchResult>> getFutureMovie(String key, String providerName, MediaSearchOptions options) {
		if (FernsehserienMetadataProvider.providerInfo.getConfig().getValueAsBool(key)) {
			ExecutorCompletionService<List<MediaSearchResult>> completionService = new ExecutorCompletionService<>(executor);
			Callable<List<MediaSearchResult>> worker = new OtherMovieSearchWorker(providerName, options);
			return completionService.submit(worker);
		}
		return null;
	}

	private Boolean searchSingleProvider(Future<List<MediaSearchResult>> future, String providerName, MediaSearchOptions options, MediaMetadata md) {
		if (future != null) {
			try {
				if (!StringUtils.isBlank(md.getId("GenreProvider").toString()) &&
						!StringUtils.isBlank(md.getId(md.getId("GenreProvider").toString()).toString())) {
					// searched before - no need to do it again
					return true;
				}
				List<MediaSearchResult> results = future.get();
				MediaSearchResult singleResult = null;
				if (results == null || results.size() == 0)
					return false;
				if (results.size() == 1) {
					singleResult = results.get(0);
				}
				else {
					for (MediaSearchResult result : results) {
						if ((options.getQuery().equalsIgnoreCase(result.getTitle()) ||
								options.getQuery().equalsIgnoreCase(result.getOriginalTitle())) &&
								result.getYear() == options.getYear()) {
							singleResult = result;
							break;
						}
					}
				}
				if (singleResult != null) {
					md.setId("GenreProvider", providerName);
					md.setId(providerName, singleResult.getId());
					if ("tmdb".equals(providerName)) {
						options.setTmdbId(Integer.parseInt(singleResult.getId()));
					}
					options.setImdbId(singleResult.getIMDBId());
					if (!StringUtils.isBlank(singleResult.getPosterUrl())) {
						MediaArtwork media = new MediaArtwork(providerName, MediaArtwork.MediaArtworkType.POSTER);
						media.setDefaultUrl(singleResult.getPosterUrl());
						md.addMediaArt(media);
					}
					md.setId("GenreProviderResult", singleResult);
					return true;
				}
			}
			catch (Exception ignored) {
				getLogger().debug("Got exception trying to search " + providerName + ": " + ignored);
			}
		}
		return false;
	}

	/*
	 * generates the accept-language http header for fernsehserien
	*/
	protected static String getAcceptLanguage(String language, String country) {
		List<String> languageString = new ArrayList<>();

		// first: take the preferred language from settings,
		// but validate whether it is legal or not
		if (StringUtils.isNotBlank(language) && StringUtils.isNotBlank(country)) {
			if (LocaleUtils.isAvailableLocale(new Locale(language, country))) {
				String combined = language + "-" + country;
				languageString.add(combined.toLowerCase(Locale.ROOT));
			}
		}

		// also build langu & default country
		Locale localeFromLanguage = null; //UrlUtil.getLocaleFromLanguage(language);
		if (localeFromLanguage != null) {
			String combined = language + "-" + localeFromLanguage.getCountry().toLowerCase(Locale.ROOT);
			if (!languageString.contains(combined)) {
				languageString.add(combined);
			}
		}

		if (StringUtils.isNotBlank(language)) {
			languageString.add(language.toLowerCase(Locale.ROOT));
		}

		// second: the JRE language
		Locale jreLocale = Locale.getDefault();
		String combined = (jreLocale.getLanguage() + "-" + jreLocale.getCountry()).toLowerCase(Locale.ROOT);
		if (!languageString.contains(combined)) {
			languageString.add(combined);
		}

		if (!languageString.contains(jreLocale.getLanguage().toLowerCase(Locale.ROOT))) {
			languageString.add(jreLocale.getLanguage().toLowerCase(Locale.ROOT));
		}

		// third: fallback to en
		if (!languageString.contains("en-us")) {
			languageString.add("en-us");
		}
		if (!languageString.contains("en")) {
			languageString.add("en");
		}

		// build a http header for the preferred language
		StringBuilder languages = new StringBuilder();
		float qualifier = 1f;

		for (String line : languageString) {
			if (languages.length() > 0) {
				languages.append(",");
			}
			languages.append(line);
			if (qualifier < 1) {
				languages.append(String.format(Locale.US, ";q=%1.1f", qualifier));
			}
			qualifier -= 0.1;
		}

		return languages.toString().toLowerCase(Locale.ROOT);
	}

	protected MediaMetadata getMetadata(String fernsehserienId, MediaScrapeOptions options) throws Exception {
		LOGGER.debug("FERNSEHSERIEN: getMetadata for " + options.getType());
		switch (options.getType()) {
			case TV_SHOW:
				return getTvShowMetadata(fernsehserienId, options);

			case TV_EPISODE:
				return getEpisodeMetadata(fernsehserienId, options);

			default:
				break;
		}
		return new MediaMetadata(providerInfo.getId());
	}

	/**
	 * get the TV show metadata
	 *
	 * @param fernsehserienId the id of the series
	 * @param options the scrape options
	 * @return the MediaMetadata
	 * @throws Exception
	 */
	MediaMetadata getTvShowMetadata(String fernsehserienId, MediaScrapeOptions options) throws Exception {
		MediaMetadata md = new MediaMetadata(providerInfo.getId());

		if (fernsehserienId == null || fernsehserienId.isEmpty()) {
			// fernsehserienId from searchResult
			if (options.getResult() != null) {
				fernsehserienId = options.getResult().getId();
			}
		}

		// fernsehserienid from scraper option
		if (fernsehserienId == null || fernsehserienId.isEmpty()) {
			fernsehserienId = options.getId("");
		}
		if (fernsehserienId == null || fernsehserienId.isEmpty()) {
			LOGGER.debug("FERNSEHSERIEN: empty fernsehserienId; returning");
			return md;
		}

		LOGGER.debug("FERNSEHSERIEN: getTvShowMetadata(fernsehserienId): " + fernsehserienId);

		// get combined data
		CachedUrl url = new CachedUrl(fernsehserienSite.getSite() + fernsehserienId);
		url.addHeader("Accept-Language", getAcceptLanguage(options.getLanguage().getLanguage(), options.getCountry().getAlpha2()));
		Document doc = Jsoup.parse(url.getInputStream(), fernsehserienSite.getCharset().displayName(), "");

		parseInfoPage(doc, options, md);

		// populate id
		md.setId(FernsehserienMetadataProvider.providerInfo.getId(), fernsehserienId);

		addGenres(md, options);

		return md;
	}

	private void addGenres(MediaMetadata metadata, MediaScrapeOptions options) {
		try {
			MediaSearchOptions searchOptions = new MediaSearchOptions(MediaType.TV_SHOW);
			searchOptions.setQuery(metadata.getTitle());
			searchOptions.setCountry(options.getCountry());
			searchOptions.setLanguage(options.getLanguage());
			searchOptions.setYear(metadata.getYear());

			addOtherProvider(searchOptions, metadata);
			options.setImdbId(searchOptions.getImdbId());
			options.setTmdbId(searchOptions.getTmdbId());
			for (Map.Entry<String, Object> kv : metadata.getIds().entrySet()) {
				options.setId(kv.getKey(), kv.getValue().toString());
			}
			options.setMetadata(metadata);
		} catch (Exception e) {
			getLogger().debug("Got exception adding other provider: " + e);
		}

		String providerName = metadata.getId("GenreProvider").toString();
		if (StringUtils.isBlank(providerName))
			return;
		String providerId = metadata.getId(providerName).toString();
		if (StringUtils.isBlank(providerId)) {
			providerId = options.getImdbId();
		}
		if (StringUtils.isBlank(providerId))
			return;

		Callable<MediaMetadata> worker = null;
		Boolean isMovie = "movie".equals(metadata.getId("tmdbKind"));
		ExecutorCompletionService<MediaMetadata> completionService = new ExecutorCompletionService<>(executor);
		MediaScrapeOptions newOptions = new MediaScrapeOptions(isMovie ? MediaType.MOVIE : MediaType.TV_SHOW);
		newOptions.setMetadata(metadata);
		newOptions.setImdbId(options.getImdbId());
		newOptions.setTmdbId(options.getTmdbId());
		newOptions.setLanguage(options.getLanguage());
		newOptions.setCountry(options.getCountry());
		newOptions.setId(providerName, providerId);
		newOptions.setResult((MediaSearchResult)metadata.getId("GenreProviderResult"));
		if (isMovie) {
			worker = new OtherMovieMediaMetaDataWorker(providerName, newOptions);

		} else {
			worker = new OtherTvShowMediaMetaDataWorker(providerName, newOptions);
		}
		Future<MediaMetadata> future = completionService.submit(worker);

		try {
			MediaMetadata otherMetadata = future.get();
			if (otherMetadata != null) {
				for (MediaGenres genre : otherMetadata.getGenres()) {
					metadata.addGenre(genre);
				}
			}
		}
		catch (Exception e) {
			getLogger().debug("Got exception trying to get metadata from " + providerName + ": " + e);
		}
	}

	protected MediaMetadata parseInfoPage(Document doc, MediaScrapeOptions options, MediaMetadata md) {
		/* <div id="stickyheader" style="" class="">
			<div id="stickyheaderrand"></div>
			<ul class="serie-header serie-header-banner">
				<li class="infos">
					<h1>Die Deutschen</h1>
					<div class="serie-produktionsjahre">
						<abbr title="Deutschland">D</abbr>&nbsp;2008–2010
					</div>
				</li>
				<li class="serie-header-benachrichtigung">
					<a title="kostenlose E-Mail-Benachrichtigung bei TV-Ausstrahlung oder DVD-Veröffentlichung" class="newsletter-button" target="_blank" onclick="window.open(this.href,'Benachrichtigung','width=450,height=660,toolbar=no,menubar=no,location=no,scrollbar=no');_gaq.push(['_trackEvent', 'wl-serienstart', fs_interner_code, fs_serien_id]);return false;" href="https://www.wunschliste.de/benachrichtigung.pl?s=12765&amp;nd=1&amp;u=f"></a></li><li class="serie-header-suche"><form onsubmit="return submit_suche(this);"><input autocomplete="off" id="input-suchbegriff-2" maxlength="50" required="" name="suchbegriff" placeholder="Serie suchen" onkeyup="fast_search(event,this)" onfocus="fast_search(null,this)" onclick="fast_search(null,this)" onsearch="fast_search(null,this)" type="search"><button type="submit"><img src="/img/btn_suche.svg"></button><ul id="suggestlist-2" data-applied="0"></ul></form> </li><li class="serie-header-suchknopf"><img src="/img/btn_suche.svg" onclick="document.getElementsByTagName('header').item(0).classList.add('show-search');document.getElementById('input-suchbegriff').scrollIntoView();document.getElementById('input-suchbegriff').focus();"></li>
			</ul>
			</div>
			...
			<article>
				<div class="serie-top-infos">
					<div class="serie-infos-ausstrahlungsformen">
						<div class="serie-infos-ausstrahlungsform">
							<a href="/die-deutschen/episodenguide/staffel-1/9311">bisher 20 Folgen in 2 Staffeln</a>
						</div>
						<div class="serie-infos-ausstrahlungsform">
							<a href="/die-deutschen/episodenguide/0/12963">Specials</a>
						</div>
					</div>
					<div class="serie-infos-erstausstrahlung">Deutsche Erstausstrahlung: 26.10.2008
						<span class="no-wrap">ZDF</span>
					</div>
					<div class="serie-infos-alternativtitel">Alternativtitel: Die Deutschen I / Die Deutschen II</div>
					<div class="serie-info-wrapper wrapped" id="serie-info-wrapper">
						<div id="serie-info">
							<p>Zeitreise durch die Jahrhunderte, von Otto dem Großen im 10. Jahrhundert bis zur Ausrufung der ersten Republik durch
								Philipp Scheidemann im November 1918. Zehnteilige Dokumenationsreihe, die nach prägenden Figuren der deutschen Geschichte
								gegliedert ist und Einblicke in die verschiedenen Epochen gewährt.
								<i>(Text:&nbsp;ZDF)</i>
							</p>
						</div>
					</div>
					<div class="serie-beziehungsart">gezeigt bei
						<a class="bold" href="/terra-x">Terra X</a>
					</div>
					<div style="margin-top:10px;" class="fs-btn-container">
						<a class="fs-btn" href="/die-deutschen/episodenguide">Übersicht mit allen Folgen</a>
					</div>
				</div>
			</article>
		 */
		Element element = doc.getElementsByClass("serie-header").first()
			.getElementsByClass("infos").first().getElementsByTag("h1").first();
		if (element != null) {
			md.setTitle(cleanString(element.text()));
		}
		element = doc.getElementsByClass("serie-produktionsjahre").first();
		if (element != null) {
			Elements countries = element.getElementsByTag("abbr");
			for (Element country: countries) {
				md.addCountry(country.text());
				country.remove();
			}
			String text = element.text().replace("\n", "").replaceFirst("^(\\s|\u00A0)*(/(\\s|\u00A0)*)?", "");
			String[] parts = text.split(" ");
			if (parts != null && parts.length > 0) {
				String[] years = parts[0].split("–");
				if (years.length > 0) {
					md.setYear(Integer.parseInt(years[0]));
					if (years.length > 1 && Integer.parseInt(years[1]) <= Calendar.getInstance().get(Calendar.YEAR)) {
						md.setStatus("Ended");
					}
				} else {
					md.setYear(Integer.parseInt(parts[0]));
				}
				if (parts.length > 1) {
					// set original title
					Pattern titlePattern = Pattern.compile("\\(([^)]+)\\)");
					Matcher matcher = titlePattern.matcher(element.text());
					if (matcher.find()) {
						md.setOriginalTitle(matcher.group(1));
					}
				}
			}
		}
		element = doc.getElementsByClass("serie-infos-erstausstrahlung").first();
		if (element != null) {
			String content = element.text();

			// search year
			Pattern yearPattern = Pattern.compile("([0-9]{2}).([0-9]{2}).([0-9]{4})");
			Matcher matcher = yearPattern.matcher(content);
			if (matcher.find()) {
				int day = Integer.parseInt(matcher.group(1));
				int month = Integer.parseInt(matcher.group(2)) - 1;
				int year = Integer.parseInt(matcher.group(3));
				Calendar cal = new GregorianCalendar(year, month, day);
				Date date = cal.getTime();
				md.setReleaseDate(date);
			}
		}
		element = doc.getElementById("serie-info");
		if (element != null) {
			md.setPlot(element.text());
		}
		return md;
	}

	/**
	 * get the episode metadata.
	 *
	 * @param fernsehserienId the id of the series
	 * @param options the scrape options
	 * @return the MediaMetaData
	 * @throws Exception
	 */
	MediaMetadata getEpisodeMetadata(String fernsehserienId, MediaScrapeOptions options) throws Exception {
		LOGGER.debug("FERNSEHSERIEN: getEpisodeMetadata(fernsehserienId): " + providerInfo.getId());
		MediaMetadata md = new MediaMetadata(providerInfo.getId());

		if (StringUtils.isBlank(fernsehserienId)) {
			fernsehserienId = options.getId("fernsehserien");
		}
		if (StringUtils.isBlank(fernsehserienId)) {
			return md;
		}

		// get episode number and season number
		int seasonNr = -1;
		int episodeNr = -1;

		try {
			seasonNr = Integer.parseInt(options.getId(MediaMetadata.SEASON_NR));
			episodeNr = Integer.parseInt(options.getId(MediaMetadata.EPISODE_NR));
		} catch (Exception e) {
			LOGGER.warn("error parsing season/episode number");
		}

		if (seasonNr == -1 || episodeNr == -1) {
			return md;
		}

		md.setSeasonNumber(seasonNr);
		md.setEpisodeNumber(episodeNr);

		LOGGER.debug("FERNSEHSERIEN: getEpisodeMetadata(): Looking for season " + seasonNr + ", episodeNr " + episodeNr);
		MediaEpisode wantedEpisode = findEpisode(options, seasonNr, episodeNr);

		// we did not find the episode; return
		if (wantedEpisode == null) {
			return md;
		}

		// then parse the actors page to get the rest
		CachedUrl url = new CachedUrl(fernsehserienSite.getSite() + "/" + wantedEpisode.ids.get(providerInfo.getId()));
		url.addHeader("Accept-Language", getAcceptLanguage(options.getLanguage().getLanguage(), options.getCountry().getAlpha2()));
		Document doc = Jsoup.parse(url.getInputStream(), fernsehserienSite.getCharset().displayName(), "");

		md.setTitle(wantedEpisode.title);
		Pattern yearPattern = Pattern.compile("([0-9]{2}).([0-9]{2}).([0-9]{4})");
		Matcher matcher = yearPattern.matcher(wantedEpisode.firstAired);
		if (matcher.find()) {
			int day = Integer.parseInt(matcher.group(1));
			int month = Integer.parseInt(matcher.group(2)) - 1;
			int year = Integer.parseInt(matcher.group(3));
			Calendar cal = new GregorianCalendar(year, month, day);
			Date date = cal.getTime();
			md.setReleaseDate(date);
		}
		md.setId(providerInfo.getId(), wantedEpisode.ids.get(providerInfo.getId()));
		Pattern titlePattern = Pattern.compile("\\(([^)]+)\\)");
		Element content = doc.getElementsByClass("episode-output-originaltitel").first();
		if (content != null) {
			matcher = titlePattern.matcher(content.text());
			if (matcher.find()) {
				md.setOriginalTitle(matcher.group(1));
			}
		}
		content = doc.getElementsByClass("episode-output-instaffel").first();
		if (content != null) {
			matcher = titlePattern.matcher(content.text());
			if (matcher.find()) {
				String[] parts = matcher.group(1).split(" ");
				md.setRuntime(Integer.parseInt(parts[0]));
			}
		}

		Elements contentParas = doc.getElementsByClass("episode-output-inhalt").first().getElementsByTag("p");
		if (contentParas != null) {
			String plot = "";
			for (Element element : contentParas) {
				plot += element.text() + "\n";
			}
			md.setPlot(plot);
		}
		content = doc.getElementsByClass("episodenguide-episode-img-div").first();
		if (content != null) {
			Element img = content.getElementsByTag("img").first();
			MediaArtwork artwork = new MediaArtwork(providerInfo.getId(), MediaArtwork.MediaArtworkType.THUMB);
			artwork.setDefaultUrl(img.attributes().get("src"));
			md.addMediaArt(artwork);
		}
		Elements castCrewTables = doc.getElementsByClass("cast-crew");
		for (Element element: castCrewTables) {
			String what = element.previousElementSibling().text();
			MediaCastMember.CastType whatType = what.equals("Cast") ? MediaCastMember.CastType.ACTOR : MediaCastMember.CastType.OTHER;
			Elements rows = element.getElementsByClass("ep-hover");
			for (Element row: rows) {
				MediaCastMember member = new MediaCastMember();
				member.setType(whatType);
				member.setId(row.attributes().get("href"));
				member.setName(row.getElementsByClass("schauspieler").first().text());
				if (what.equals("Crew")) {
					for (Element bemerkung : row.getElementsByClass("bemerkung")) {
						switch (bemerkung.text()) {
							case "Produktion":
								member.setType(MediaCastMember.CastType.PRODUCER);
								break;
							case "Regie":
								member.setType(MediaCastMember.CastType.DIRECTOR);
								break;
							case "Drehbuch":
								member.setType(MediaCastMember.CastType.WRITER);
								break;
						}
						member.setCharacter(bemerkung.text());
					}
				}
				else {
					member.setCharacter(row.getElementsByClass("rolle").first().text());
				}
				Element imageElement = row.getElementsByClass("bild").first();
				Elements images = imageElement.getElementsByTag("img");
				if (images.size() > 0) {
					member.setImageUrl(images.first().attributes().get("src"));
				}
				md.addCastMember(member);
			}
		}

		return md;
	}

	/**
	 * find an episode in the episode overview
	 *
	 * @param options the scrape options
	 * @param seasonNr the season no
	 * @param episodeNr the episode
	 * @return the episode or null if not found
	 * @throws Exception
	 */
	MediaEpisode findEpisode(MediaScrapeOptions options, int seasonNr, int episodeNr) throws Exception {
		// parse the episodes from the ratings overview page (e.g.
		// https://www.fernsehserien.de/malcolm-mittendrin/episodenguide )
		String fernsehserienId = options.getId("fernsehserien");
		if (StringUtils.isBlank(fernsehserienId)) {
			return null;
		}

		CachedUrl url = new CachedUrl(fernsehserienSite.getSite() + fernsehserienId + "/episodenguide");
		url.addHeader("Accept-Language", getAcceptLanguage(options.getLanguage().getLanguage(), options.getCountry().getAlpha2()));
		Document doc = Jsoup.parse(url.getInputStream(), fernsehserienSite.getCharset().displayName(), "");

		Elements episodeElements = doc.getElementsByAttributeValue("itemprop", "episode");
		for (Element episode : episodeElements) {
			// 	1		1.	01		Malcolm, der Held	24.09.2001	Pilot	09.01.2000
			Elements numbers = episode.getElementsByClass("episodenliste-episodennummer");
			MediaEpisode me = new MediaEpisode(providerInfo.getId());
			me.ids.put(providerInfo.getId(), numbers.get(0).attributes().get("data-href"));
			String seasonStr = numbers.get(1).text();
			if (StringUtils.isBlank(seasonStr)) {
				// Specials
				me.season = 0;
				if (StringUtils.isBlank(numbers.get(0).text()))
					me.episode = 0;
				else
					me.episode = Integer.parseInt(numbers.get(0).text());
			} else {
				me.season = Integer.parseInt(seasonStr.substring(0, seasonStr.length() - 1)); // remove .
				me.episode = Integer.parseInt(numbers.get(2).text());
			}
			Element title = episode.getElementsByClass("episodenliste-titel").first();
			me.title = title.getElementsByAttributeValue("itemprop", "name").text();
			me.firstAired = episode.getElementsByClass("episodenliste-ea").first().text();

			if (me.season == seasonNr && me.episode == episodeNr) {
				return me;
			}
		}

		return null;
	}

	/**
	 * parse the episode list from the ratings overview
	 *
	 * @param options the scrape options
	 * @return the episode list
	 * @throws Exception
	 */
	List<MediaEpisode> getEpisodeList(MediaScrapeOptions options) throws Exception {
		List<MediaEpisode> episodes = new ArrayList<>();

		// parse the episodes from the ratings overview page (e.g.
		// https://www.fernsehserien.de/malcolm-mittendrin/episodenguide )
		String fernsehserienId = options.getId("fernsehserien");
		if (StringUtils.isBlank(fernsehserienId)) {
			return episodes;
		}

		CachedUrl url = new CachedUrl(fernsehserienSite.getSite() + "/" + fernsehserienId + "/episodenguide");
		url.addHeader("Accept-Language", getAcceptLanguage(options.getLanguage().getLanguage(), options.getCountry().getAlpha2()));
		Document doc = Jsoup.parse(url.getInputStream(), fernsehserienSite.getCharset().displayName(), "");

		Elements episodeElements = doc.getElementsByAttributeValue("itemprop", "episode");
		for (Element episode : episodeElements) {
			// 	1		1.	01		Malcolm, der Held	24.09.2001	Pilot	09.01.2000
			Elements numbers = episode.getElementsByClass("episodenliste-episodennummer");
			MediaEpisode me = new MediaEpisode(providerInfo.getId());
			me.ids.put(providerInfo.getId(), numbers.get(0).attributes().get("data-href"));
			String seasonStr = numbers.get(1).text();
			if (StringUtils.isBlank(seasonStr)) {
				// Specials
				me.season = 0;
				if (StringUtils.isBlank(numbers.get(0).text()))
					me.episode = 0;
				else
					me.episode = Integer.parseInt(numbers.get(0).text());
			} else {
				me.season = Integer.parseInt(seasonStr.substring(0, seasonStr.length() - 1)); // remove .
				me.episode = Integer.parseInt(numbers.get(2).text());
			}
			Element title = episode.getElementsByClass("episodenliste-titel").first();
			me.title = title.getElementsByAttributeValue("itemprop", "name").text();
			me.firstAired = episode.getElementsByClass("episodenliste-ea").first().text();

			episodes.add(me);
		}

		return episodes;
	}
}
