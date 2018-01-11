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

import java.util.Arrays;
import java.util.List;

/**
 * A single search result
 */
public class SearchResult {
	public enum Types
	{
		Unknown,
		Series
	}

	private String a;
	private String t;
	private String l;
	private String s;
	private String b;
	private String c;

	public SearchResult()
	{
	}

	public Types getType()
	{
		switch (a)
		{
			case "s":
				return Types.Series;
		}
		return Types.Unknown;
	}

	public String getTitle()
	{
		return t;
	}

	public List<String> getCountries()
	{
		String[] parts = l.split(" ");
		String[] countries = parts[0].split("/");
		return Arrays.asList(countries);
	}

	public int getYear()
	{
		String[] parts = l.split(" ");
		if (parts.length < 2)
			return 0;

		String[] years = parts[1].split("–");
		if (years.length > 0) {
			return Integer.parseInt(years[0]);
		} else {
			return Integer.parseInt(parts[1]);
		}
	}

	public String getSeries()
	{
		return s;
	}

	public String getBannerUrl()
	{
		return b;
	}

	public String getCopyright()
	{
		return c;
	}
}
