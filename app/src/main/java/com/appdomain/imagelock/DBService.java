package com.appdomain.imagelock;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.URL;

public class DBService {

    public static JSONObject validateAccount(JSONObject requestBody) throws Exception {
        // url will change
        URL url = new URL("http://10.0.2.2:3000/api/user/validate");
        JSONObject response = sendHttpRequest(url, "POST", requestBody);
        return response;
    }

    public static JSONObject registerAccount(JSONObject requestBody) throws Exception {
        // url will change
        URL url = new URL("http://10.0.2.2:3000/api/user/register");
        JSONObject response = sendHttpRequest(url, "POST", requestBody);
        return response;
    }

    public static boolean getAccountStatus(JSONObject requestBody) throws Exception {
        return false;
    }

    public static boolean setAccountStatus(JSONObject requestBody) throws Exception {
        return false;
    }

    public static JSONObject sendHttpRequest(URL url, String method, JSONObject body) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setDoInput(true);
        connection.setDoOutput(true);
        connection.setRequestProperty("Content-Type", "application/json");
        connection.setRequestProperty("Accept", "application/json");
        connection.setRequestMethod(method);

        OutputStreamWriter osr = new OutputStreamWriter(connection.getOutputStream());
        osr.write(body.toString());
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
