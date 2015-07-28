/**
 * Copyright (C) 2011 Whisper Systems
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.grovecity.drizzlesms;

import android.os.AsyncTask;
import android.os.Bundle;
import android.support.v7.app.ActionBar;

import org.grovecity.drizzlesms.crypto.IdentityKeyUtil;
import org.grovecity.drizzlesms.crypto.MasterSecret;
import org.grovecity.drizzlesms.crypto.MasterSecretUtil;
import org.grovecity.drizzlesms.util.DrizzleSmsPreferences;
import org.grovecity.drizzlesms.R;
import org.grovecity.drizzlesms.util.VersionTracker;

/**
 * Activity for creating a user's local encryption passphrase.
 *
 * @author Moxie Marlinspike
 */

public class PassphraseCreateActivity extends PassphraseActivity {

    public PassphraseCreateActivity() { }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.create_passphrase_activity);

        initializeResources();
    }

    private void initializeResources() {
        getSupportActionBar().setDisplayOptions(ActionBar.DISPLAY_SHOW_CUSTOM);
        getSupportActionBar().setCustomView(R.layout.centered_app_title);
        // Made Changes Here To Add Both Lines Below...
        //DrizzleSmsPreferences.setPromptedPushRegistration(PassphraseCreateActivity.this, true);
        //DrizzleSmsPreferences.setPromptedDefaultSmsProvider(PassphraseCreateActivity.this, true);
        DrizzleSmsPreferences.setPushRegistered(PassphraseCreateActivity.this, true);

        new SecretGenerator().execute(MasterSecretUtil.UNENCRYPTED_PASSPHRASE);
    }

    private class SecretGenerator extends AsyncTask<String, Void, Void> {
        private MasterSecret masterSecret;

        @Override
        protected void onPreExecute() {
        }

        @Override
        protected Void doInBackground(String... params) {
            String passphrase = params[0];
            masterSecret      = MasterSecretUtil.generateMasterSecret(PassphraseCreateActivity.this,
                    passphrase);

            MasterSecretUtil.generateAsymmetricMasterSecret(PassphraseCreateActivity.this, masterSecret);
            IdentityKeyUtil.generateIdentityKeys(PassphraseCreateActivity.this, masterSecret);
            VersionTracker.updateLastSeenVersion(PassphraseCreateActivity.this);
            DrizzleSmsPreferences.setPasswordDisabled(PassphraseCreateActivity.this, true);

            return null;
        }

        @Override
        protected void onPostExecute(Void param) {
            setMasterSecret(masterSecret);
        }
    }

    @Override
    protected void cleanup() {
        System.gc();
    }
}
