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

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.*;

import java.text.SimpleDateFormat;
import java.util.List;
import java.util.Locale;

import org.apache.commons.lang3.LocaleUtils;
import org.junit.Test;
import org.tinymediamanager.scraper.MediaMetadata;
import org.tinymediamanager.scraper.MediaScrapeOptions;
import org.tinymediamanager.scraper.MediaSearchOptions;
import org.tinymediamanager.scraper.MediaSearchResult;
import org.tinymediamanager.scraper.entities.CountryCode;
import org.tinymediamanager.scraper.entities.MediaCastMember.CastType;
import org.tinymediamanager.scraper.entities.MediaEpisode;
import org.tinymediamanager.scraper.entities.MediaLanguages;
import org.tinymediamanager.scraper.entities.MediaType;
import org.tinymediamanager.scraper.mediaprovider.ITvShowMetadataProvider;

public class FernsehserienMetadataProviderTest {

	@Test
	public void testTvShowSearch_MultipleResults() throws Exception {
		// Die Deutschen
		ITvShowMetadataProvider mp = new FernsehserienMetadataProvider();
		MediaSearchOptions options = new MediaSearchOptions(MediaType.TV_SHOW, "Die Deutschen");
		options.setLanguage(Locale.GERMAN);
		List<MediaSearchResult> results = mp.search(options);
		// did we get a result?
		assertNotNull("Result", results);

		// should get more than one result
		assertThat(results.size()).isGreaterThan(1);
	}

	@Test
	public void testTvShowSearch_OneResult() throws Exception {
		ITvShowMetadataProvider mp = new FernsehserienMetadataProvider();
		MediaSearchOptions options = new MediaSearchOptions(MediaType.TV_SHOW, "Malcolm mittendrin");
		options.setLanguage(Locale.GERMAN);
		List<MediaSearchResult> results = mp.search(options);
		// did we get a result?
		assertNotNull("Result", results);

		assertEquals("Result count", 1, results.size());
		MediaSearchResult singleResult = results.get(0);
		assertEquals("Malcolm mittendrin", singleResult.getTitle());
		assertEquals("malcolm-mittendrin", singleResult.getId());
		assertEquals(1999, singleResult.getYear());
		assertEquals(1, singleResult.getMediaMetadata().getCountries().size());
		assertEquals("USA", singleResult.getMediaMetadata().getCountries().get(0));
		assertEquals("In dieser Comedyserie dreht alles um Malcolm (Frankie Muniz), einen Teenager, dessen Leben durch seinen überdurchschnittlich hohen Intelligenzquotienten und seine durchgedrehte Familie völlig neben der Spur verläuft. Seine Mutter ist streng, rechthaberisch und kontrollsüchtig, sein Vater ein liebevoller, aber kindischer Chaot, sein ältester Bruder plant seinen eigenen Privatkrieg auf einer Militärakademie und reist dann nach Alaska, um bloß nicht zu Hause sein zu müssen, und von seinen anderen beiden Brüdern ist der eine ein nicht besonders heller Raufbold und der andere eine eigenbrötlerische Nervensäge. Am Ende der vierten Staffel bekommt Malcolms Mutter erneut männlichen Nachwuchs, jedoch verhält sich das Baby im Gegensatz zum Rest der Familie geradezu verdächtig ruhig. Malcolm versucht, sein Leben so gut es geht in den Griff zu kriegen, steht aber insbesondere bei seinen Begegnungen mit dem weiblichen Geschlecht immer wieder vor großen Rätseln.",
				singleResult.getMediaMetadata().getPlot());
		assertEquals("Malcolm In The Middle", singleResult.getMediaMetadata().getOriginalTitle());
		assertEquals("Malcolm In The Middle", singleResult.getOriginalTitle());
		assertEquals("2001-09-24", new SimpleDateFormat("yyyy-MM-dd").format(singleResult.getMediaMetadata().getReleaseDate()));
		assertNotNull(singleResult.getPosterUrl());
	}

	@Test
	public void testEpisodeListing() throws Exception{
		ITvShowMetadataProvider mp = null;
		List<MediaEpisode> episodes = null;

	    /*
	     * Malcom mittendrin
	     */
		mp = new FernsehserienMetadataProvider();
		MediaScrapeOptions options = new MediaScrapeOptions(MediaType.TV_SHOW);
		options.setLanguage(Locale.GERMAN);
		options.setId(mp.getProviderInfo().getId(), "malcolm-mittendrin");

		episodes = mp.getEpisodeList(options);

		// did we get a result?
		assertNotNull("Episodes", episodes);

		// result count
		assertEquals("Episodes count", 151, episodes.size());
	}

