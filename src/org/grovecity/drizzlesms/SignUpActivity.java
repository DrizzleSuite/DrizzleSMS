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

import com.parse.ParseException;
import com.parse.ParseUser;
import com.parse.SignUpCallback;

import org.grovecity.drizzlesms.R;

public class SignUpActivity extends Activity implements OnClickListener {

	Button createaccount;
	EditText email, password, name, username;
	String usernametxt, passwordtxt, nametxt, emailtxt;
    public SignUpActivity() {
    }

    @Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_signup);
		initializeView();
	}

	private void initializeView() {

		createaccount = (Button) findViewById(R.id.createaccount);
		//username = (EditText) findViewById(R.id.username);
		password = (EditText) findViewById(R.id.password);
		name = (EditText) findViewById(R.id.name);
		email = (EditText) findViewById(R.id.email);
		createaccount.setOnClickListener(this);
	}

	@Override
	public void onClick(View v) {
		switch (v.getId()) {

		case R.id.createaccount:

			//usernametxt = username.getText().toString();
			passwordtxt = password.getText().toString();
			nametxt = name.getText().toString();
			emailtxt = email.getText().toString();

            if (passwordtxt.length() == 0 && nametxt.length() == 0 && emailtxt.length() == 0) {
                Toast.makeText(SignUpActivity.this,"All fields must be filled out", Toast.LENGTH_SHORT).show();

            }
//            else if ((usernametxt.equalsIgnoreCase(""))) {
//                Toast.makeText(SignUpActivity.this, "Please enter username",Toast.LENGTH_SHORT).show();
//            }

            else if ((passwordtxt.equalsIgnoreCase(""))) {
                Toast.makeText(SignUpActivity.this, "Please enter password",Toast.LENGTH_SHORT).show();
            }

            else if ((nametxt.equalsIgnoreCase(""))) {
                Toast.makeText(SignUpActivity.this, "Please enter name",Toast.LENGTH_SHORT).show();
            }
            else if ((emailtxt.equalsIgnoreCase(""))) {
                Toast.makeText(SignUpActivity.this, "Please enter email",Toast.LENGTH_SHORT).show();
            }
            else {

				ParseUser user = new ParseUser();
				user.setUsername(emailtxt);
				user.setPassword(passwordtxt);
				user.put("name", nametxt);
				user.put("email", emailtxt);


				user.signUpInBackground(new SignUpCallback() {
					public void done(ParseException e) {
						if (e == null) {

							Toast.makeText(getApplicationContext(),
									"SignUp Successful", Toast.LENGTH_LONG)
									.show();
							startActivity(new Intent(SignUpActivity.this, LoginActivityNew.class));
							finish();

						} else {
							Log.e("e", "" + e);
							Toast.makeText(getApplicationContext(),
									"" + e.getMessage(), Toast.LENGTH_LONG).show();
						}
					}
				});
			}
			break;
		default:
			break;

		}
	}

    @Override
    public void onBackPressed() {
        super.onBackPressed();
        startActivity(new Intent(SignUpActivity.this, LoginActivityNew.class));
        finish();
    }
}
