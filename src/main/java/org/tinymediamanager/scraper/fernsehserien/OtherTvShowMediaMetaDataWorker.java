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

import org.tinymediamanager.scraper.MediaMetadata;
import org.tinymediamanager.scraper.MediaScrapeOptions;
import org.tinymediamanager.scraper.mediaprovider.ITvShowMetadataProvider;
import org.tinymediamanager.scraper.util.PluginManager;

import java.util.List;
import java.util.concurrent.Callable;

class OtherTvShowMediaMetaDataWorker implements Callable<MediaMetadata> {
	private MediaScrapeOptions options;
	private String otherProviderName;

	public OtherTvShowMediaMetaDataWorker(String otherProviderName, MediaScrapeOptions options) {
		this.options = options;
		this.otherProviderName = otherProviderName;
	}

	@Override
	public MediaMetadata call() throws Exception {
		try {
			ITvShowMetadataProvider otherProvider = null;
			List<ITvShowMetadataProvider> providers = PluginManager.getInstance().getPluginsForInterface(ITvShowMetadataProvider.class);
			for (ITvShowMetadataProvider provider : providers) {
				if (otherProviderName.equals(provider.getProviderInfo().getId())) {
					otherProvider = provider;
					break;
				}
			}
			if (otherProvider == null) {
				return null;
			}

			return otherProvider.getMetadata(options);
		}
		catch (Exception e) {
			return null;
		}
	}
}
