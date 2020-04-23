package com.hatchways.assessment.api;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import static org.junit.Assert.*;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashSet;

class ApplicationTests {

	/// helper function to get the response code of the API endpoint
	public static int getStatus(String url) throws IOException {
		URL siteURL = new URL(url);
		HttpURLConnection connection = (HttpURLConnection) siteURL.openConnection();
		return connection.getResponseCode();
	}

	// helper function to get the response JSON of the API endpoint
	public JsonObject getResponseJson(String url) throws IOException {
		URL siteURL = new URL(url);
		HttpURLConnection connection = (HttpURLConnection) siteURL.openConnection();
		int responseCode = connection.getResponseCode();
		InputStream inputStream;
		if (200 <= responseCode && responseCode <= 299) {
			inputStream = connection.getInputStream();
		} else {
			inputStream = connection.getErrorStream();
		}
		JsonObject json;
		JsonElement element = new JsonParser().parse(new InputStreamReader(inputStream));
		json = element.getAsJsonObject();
		return json;
	}

	// check if JSON is sorted by its sortingKey
	public boolean isSorted(JsonObject json, String sortingKey, String direction) {
		double prev = json.get("posts").getAsJsonArray().get(0).getAsJsonObject().get(sortingKey).getAsDouble();
		for (JsonElement elem : json.get("posts").getAsJsonArray()) {
			if (direction.equals("asc")) {
				if (prev <= elem.getAsJsonObject().get(sortingKey).getAsDouble()) {
					prev = elem.getAsJsonObject().get(sortingKey).getAsDouble();
					continue;
				} else {
					return false;
				}
			}
			if (direction.equals("desc")) {
				if (prev >= elem.getAsJsonObject().get(sortingKey).getAsDouble()) {
					prev = elem.getAsJsonObject().get(sortingKey).getAsDouble();
				} else {
					return false;
				}
			}
		}
		return true;
	}

	// check if all posts are unique
	public boolean isUnique(JsonObject json) {
		HashSet<Integer> set = new HashSet<>();
		for (JsonElement elem : json.get("posts").getAsJsonArray()) {
			if (set.contains(elem.getAsJsonObject().get("id").getAsInt())) {
				return false;
			} else {
				set.add(elem.getAsJsonObject().get("id").getAsInt());
			}
		}
		return true;
	}

	// Test for status code for api/posts route
	@Test
	public void testPostAPI() throws Exception {
		String url = createURLWithPort("/api/posts?tags=tech", "8080");
		try {
			assertEquals(200,getStatus(url));
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	// Test for status code for api/ping route
	@Test
	public void testPingAPI() throws Exception {
		String url = createURLWithPort("/api/ping", "8080");
		try {
			assertEquals(200,getStatus(url));
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	// Test for proper error handling
	@Test
	void testErrorResponses() throws Exception {
		String url = createURLWithPort("/api/posts?tags=tech&sortBy=rubbish", "8080");
		JsonObject json = getResponseJson(url);
		assertEquals("{\"error\":\"sortBy parameter is invalid\"}", json.toString());
		url = createURLWithPort("/api/posts?tags=tech&sortBy=id&direction=rubbish", "8080");
		json = getResponseJson(url);
		assertEquals("{\"error\":\"direction parameter is invalid\"}", json.toString());
	}

	// Test for responses being sorted
	@Test
	void testSortedResponses() throws Exception {
		String url = createURLforSortTest("id", "asc", "8080", "/api/posts?tags=tech,history");
		JsonObject json = getResponseJson(url);
		assertEquals(isSorted(json,"id", "asc"), true);

		url = createURLforSortTest("id", "desc", "8080", "/api/posts?tags=tech,history");
		json = getResponseJson(url);
		assertEquals(isSorted(json,"id", "desc"), true);

		url = createURLforSortTest("reads", "asc", "8080", "/api/posts?tags=tech,history");
		json = getResponseJson(url);
		assertEquals(isSorted(json,"reads", "asc"), true);

		url = createURLforSortTest("reads", "desc", "8080", "/api/posts?tags=tech,history");
		json = getResponseJson(url);
		assertEquals(isSorted(json,"reads", "desc"), true);

		url = createURLforSortTest("likes", "asc", "8080", "/api/posts?tags=tech,history");
		json = getResponseJson(url);
		assertEquals(isSorted(json,"likes", "asc"), true);

		url = createURLforSortTest("likes", "desc", "8080", "/api/posts?tags=tech,history");
		json = getResponseJson(url);
		assertEquals(isSorted(json,"likes", "desc"), true);

		url = createURLforSortTest("popularity", "asc", "8080", "/api/posts?tags=tech,history");
		json = getResponseJson(url);
		assertEquals(isSorted(json,"popularity", "asc"), true);

		url = createURLforSortTest("popularity", "desc", "8080", "/api/posts?tags=tech,history");
		json = getResponseJson(url);
		assertEquals(isSorted(json,"popularity", "desc"), true);
	}

	@Test
	void testUniqueEntries() throws Exception {
		String url = createURLWithPort("/api/posts?tags=tech,history,design", "8080");
		JsonObject json = getResponseJson(url);
		assertEquals(isUnique(json), true);
	}
	// helper function to create url for all tests
	private String createURLWithPort(String uri, String port) {
		return "http://localhost:" + port + uri;
	}

	// helper function to create url for sorted test
	private String createURLforSortTest(String sortingKey, String direction, String port, String uri) {
		return "http://localhost:" + port + uri + "&sortBy=" + sortingKey + "&direction=" + direction;
	}
}
