package external;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.http.HttpEntity;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import entity.Item.ItemBuilder;
import entity.Item;

public class GitHubClient {
	//github client has not position in Taipei
	//lat is for the description
	//https://www.google.com/search?q=github+api&oq=github+api&aqs=chrome..69i57j0l5j69i60l2.1431j0j4&sourceid=chrome&ie=UTF-8
	private static final String URL_TEMPLATE = "https://jobs.github.com/positions.json?description=%s&lat=%s&long=%s";
	private static final String DEFAULT_KEYWORD = "developer";
	
	//search function
	public List<Item> search(double lat, double lon, String keyword) {
		if (keyword == null) {
			keyword = DEFAULT_KEYWORD;
		}
		try {
			keyword = URLEncoder.encode(keyword, "UTF-8");
		} catch (UnsupportedEncodingException e) {
			e.printStackTrace();
		}
		String url = String.format(URL_TEMPLATE, keyword, lat, lon);
		CloseableHttpClient httpClient = HttpClients.createDefault();
		try {
			CloseableHttpResponse response = httpClient.execute(new HttpGet(url));
			if (response.getStatusLine().getStatusCode() != 200) {
				return new ArrayList<>();
			}
			HttpEntity entity = response.getEntity();
			if (entity == null) {
				return new ArrayList<>();
			}
			BufferedReader reader = new BufferedReader(new InputStreamReader(entity.getContent()));
			StringBuilder responseBody = new StringBuilder();
			String line = null;
			while ((line = reader.readLine()) != null) {
				responseBody.append(line);
			}
			JSONArray array = new JSONArray(responseBody.toString());
			return getItemList(array);
		} catch (ClientProtocolException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
		return new ArrayList<>();
	}

	
	private List<Item> getItemList(JSONArray array) throws JSONException {
		List<Item> itemList = new ArrayList<>();
		List<String> descriptionList = new ArrayList<>();

		for (int i = 0; i < array.length(); i++) {
			JSONObject object = array.getJSONObject(i);
			ItemBuilder builder = new ItemBuilder();
			builder.setItemId(object.getString("id"));
			builder.setName(object.getString("title"));
			builder.setAddress(object.getString("location"));
			builder.setUrl(object.getString("url"));
			builder.setImageUrl(object.isNull("company_logo") ? "" : object.getString("company_logo"));

			// We need to extract categories from description since GitHub API
			// doesn't return keywords.
			//
			if (object.getString("description").equals("\n")) {
				descriptionList.add(object.getString("title"));
			} else {
				descriptionList.add(object.getString("description"));
			}
			
			Item item = builder.build();
			itemList.add(item);
		}

		// We need to get keywords from multiple text in one request since
		// MonkeyLearnAPI has a limitation on request per minute.
		String[] descriptionArray = descriptionList.toArray(new String[descriptionList.size()]); // Convert list to an array of the same type.
		List<List<String>> keywords = MonkeyLearnClient.extractKeywords(descriptionArray); // Call MonkeyLearn API.
		for (int i = 0; i < keywords.size(); ++i) {
			List<String> list = keywords.get(i);
			// Why do we use HashSet but not List here?  //to avoid getting same item
			Set<String> set = new HashSet<String>(list);
			itemList.get(i).setKeywords(set);
		}

		return itemList;
	}

	private String getStringFieldOrEmpty(JSONObject obj, String field) {
		return obj.isNull(field) ? "" : obj.getString(field);
	}
	
	public static void main(String[] args) {
		GitHubClient githubClient = new GitHubClient();
		List<Item> list = githubClient.search(37.38, -122.08, null);
		for (Item item : list) {
			JSONObject jsonObject = item.toJSONObject();
			System.out.println(jsonObject);
		}
	}

}

