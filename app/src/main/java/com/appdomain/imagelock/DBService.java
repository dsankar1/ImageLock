package com.appdomain.imagelock;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.URL;

public class DBService {

    public static JSONObject authenticateAccount(JSONObject requestBody) throws Exception {
        URL url = new URL("http://10.0.2.2:3002/api/authenticate");
        JSONObject response = sendHttpRequest(url, "POST", requestBody, "");
        return response;
    }

    public static JSONObject registerAccount(JSONObject requestBody) throws Exception {
        URL url = new URL("http://10.0.2.2:3002/api/register");
        JSONObject response = sendHttpRequest(url, "POST", requestBody, "");
        return response;
    }

    public static JSONObject getImages(String token) throws Exception {
        URL url = new URL("http://10.0.2.2:3002/api/images");
        JSONObject response = sendHttpRequest(url, "GET", null, token);
        return response;
    }

    public static JSONObject getRequest(URL url, String token) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("GET");
        connection.setRequestProperty("Content-Type", "application/json");
        connection.setRequestProperty("x-access-token", token);
        BufferedReader input = new BufferedReader(new InputStreamReader(connection.getInputStream()));
        String line;
        StringBuffer response = new StringBuffer();
        while ((line = input.readLine()) != null) {
            response.append(line);
        }
        input.close();
        JSONObject jsonResponse = new JSONObject(response.toString());
        return jsonResponse;
    }

    public static JSONObject sendHttpRequest(URL url, String method, JSONObject body, String token) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setDoInput(true);
        connection.setDoOutput(true);
        connection.setRequestProperty("Content-Type", "application/json");
        connection.setRequestProperty("x-access-token", token);
        connection.setRequestProperty("Accept", "application/json");
        connection.setRequestMethod(method);

        OutputStreamWriter osr = new OutputStreamWriter(connection.getOutputStream());
        if (body != null) osr.write(body.toString());
        osr.flush();

        BufferedReader br = new BufferedReader
                (new InputStreamReader(connection.getInputStream()));

        String line;
        StringBuilder response = new StringBuilder();
        while ((line = br.readLine()) != null) {
            response.append(line);
        }
        br.close();
        JSONObject json = new JSONObject(response.toString());
        return json;
    }

}
