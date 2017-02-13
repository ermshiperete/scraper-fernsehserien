/*
 * Copyright 2017 Eberhard Beilharz
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

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tinymediamanager.scraper.MediaMetadata;
import org.tinymediamanager.scraper.MediaProviderInfo;
import org.tinymediamanager.scraper.MediaScrapeOptions;
import org.tinymediamanager.scraper.MediaSearchOptions;
import org.tinymediamanager.scraper.MediaSearchResult;
import org.tinymediamanager.scraper.UnsupportedMediaTypeException;
import org.tinymediamanager.scraper.entities.MediaArtwork;
import org.tinymediamanager.scraper.entities.MediaArtwork.MediaArtworkType;
import org.tinymediamanager.scraper.entities.MediaEpisode;
import org.tinymediamanager.scraper.entities.MediaGenres;
import org.tinymediamanager.scraper.mediaprovider.IMovieMetadataProvider;
import org.tinymediamanager.scraper.mediaprovider.ITvShowMetadataProvider;

import net.xeoh.plugins.base.annotations.PluginImplementation;

/**
 * The Class FernsehserienMetadataProvider. A meta data provider for the site fernsehserien.de
 */
@PluginImplementation
public class FernsehserienMetadataProvider implements ITvShowMetadataProvider {
	private static final Logger LOGGER = LoggerFactory.getLogger(org.tinymediamanager.scraper.fernsehserien.FernsehserienMetadataProvider.class);

	static final MediaProviderInfo providerInfo = createMediaProviderInfo();

	private FernsehserienSiteDefinition fernsehserienSite;

	public FernsehserienMetadataProvider() {
		fernsehserienSite = FernsehserienSiteDefinition.FERNSEHSERIEN_COM;

		// configure/load settings
		providerInfo.getConfig().addBoolean("useImdb", true);
		providerInfo.getConfig().addBoolean("localReleaseDate", true);

		providerInfo.getConfig().load();
	}

	private static MediaProviderInfo createMediaProviderInfo() {
		MediaProviderInfo mpi = new MediaProviderInfo("fernsehserien", "fernsehserien.de",
				"<html><h3>Fernsehserien.de</h3><br />Provides information about TV series shown on German TV.<br />Available languages: German</html>",
				org.tinymediamanager.scraper.fernsehserien.FernsehserienMetadataProvider.class.getResource("/fernsehserien_de.png"));
		mpi.setVersion(org.tinymediamanager.scraper.fernsehserien.FernsehserienMetadataProvider.class);
		return mpi;
	}

	@Override
	public MediaProviderInfo getProviderInfo() {
		return providerInfo;
	}

	@Override
	public MediaMetadata getMetadata(MediaScrapeOptions options) throws Exception {
		LOGGER.debug("****FERNSEHSERIEN: FernsehserienMetadataProvider.getMetadata() " + options.toString());

		switch (options.getType()) {
			case TV_SHOW:
				return (new FernsehserienTvShowParser(fernsehserienSite)).getTvShowMetadata("", options);

			case TV_EPISODE:
				return (new FernsehserienTvShowParser(fernsehserienSite)).getEpisodeMetadata("", options);

			default:
				throw new UnsupportedMediaTypeException(options.getType());
		}
	}

	@Override
	public List<MediaSearchResult> search(MediaSearchOptions query) throws Exception {
		LOGGER.debug("search() " + query.toString());

		switch (query.getMediaType()) {
			case TV_SHOW:
				return (new FernsehserienTvShowParser(fernsehserienSite)).search(query);

			default:
				throw new UnsupportedMediaTypeException(query.getMediaType());
		}
	}

	@Override
	public List<MediaEpisode> getEpisodeList(MediaScrapeOptions options) throws Exception {
		LOGGER.debug("getEpisodeList() " + options.toString());
		return new FernsehserienTvShowParser(fernsehserienSite).getEpisodeList(options);
	}

	static void processMediaArt(MediaMetadata md, MediaArtworkType type, String image) {
		MediaArtwork ma = new MediaArtwork(providerInfo.getId(), type);
		ma.setPreviewUrl(image);
		md.addMediaArt(ma);
	}

	static String cleanString(String oldString) {
		if (StringUtils.isEmpty(oldString)) {
			return "";
		}
		// remove non breaking spaces
		String newString = StringUtils.trim(oldString.replace(String.valueOf((char) 160), " "));

		// if there is a leading AND trailing quotation marks (e.g. at TV shows) - remove them
		if (newString.startsWith("\"") && newString.endsWith("\"")) {
			newString = StringUtils.stripEnd(StringUtils.stripStart(newString, "\""), "\"");
		}

		// and trim
		return newString;
	}

	/*
	 * Maps scraper Genres to internal TMM genres
	 */
	static MediaGenres getTmmGenre(String genre) {
		MediaGenres g = null;
		if (StringUtils.isBlank(genre)) {
			return null;
		}
		// @formatter:off
		else if (genre.equals("Action")) {
			g = MediaGenres.ACTION;
		} else if (genre.equals("Adventure")) {
			g = MediaGenres.ADVENTURE;
		} else if (genre.equals("Animation")) {
			g = MediaGenres.ANIMATION;
		} else if (genre.equals("Biography")) {
			g = MediaGenres.BIOGRAPHY;
		} else if (genre.equals("Comedy")) {
			g = MediaGenres.COMEDY;
		} else if (genre.equals("Crime")) {
			g = MediaGenres.CRIME;
		} else if (genre.equals("Documentary")) {
			g = MediaGenres.DOCUMENTARY;
		} else if (genre.equals("Drama")) {
			g = MediaGenres.DRAMA;
		} else if (genre.equals("Family")) {
			g = MediaGenres.FAMILY;
		} else if (genre.equals("Fantasy")) {
			g = MediaGenres.FANTASY;
		} else if (genre.equals("Film-Noir")) {
			g = MediaGenres.FILM_NOIR;
		} else if (genre.equals("Game-Show")) {
			g = MediaGenres.GAME_SHOW;
		} else if (genre.equals("History")) {
			g = MediaGenres.HISTORY;
		} else if (genre.equals("Horror")) {
			g = MediaGenres.HORROR;
		} else if (genre.equals("Music")) {
			g = MediaGenres.MUSIC;
		} else if (genre.equals("Musical")) {
			g = MediaGenres.MUSICAL;
		} else if (genre.equals("Mystery")) {
			g = MediaGenres.MYSTERY;
		} else if (genre.equals("News")) {
			g = MediaGenres.NEWS;
		} else if (genre.equals("Reality-TV")) {
			g = MediaGenres.REALITY_TV;
		} else if (genre.equals("Romance")) {
			g = MediaGenres.ROMANCE;
		} else if (genre.equals("Sci-Fi")) {
			g = MediaGenres.SCIENCE_FICTION;
		} else if (genre.equals("Sport")) {
			g = MediaGenres.SPORT;
		} else if (genre.equals("Talk-Show")) {
			g = MediaGenres.TALK_SHOW;
		} else if (genre.equals("Thriller")) {
			g = MediaGenres.THRILLER;
		} else if (genre.equals("War")) {
			g = MediaGenres.WAR;
		} else if (genre.equals("Western")) {
			g = MediaGenres.WESTERN;
		}
		// @formatter:on
		if (g == null) {
			g = MediaGenres.getGenre(genre);
		}
		return g;
	}
}
