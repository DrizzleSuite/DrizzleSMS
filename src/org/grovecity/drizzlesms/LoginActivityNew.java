package org.grovecity.drizzlesms;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import com.parse.LogInCallback;
import com.parse.ParseFacebookUtils;
import com.parse.ParseTwitterUtils;
import com.parse.ParseUser;

import org.grovecity.drizzlesms.R;
import org.grovecity.drizzlesms.util.DrizzleSmsPreferences;

import java.util.Arrays;
import java.util.List;

public class LoginActivityNew extends Activity implements OnClickListener {

    public LoginActivityNew() { }
	Button login, createaccount, facebook, twitter, parseLoginHelpButton;
	EditText email, password;
	String usernametxt, passwordtxt;


	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_login);
        ParseFacebookUtils.initialize(getApplicationContext());
        ParseTwitterUtils.initialize(getString(R.string.twitter_consumer_key), getString(R.string.twitter_consumer_secret));
		initializeView();
	}

	private void initializeView() {
		login = (Button) findViewById(R.id.login);
		createaccount = (Button) findViewById(R.id.createaccount);
        facebook = (Button) findViewById(R.id.facebook);
        twitter = (Button) findViewById(R.id.twitter);
		email = (EditText) findViewById(R.id.email);
        //parseLoginHelpButton = (Button) findViewById(R.id.parse_login_help);
		password = (EditText) findViewById(R.id.password);
		login.setOnClickListener(this);
		createaccount.setOnClickListener(this);
        facebook.setOnClickListener(this);
        twitter.setOnClickListener(this);

	}

	@Override
	public void onClick(View v) {
		switch (v.getId()) {

		case R.id.login:
			usernametxt = email.getText().toString();
			passwordtxt = password.getText().toString();


            if (usernametxt.length() == 0 && passwordtxt.length() == 0) {
                Toast.makeText(LoginActivityNew.this,"All fields must be filled out", Toast.LENGTH_SHORT).show();

            }
            else if ((usernametxt.equalsIgnoreCase(""))) {
                Toast.makeText(LoginActivityNew.this, "Please enter username",Toast.LENGTH_SHORT).show();
            }
            else if ((passwordtxt.equalsIgnoreCase(""))) {
                Toast.makeText(LoginActivityNew.this, "Please enter password",Toast.LENGTH_SHORT).show();
            }
            else {
				ParseUser.logInInBackground(usernametxt, passwordtxt,
						new LogInCallback() {
							public void done(ParseUser user,
									com.parse.ParseException e) {
								if (user != null) {
                                    DrizzleSmsPreferences.setPromptedPushRegistration(LoginActivityNew.this, true);
                                    Intent nextIntent = getIntent().getParcelableExtra("next_intent");

                                    if (nextIntent == null) {
                                        nextIntent = new Intent(LoginActivityNew.this, ConversationListActivity.class);
                                    }
                                    startActivity(nextIntent);
                                    finish();
								} else {
									Toast.makeText(getApplicationContext(),"No such user exist, Please sign up",Toast.LENGTH_SHORT).show();
								}
							}

						});
			}

			break;
		case R.id.createaccount:
			Intent in = new Intent(LoginActivityNew.this, SignUpActivity.class);
			startActivity(in);
			finish();
			break;

            case R.id.facebook:
                fbLogin();
                break;

            case R.id.twitter:
                TwitterLogin();
                break;
//            case R.id.parse_login_help:
//                Intent in1 = new Intent(LoginActivityNew.this, LoginActivityNew.class);
//                startActivity(in1);
//                finish();
//                break;

		default:
			break;

		}

	}


    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        ParseFacebookUtils.onActivityResult(requestCode, resultCode, data);
    }

    private final void fbLogin() {
        List permission = Arrays.asList("public_profile, email");
        ParseFacebookUtils.logInWithReadPermissionsInBackground(this, permission, new LogInCallback() {
            @Override
            public void done(ParseUser user, com.parse.ParseException e) {
                if (user == null) {
                    Log.d("No", "Uh oh. The user cancelled the Facebook login.");
                } else if (user.isNew()) {
                    Log.d("MyApp", "User signed up and logged in through Facebook!");
                    loginSuccess();

                } else {
                    Log.d("MyApp", "User logged in through Facebook!");
                    loginSuccess();
                }
            }
        });
    }

    private void loginSuccess(){
        DrizzleSmsPreferences.setPromptedPushRegistration(LoginActivityNew.this, true);
        Intent nextIntent = getIntent().getParcelableExtra("next_intent");

        if (nextIntent == null) {
            nextIntent = new Intent(LoginActivityNew.this, ConversationListActivity.class);
        }
        startActivity(nextIntent);
        finish();
    }
    private final void TwitterLogin(){
        ParseTwitterUtils.logIn(this, new LogInCallback() {
            @Override
            public void done(ParseUser parseUser, com.parse.ParseException e) {
                if (parseUser == null) {
                    Log.d("No", "Uh oh. The user cancelled the Twitter login.");

                } else if (parseUser.isNew()) {
                    Log.d("MyApp", "User signed up and logged in through Twitter!");
                    loginSuccess();
                } else {
                    loginSuccess();
                }
            }
        });
    }

}
