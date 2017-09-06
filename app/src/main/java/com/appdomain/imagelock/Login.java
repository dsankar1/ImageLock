package com.appdomain.imagelock;

import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;

import org.json.JSONObject;

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
                if (validateAccount()) {
                    return NO_ERROR;
                }
                else {
                    return UNVERIFIED;
                }
            }
            else {
                if (registerAccount()) {
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

        private boolean registerAccount() {
            try {
                JSONObject account = new JSONObject();
                account.put("username", username);
                account.put("password", hash(password));
                account.put("prefix", username + "/");
                JSONObject response = DBService.registerAccount(account);
                boolean result = response.getBoolean("registered");
                return result;
            }
            catch (Exception e) {
                Log.i("Error", e.toString());
                return false;
            }
        }

        private boolean validateAccount() {
            try {
                JSONObject account = new JSONObject();
                account.put("username", username);
                account.put("password", hash(password));
                JSONObject response = DBService.validateAccount(account);
                boolean result = response.getBoolean("valid");
                return result;
            }
            catch (Exception e) {
                Log.i("Error", e.toString());
                return false;
            }
        }

        public String hash(String message) {
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
