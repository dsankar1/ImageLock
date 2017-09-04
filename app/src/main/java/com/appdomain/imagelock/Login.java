package com.appdomain.imagelock;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.annotation.TargetApi;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;

public class Login extends AppCompatActivity {

    private ProcessCredentialsTask processCredTask = null;
    private EditText usernameEditText, passwordEditText;
    private View loginProgressBar;
    private View loginForm;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        usernameEditText = (EditText) findViewById(R.id.usernameEditText);
        passwordEditText = (EditText) findViewById(R.id.passwordEditText);
        loginForm = findViewById(R.id.loginForm);
        loginProgressBar = findViewById(R.id.loginProgressBar);

        Button loginBtn = (Button) findViewById(R.id.loginBtn);
        loginBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                processCredentials(ProcessCredentialsTask.LOGIN);
            }
        });

        Button signUpBtn = (Button) findViewById(R.id.signUpBtn);
        signUpBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                processCredentials(ProcessCredentialsTask.SIGNUP);
            }
        });
    }

    private void processCredentials(int process) {
        if (processCredTask != null) {
            return;
        }

        usernameEditText.setError(null);
        passwordEditText.setError(null);

        String username = usernameEditText.getText().toString();
        String password = passwordEditText.getText().toString();
        boolean cancel = false;
        View focusView = null;

        if (TextUtils.isEmpty(password)) {
            passwordEditText.setError(getString(R.string.error_field_required));
            focusView = passwordEditText;
            cancel = true;
        }

        if (!TextUtils.isEmpty(password) && !isPasswordValid(password)) {
            passwordEditText.setError(getString(R.string.error_invalid_password));
            focusView = passwordEditText;
            cancel = true;
        }

        if (TextUtils.isEmpty(username)) {
            usernameEditText.setError(getString(R.string.error_field_required));
            focusView = usernameEditText;
            cancel = true;
        }

        if (!TextUtils.isEmpty(username) && !isUsernameValid(username)) {
            usernameEditText.setError(getString(R.string.error_invalid_username));
            focusView = usernameEditText;
            cancel = true;
        }

        if (cancel) {
            focusView.requestFocus();
        } else {
            showProgress(true);
            processCredTask = new ProcessCredentialsTask(username, password, process);
            processCredTask.execute((Void) null);
        }
    }

    private boolean isUsernameValid(String username) {
        return username.matches("[a-zA-Z0-9]*");
    }

    private boolean isPasswordValid(String password) {
        return password.length() > 4;
    }

    @TargetApi(Build.VERSION_CODES.HONEYCOMB_MR2)
    private void showProgress(final boolean show) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB_MR2) {
            int shortAnimTime = getResources().getInteger(android.R.integer.config_shortAnimTime);

            loginForm.setVisibility(show ? View.GONE : View.VISIBLE);
            loginForm.animate().setDuration(shortAnimTime).alpha(
                    show ? 0 : 1).setListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    loginForm.setVisibility(show ? View.GONE : View.VISIBLE);
                }
            });

            loginProgressBar.setVisibility(show ? View.VISIBLE : View.GONE);
            loginProgressBar.animate().setDuration(shortAnimTime).alpha(
                    show ? 1 : 0).setListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    loginProgressBar.setVisibility(show ? View.VISIBLE : View.GONE);
                }
            });
        } else {
            loginProgressBar.setVisibility(show ? View.VISIBLE : View.GONE);
            loginForm.setVisibility(show ? View.GONE : View.VISIBLE);
        }
    }

    private class ProcessCredentialsTask extends AsyncTask<Void, Void, Integer> {

        private final static int NO_ERROR = 0, ACCOUNT_EXISTS = 1, UNVERIFIED = 2;
        public final static int LOGIN = 1, SIGNUP = 2;
        private final String username;
        private final String password;
        private final int processCode;

        ProcessCredentialsTask(String username, String password, int processCode) {
            this.username = username;
            this.password = password;
            this.processCode = processCode;
        }

        @Override
        protected Integer doInBackground(Void... params) {
            if (processCode == LOGIN) {
                if (validateUser()) {
                    return NO_ERROR;
                }
                else {
                    return UNVERIFIED;
                }
            }
            else {
                if (registerUser()) {
                    return NO_ERROR;
                }
                else {
                    return ACCOUNT_EXISTS;
                }
            }
        }

        @Override
        protected void onPostExecute(final Integer errorCode) {
            processCredTask = null;

            if (errorCode == UNVERIFIED) {
                usernameEditText.setError(getString(R.string.error_account_not_recognized));
                usernameEditText.requestFocus();
            } else if (errorCode == ACCOUNT_EXISTS) {
                usernameEditText.setError(getString(R.string.error_username_taken));
                usernameEditText.requestFocus();
            } else {
                Intent home = new Intent(getApplicationContext(), Gallery.class);
                home.putExtra("USERNAME", username);
                home.putExtra("PASSWORD", password);
                home.putExtra("PREFIX", username + "/");
                startActivity(home);
            }
            showProgress(false);
        }

        @Override
        protected void onCancelled() {
            processCredTask = null;
            showProgress(false);
        }

        private boolean registerUser() {
            try {
                URL url = new URL("http://10.0.2.2:3000/api/user/register");
                JSONObject user = new JSONObject();
                user.put("username", username);
                user.put("password", password);
                user.put("prefix", username + "/");

                JSONObject response = sendHttpRequest(url, "POST", user);
                boolean result = response.getBoolean("registered");
                return result;
            }
            catch (Exception e) {
                Log.i("Error", e.toString());
                return false;
            }
        }

        private boolean validateUser() {
            try {
                URL url = new URL("http://10.0.2.2:3000/api/user/validate");
                JSONObject user = new JSONObject();
                user.put("username", username);
                user.put("password", password);

                JSONObject response = sendHttpRequest(url, "POST", user);
                boolean result = response.getBoolean("valid");
                return result;
            }
            catch (Exception e) {
                Log.i("Error", e.toString());
                return false;
            }
        }

        private JSONObject sendHttpRequest(URL url, String method, JSONObject body) throws Exception {
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

        private String hash(String message) {
            String hash = null;
            try {
                MessageDigest md = MessageDigest.getInstance("MD5");
                md.update(message.getBytes());
                byte[] bytes = md.digest();
                StringBuilder sb = new StringBuilder();

                for (byte b : bytes) {
                    sb.append(Integer.toString((b & 0xff) + 0x100, 16).substring(1));
                }
                hash = sb.toString();
            }
            catch(Exception e) {
                Log.e("Hash", e.toString());
            }
            return hash;
        }

    }
}
