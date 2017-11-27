package com.appdomain.imagelock;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.Key;
import java.util.ArrayList;
import java.util.Arrays;

import javax.crypto.Cipher;
import javax.crypto.CipherInputStream;
import javax.crypto.spec.SecretKeySpec;

import okhttp3.ConnectionSpec;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class HttpService {

    public static JSONObject authenticateAccount(JSONObject body) throws Exception {
        return sendHttpRequest("POST", "http://10.0.2.2:3002/api/authenticate", body, null);
    }

    public static JSONObject registerAccount(JSONObject body) throws Exception {
        return sendHttpRequest("POST", "http://10.0.2.2:3002/api/register", body, null);
    }

    public static boolean deleteImage(String filename, String token) throws Exception {
        JSONObject response = sendHttpRequest("DELETE", "http://10.0.2.2:3002/api/images/" + filename, null, token);
        return response.getBoolean("success");
    }

    public static boolean uploadImage(File file, String token) throws Exception {
        String url = getUploadUrl(file.getName(), token);
        OkHttpClient client = new OkHttpClient.Builder()
                .connectionSpecs(Arrays.asList(ConnectionSpec.MODERN_TLS, ConnectionSpec.CLEARTEXT))
                .build();
        Request uploadFileRequest = new Request.Builder()
                .url(url)
                .put(RequestBody.create(MediaType.parse(""), file))
                .build();
        Response uploadResponse = client.newCall(uploadFileRequest).execute();
        return uploadResponse.isSuccessful();
    }

    private static String getUploadUrl(String filename, String token) throws Exception {
        JSONObject response = sendHttpRequest("GET", "http://10.0.2.2:3002/api/images/" + filename, null, token);
        return response.getString("url");
    }

    public static void downloadImages(File directory, String token, String key) throws Exception {
        ArrayList<URL> urls = getDownloadUrls(token);
        for (int i = 0; i < urls.size(); i++) {
            String filename = urls.get(i).getPath();
            filename = filename.substring(filename.lastIndexOf("/"));
            filename = filename.substring(0, filename.lastIndexOf(".")) + ".jpg";
            File image = new File(directory, filename);
            streamUrlContentToFile(urls.get(i), image, key);
        }
    }

    private static void streamUrlContentToFile(URL url, File destination, String key) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        // AES/ECB/PKCS5Padding
        Key secretKey = new SecretKeySpec(key.getBytes(), "AES");
        Cipher cipher = Cipher.getInstance("AES");
        cipher.init(Cipher.DECRYPT_MODE, secretKey);
        CipherInputStream input = new CipherInputStream(connection.getInputStream(), cipher);
        byte[] buffer = new byte[4096];
        int count;
        OutputStream output = new FileOutputStream(destination);
        while ( (count = input.read(buffer)) != -1) {
            output.write(buffer, 0, count);
        }
        output.close();
    }

    private static ArrayList<URL> getDownloadUrls(String token) throws Exception {
        JSONObject response = sendHttpRequest("GET", "http://10.0.2.2:3002/api/images", null, token);
        JSONArray jsonUrls = response.getJSONArray("urls");
        ArrayList<URL> urls = new ArrayList<>();
        for (int i = 0; i < jsonUrls.length(); i++) {
            URL imageUrl = new URL((String) jsonUrls.get(i));
            urls.add(imageUrl);
        }
        return urls;
    }

    private static JSONObject sendHttpRequest(String method, String urlString, Object body, String token) throws Exception {
        URL url = new URL(urlString);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod(method);
        connection.setRequestProperty("Content-Type", "application/json");
        if (token != null) connection.setRequestProperty("x-access-token", token);
        if (body != null) {
            OutputStreamWriter out = new OutputStreamWriter(connection.getOutputStream());
            out.write(body.toString());
            out.close();
        }
        BufferedReader input = new BufferedReader(new InputStreamReader(connection.getInputStream()));
        String line;
        StringBuilder response = new StringBuilder();
        while ((line = input.readLine()) != null) {
            response.append(line);
        }
        input.close();
        return new JSONObject(response.toString());
    }

}
