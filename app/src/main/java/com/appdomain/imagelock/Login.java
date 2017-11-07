package com.appdomain.imagelock;

import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.design.widget.Snackbar;
import android.support.v7.app.AppCompatActivity;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;

import org.json.JSONObject;

import java.security.MessageDigest;

public class Login extends AppCompatActivity {

    private ProcessCredentialsTask processCredTask = null;
    private EditText usernameEditText, passwordEditText;
    private ProgressBar loginProgressBar;
    private View loginForm;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        usernameEditText = (EditText) findViewById(R.id.usernameEditText);
        passwordEditText = (EditText) findViewById(R.id.passwordEditText);
        loginForm = findViewById(R.id.loginForm);
        loginProgressBar = (ProgressBar) findViewById(R.id.loginProgressBar);
        loginProgressBar.getIndeterminateDrawable()
                .setColorFilter(Color.parseColor("#FFFFFF"), PorterDuff.Mode.MULTIPLY);
        setClickEvents();
    }

    private void setClickEvents() {
        Button loginBtn = (Button) findViewById(R.id.loginBtn);
        loginBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (!isConnectedToInternet()) {
                    Snackbar deleted = Snackbar.make(findViewById(android.R.id.content),
                            "No Internet Access", Snackbar.LENGTH_LONG);
                    deleted.show();
                    return;
                }
                processCredentials(ProcessCredentialsTask.LOGIN);
            }
        });

        Button signUpBtn = (Button) findViewById(R.id.signUpBtn);
        signUpBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (!isConnectedToInternet()) {
                    final Snackbar deleted = Snackbar.make(findViewById(android.R.id.content),
                            "No Internet Access", Snackbar.LENGTH_LONG);
                    deleted.show();
                    return;
                }
                processCredentials(ProcessCredentialsTask.SIGN_UP);
            }
        });
    }

    private boolean isConnectedToInternet() {
        ConnectivityManager cm = (ConnectivityManager) getApplicationContext()
                .getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo activeNetwork = cm.getActiveNetworkInfo();
        return activeNetwork != null &&
                activeNetwork.isConnectedOrConnecting();
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
            processCredTask.execute();
        }
    }

    private boolean isUsernameValid(String username) {
        // Checks to make sure username is alphanumeric
        return username.matches("[a-zA-Z0-9]*");
    }

    private boolean isPasswordValid(String password) {
        return password.length() > 4;
    }

    private synchronized void showProgress(boolean show) {
        if (show) {
            loginProgressBar.setVisibility(View.VISIBLE);
            loginForm.setVisibility(View.GONE);
        }
        else {
            loginProgressBar.setVisibility(View.GONE);
            loginForm.setVisibility(View.VISIBLE);
        }
    }

    private class ProcessCredentialsTask extends AsyncTask<Void, Void, JSONObject> {

        public final static int LOGIN = 1, SIGN_UP = 2;
        private final String username;
        private final String password;
        private final int processCode;

        ProcessCredentialsTask(String username, String password, int processCode) {
            this.username = username;
            this.password = password;
            this.processCode = processCode;
        }

        @Override
        protected JSONObject doInBackground(Void... params) {
            if (processCode == LOGIN) {
                return validateAccount();
            }
            else {
                return registerAccount();
            }
        }

        @Override
        protected void onPostExecute(final JSONObject response) {
            processCredTask = null;
            try {
                Boolean success = response.getBoolean("success");
                if (success) {
                    String salt = response.getString("salt");
                    String key = hash(password, salt);
                    String token = response.getString("token");

                    Intent home = new Intent(getApplicationContext(), Gallery.class);
                    home.putExtra("USERNAME", username);
                    home.putExtra("KEY", key);
                    home.putExtra("TOKEN", token);
                    startActivity(home);
                    finish();
                }
                else {
                    Integer errorCode = response.getInt("errorCode");
                    switch(errorCode) {
                        case 1:
                            usernameEditText.setError(getString(R.string.error_username_taken));
                            usernameEditText.requestFocus();
                            break;
                        case 2:
                            usernameEditText.setError(getString(R.string.error_account_not_recognized));
                            usernameEditText.requestFocus();
                            break;
                        case 3:
                            passwordEditText.setError(getString(R.string.error_incorrect_password));
                            passwordEditText.requestFocus();
                            break;
                        case 4:
                            Snackbar serverError = Snackbar.make(findViewById(android.R.id.content),
                                    "Internal Server Error", Snackbar.LENGTH_LONG);
                            serverError.show();
                            break;
                        default:
                            Snackbar codeError = Snackbar.make(findViewById(android.R.id.content),
                                    "Code Error", Snackbar.LENGTH_LONG);
                            codeError.show();
                            break;
                    }
                    showProgress(false);
                }
            }
            catch(Exception e) {
                showProgress(false);
            }
        }

        @Override
        protected void onCancelled() {
            processCredTask = null;
            showProgress(false);
        }

        private JSONObject registerAccount() {
            try {
                JSONObject account = new JSONObject();
                account.put("username", username);
                account.put("password", password);
                return HttpService.registerAccount(account);
            }
            catch (Exception e) {
                return null;
            }
        }

        private JSONObject validateAccount() {
            try {
                JSONObject account = new JSONObject();
                account.put("username", username);
                account.put("password", password);
                return HttpService.authenticateAccount(account);
            }
            catch (Exception e) {
                return null;
            }
        }

        public String hash(String message, String salt) {
            String hash = null;
            message = message + salt;
            try {
                MessageDigest md = MessageDigest.getInstance("SHA-256");
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