	@Test
	public void testTvShowScrape() throws Exception {
		ITvShowMetadataProvider mp = null;
		MediaScrapeOptions options = null;
		MediaMetadata md = null;

	    /*
	     * Malcom mittendrin
	     */

		mp = new FernsehserienMetadataProvider();
		options = new MediaScrapeOptions(MediaType.TV_SHOW);
		options.setId("", "malcolm-mittendrin");
		options.setCountry(CountryCode.DE);
		options.setLanguage(LocaleUtils.toLocale(MediaLanguages.de.name()));
		md = mp.getMetadata(options);

		// did we get metadata?
		assertNotNull("MediaMetadata", md);

		assertEquals(1999, md.getYear());
		assertEquals(1, md.getCountries().size());
		assertEquals("USA", md.getCountries().get(0));
		assertEquals("In dieser Comedyserie dreht alles um Malcolm (Frankie Muniz), einen Teenager, dessen Leben durch seinen überdurchschnittlich hohen Intelligenzquotienten und seine durchgedrehte Familie völlig neben der Spur verläuft. Seine Mutter ist streng, rechthaberisch und kontrollsüchtig, sein Vater ein liebevoller, aber kindischer Chaot, sein ältester Bruder plant seinen eigenen Privatkrieg auf einer Militärakademie und reist dann nach Alaska, um bloß nicht zu Hause sein zu müssen, und von seinen anderen beiden Brüdern ist der eine ein nicht besonders heller Raufbold und der andere eine eigenbrötlerische Nervensäge. Am Ende der vierten Staffel bekommt Malcolms Mutter erneut männlichen Nachwuchs, jedoch verhält sich das Baby im Gegensatz zum Rest der Familie geradezu verdächtig ruhig. Malcolm versucht, sein Leben so gut es geht in den Griff zu kriegen, steht aber insbesondere bei seinen Begegnungen mit dem weiblichen Geschlecht immer wieder vor großen Rätseln.",
				md.getPlot());
		assertEquals("Malcolm In The Middle", md.getOriginalTitle());
		assertEquals("2001-09-24", new SimpleDateFormat("yyyy-MM-dd").format(md.getReleaseDate()));
		assertEquals("Ended", md.getStatus());
	}

	@Test
	public void testEpisodeScrape() throws Exception {
		ITvShowMetadataProvider mp = null;
		MediaScrapeOptions options = null;
		MediaMetadata md = null;
		SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");

	    /*
	     * Malcom mittendrin
	     */
		mp = new FernsehserienMetadataProvider();
		options = new MediaScrapeOptions(MediaType.TV_EPISODE);
		options.setId(mp.getProviderInfo().getId(), "malcolm-mittendrin");
		options.setCountry(CountryCode.DE);
		options.setLanguage(LocaleUtils.toLocale(MediaLanguages.de.name()));
		options.setId(MediaMetadata.SEASON_NR, "1");
		options.setId(MediaMetadata.EPISODE_NR, "1");
		md = mp.getMetadata(options);

		// did we get metadata?
		assertNotNull("MediaMetadata", md);

		assertEquals("Malcolm, der Held", md.getTitle());
		assertEquals(
				"Auf Anordnung seiner Mutter Lois muss der neunjährige Malcolm sich mit Stevie treffen, einem an den Rollstuhl gefesselten hoch begabten Schüler. Stevie besucht eine Spezialklasse, in der nur besonders intelligente Kinder unterrichtet werden. Weil die anderen Kinder diese verachten, will auch Malcolm zunächst mit Stevie nichts zu tun haben. Doch es kommt noch schlimmer: Weil auch Malcolm überdurchschnittlich intelligent ist, muss er schon bald die Klasse wechseln – zu Stevie. (Text: ProSieben)\n",
				md.getPlot());
		assertEquals("2001-09-24", sdf.format(md.getReleaseDate()));
		assertEquals("Pilot", md.getOriginalTitle());
		assertEquals(19, md.getCastMembers(CastType.ACTOR).size());
	}

}
