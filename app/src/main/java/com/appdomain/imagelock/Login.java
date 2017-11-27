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
import android.widget.ImageView;
import android.widget.ProgressBar;

import org.json.JSONObject;

import java.security.MessageDigest;

public class Login extends AppCompatActivity {

    private final static int LOGIN_MODE = 1, SIGNUP_MODE = 2;
    private ProcessCredentialsTask processCredTask = null;
    private EditText usernameEditText, passwordEditText, retypePasswordEditText;
    private ProgressBar loginProgressBar;
    private Button loginBtn, signUpBtn;
    private ImageView logo;
    private View loginForm;
    private int mode;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);
        usernameEditText = (EditText) findViewById(R.id.usernameEditText);
        passwordEditText = (EditText) findViewById(R.id.passwordEditText);
        retypePasswordEditText = (EditText) findViewById(R.id.retypePasswordEditText);
        loginForm = findViewById(R.id.loginForm);
        loginBtn = (Button) findViewById(R.id.loginBtn);
        signUpBtn = (Button) findViewById(R.id.signUpBtn);
        logo = (ImageView) findViewById(R.id.logo);
        loginProgressBar = (ProgressBar) findViewById(R.id.loginProgressBar);
        loginProgressBar.getIndeterminateDrawable()
                .setColorFilter(Color.parseColor("#C3C3C3"), PorterDuff.Mode.MULTIPLY);
        setClickEvents();
        setMode(LOGIN_MODE);
    }

    private void setMode(int mode) {
        this.mode = mode;
        clearText();
        updateViewByMode();
        usernameEditText.requestFocus();
    }

    private void clearText() {
        usernameEditText.setText("");
        passwordEditText.setText("");
        retypePasswordEditText.setText("");
    }

    private void updateViewByMode() {
        if (mode == LOGIN_MODE) {
            retypePasswordEditText.setVisibility(View.GONE);
            loginBtn.setBackgroundResource(R.drawable.white_fill_gray_outline);
            signUpBtn.setBackgroundResource(R.color.darkGrey);
        } else {
            retypePasswordEditText.setVisibility(View.VISIBLE);
            loginBtn.setBackgroundResource(R.color.darkGrey);
            signUpBtn.setBackgroundResource(R.drawable.white_fill_gray_outline);
        }
    }

    private void setClickEvents() {
        loginBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                usernameEditText.setError(null);
                passwordEditText.setError(null);
                if (mode == LOGIN_MODE) {
                    processCredentials();
                } else {
                    setMode(LOGIN_MODE);
                }
            }
        });

        signUpBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                usernameEditText.setError(null);
                passwordEditText.setError(null);
                retypePasswordEditText.setError(null);
                if (mode == SIGNUP_MODE) {
                    processCredentials();
                } else {
                    setMode(SIGNUP_MODE);
                }
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

    private void processCredentials() {
        if (!isConnectedToInternet()) {
            final Snackbar noInternet = Snackbar.make(findViewById(android.R.id.content),
                    "No Internet Access", Snackbar.LENGTH_LONG);
            noInternet.show();
            return;
        }

        if (processCredTask != null) {
            return;
        }

        usernameEditText.setError(null);
        passwordEditText.setError(null);

        String username = usernameEditText.getText().toString();
        String password = passwordEditText.getText().toString();

        boolean cancel = false;
        View focusView = null;

        if (mode == SIGNUP_MODE) {
            String retypedPassword = retypePasswordEditText.getText().toString();
            if (TextUtils.isEmpty(retypedPassword)) {
                retypePasswordEditText.setError(getString(R.string.please_retype_password));
                focusView = retypePasswordEditText;
                cancel = true;
            }

            if (!TextUtils.isEmpty(retypedPassword) && !retypedPassword.equals(password)) {
                passwordEditText.setError(getString(R.string.passwords_dont_match));
                retypePasswordEditText.setError(getString(R.string.passwords_dont_match));
                focusView = passwordEditText;
                cancel = true;
            }
        }

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
            processCredTask = new ProcessCredentialsTask(username, password);
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
            logo.setVisibility(View.GONE);
        }
        else {
            loginProgressBar.setVisibility(View.GONE);
            loginForm.setVisibility(View.VISIBLE);
            logo.setVisibility(View.VISIBLE);
        }
    }

    private class ProcessCredentialsTask extends AsyncTask<Void, Void, JSONObject> {

        private final String username;
        private final String password;

        ProcessCredentialsTask(String username, String password) {
            this.username = username;
            this.password = password;
        }

        @Override
        protected JSONObject doInBackground(Void... params) {
            if (mode == LOGIN_MODE) {
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
            message = salt + message;
            try {
                MessageDigest md = MessageDigest.getInstance("SHA-256");
                md.update(message.getBytes());
                byte[] bytes = md.digest();
                hash = bytesToHex(bytes);
            }
            catch(Exception e) {
                Log.e("Hash", e.toString());
            }
            return hash;
        }

        private String bytesToHex(byte[] hash) {
            StringBuffer hexString = new StringBuffer();
            for (int i = 0; i < hash.length; i++) {
                if (i % 2 == 0) continue;
                String hex = Integer.toHexString(0xff & hash[i]);
                if(hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        }

    }

}
