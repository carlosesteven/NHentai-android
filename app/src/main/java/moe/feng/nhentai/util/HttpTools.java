package moe.feng.nhentai.util;

import android.support.annotation.Nullable;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;

public class HttpTools {

	public static HttpURLConnection openConnection(String url) throws IOException {
		URL u = new URL(url);
		HttpURLConnection conn = (HttpURLConnection) u.openConnection();
		conn.setConnectTimeout(5000);
		conn.setRequestMethod("GET");
		conn.setRequestProperty("Accept-Encoding", "identity");
		conn.setRequestProperty("Referer", URLEncoder.encode(url, "UTF-8"));
		conn.setRequestProperty("Charset", "UTF-8");
		conn.setRequestProperty("Connection", "Keep-Alive");
		return conn;
	}

	public static long getTargetContentSize(String url) throws IOException {
		HttpURLConnection conn = openConnection(url);
		if (conn.getResponseCode() == HttpURLConnection.HTTP_OK) {
			return conn.getContentLength();
		}
		return -1;
	}

	@Nullable
	public static String getStringFromUrl(String url) throws IOException{
		HttpURLConnection connection=openConnection(url);
        connection.connect();
		if (connection.getResponseCode()==HttpURLConnection.HTTP_OK) {
			BufferedReader br = new BufferedReader(new InputStreamReader(connection.getInputStream()));
			String line;
			StringBuilder builder = new StringBuilder();
			while ((line = br.readLine()) != null) {
				builder.append(line);
			}
			br.close();
			connection.disconnect();
			return builder.toString();
		}
		connection.disconnect();
		return null;
	}

}
