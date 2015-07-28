package org.grovecity.drizzlesms;

import com.parse.Parse;
import com.parse.ParseACL;
import com.parse.ParseUser;
import android.app.Application;
import android.widget.Toast;

public class ParseApplication extends Application {

	@Override
	public void onCreate() {
		super.onCreate();
		Parse.initialize(this,"6qe05Nb4AGXyOiSbFVPxND8ylDZ5wlI5TwQvwy3x","94QQZpvkGxcbK5amPwXRa5d517ZVCy8xA8VFhawi");
		ParseUser.enableAutomaticUser();
		ParseACL defaultACL = new ParseACL();
		defaultACL.setPublicReadAccess(true);
		ParseACL.setDefaultACL(defaultACL, true);
	}

}