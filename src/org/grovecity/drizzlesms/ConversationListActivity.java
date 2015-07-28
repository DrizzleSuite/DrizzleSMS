/**
 * Copyright (C) 2014 Open Whisper Systems
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

import android.content.Intent;
import android.database.ContentObserver;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.provider.ContactsContract;
import android.support.annotation.NonNull;
import android.support.v7.app.ActionBar;
import android.util.Log;
import android.support.v4.view.MenuItemCompat;
import android.support.v7.widget.SearchView;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;

import com.facebook.appevents.AppEventsLogger;
import com.parse.GetCallback;
import com.parse.ParseException;
import com.parse.ParseQuery;
import com.parse.ParseUser;

import org.grovecity.drizzlesms.crypto.MasterSecret;
import org.grovecity.drizzlesms.database.DatabaseFactory;
import org.grovecity.drizzlesms.notifications.MessageNotifier;
import org.grovecity.drizzlesms.service.DirectoryRefreshListener;
import org.grovecity.drizzlesms.util.DynamicLanguage;
import org.grovecity.drizzlesms.R;
import org.grovecity.drizzlesms.components.RatingManager;
import org.grovecity.drizzlesms.recipients.RecipientFactory;
import org.grovecity.drizzlesms.recipients.Recipients;
import org.grovecity.drizzlesms.service.KeyCachingService;
import org.grovecity.drizzlesms.util.DynamicTheme;
import org.grovecity.drizzlesms.util.DrizzleSmsPreferences;

public class ConversationListActivity extends PassphraseRequiredActionBarActivity
        implements ConversationListFragment.ConversationSelectedListener
{
    private static final String TAG = ConversationListActivity.class.getSimpleName();

    private final DynamicTheme    dynamicTheme    = new DynamicTheme   ();
    private final DynamicLanguage dynamicLanguage = new DynamicLanguage();

    private ConversationListFragment fragment;
    private ContentObserver observer;
    private MasterSecret masterSecret;
    private TextView coinsView;
    public int mainVal= 0;

    @Override
    protected void onPreCreate() {
        dynamicTheme.onCreate(this);
        dynamicLanguage.onCreate(this);
    }

    @Override
    protected void onCreate(Bundle icicle, @NonNull MasterSecret masterSecret) {
        this.masterSecret = masterSecret;

        getSupportActionBar().setDisplayOptions(ActionBar.DISPLAY_SHOW_HOME | ActionBar.DISPLAY_SHOW_TITLE);
        getSupportActionBar().setTitle(R.string.app_name);

        ActionBar.LayoutParams lp = new ActionBar.LayoutParams(ActionBar.LayoutParams.WRAP_CONTENT, ActionBar.LayoutParams.WRAP_CONTENT, Gravity.RIGHT | Gravity.CENTER_VERTICAL);
        View customNav = LayoutInflater.from(this).inflate(R.layout.conversation_list_actionbar, null); // layout which contains your button.
        coinsView = (TextView) customNav.findViewById(R.id.coinsView);

        getSupportActionBar().setCustomView(customNav, lp);
        getSupportActionBar().setDisplayShowCustomEnabled(true);

        fragment = initFragment(android.R.id.content, new ConversationListFragment(), masterSecret, dynamicLanguage.getCurrentLocale());

        initializeContactUpdatesReceiver();

        DirectoryRefreshListener.schedule(this);
        RatingManager.showRatingDialogIfNecessary(this);


    }

    @Override
    public void onResume() {
        super.onResume();
        dynamicTheme.onResume(this);
        dynamicLanguage.onResume(this);
        AppEventsLogger.activateApp(this);
        if(ParseUser.getCurrentUser()!=null) {
            ParseUser user = ParseUser.getCurrentUser();
            ParseQuery<ParseUser> query = ParseUser.getQuery();
            query.getInBackground(user.getObjectId(), new GetCallback<ParseUser>() {
                public void done(ParseUser object, ParseException e) {
                    if (e == null) {
                        System.out.println("parse" + object.getInt("coins"));
                        coinsView.setText(getString(R.string.coins, object.getInt("coins")));

                    } else {
                        Log.d("getCoins Error: ", e.getMessage());
                    }
                }
            });
        }



    }

    @Override
    public void onDestroy() {
        if (observer != null) getContentResolver().unregisterContentObserver(observer);
        super.onDestroy();
    }

    @Override
    protected void onPause() {
        super.onPause();
        // Logs 'app deactivate' App Event.
        AppEventsLogger.deactivateApp(this);
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        MenuInflater inflater = this.getMenuInflater();
        menu.clear();

        inflater.inflate(R.menu.text_secure_normal, menu);


        menu.findItem(R.id.menu_new_group).setVisible(false);
        menu.findItem(R.id.menu_my_identity).setVisible(false);
        menu.findItem(R.id.menu_clear_passphrase).setVisible(!DrizzleSmsPreferences.isPasswordDisabled(this));

        inflater.inflate(R.menu.conversation_list, menu);
        MenuItem menuItem = menu.findItem(R.id.menu_search);
        // Changes Made Here to hide top add group button and shift at the bottom...
        menu.findItem(R.id.menu_add_group).setVisible(false);
        initializeSearch(menuItem);

        super.onPrepareOptionsMenu(menu);
        return true;
    }

    private void initializeSearch(MenuItem searchViewItem) {
        switch (searchViewItem.getItemId()) {
            case R.id.menu_search:            Search(searchViewItem);
        }

    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        super.onOptionsItemSelected(item);

        switch (item.getItemId()) {
            case R.id.menu_new_group:         createGroup();                  return true;
            case R.id.menu_settings:          handleDisplaySettings();        return true;
            case R.id.menu_clear_passphrase:  handleClearPassphrase();        return true;
            case R.id.menu_mark_all_read:     handleMarkAllRead();            return true;
            case R.id.menu_import_export:     handleImportExport();           return true;
            case R.id.menu_my_identity:       handleMyIdentity();             return true;
            case R.id.menu_add_group:         createGroup();                  return true;
            case R.id.menu_search:            Search(item);                   return true;
            case R.id.menu_rewards:           startRewardsApp();              return true;
            case R.id.menu_logoff:            logout();                       return true;
        }

        return false;
    }

    @Override
    public void onCreateConversation(long threadId, Recipients recipients, int distributionType) {
        createConversation(threadId, recipients, distributionType);
    }

    // Search Here uSing Action Bar Search Listener...
    private void Search(MenuItem searchViewItem) {
        SearchView searchView = (SearchView)MenuItemCompat.getActionView(searchViewItem);
        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) {
                if (fragment != null) {
                    fragment.setQueryFilter(query);
                    return true;
                }

                return false;
            }

            @Override
            public boolean onQueryTextChange(String newText) {
                return onQueryTextSubmit(newText);
            }
        });

        MenuItemCompat.setOnActionExpandListener(searchViewItem, new MenuItemCompat.OnActionExpandListener() {
            @Override
            public boolean onMenuItemActionExpand(MenuItem menuItem) {
                return true;
            }

            @Override
            public boolean onMenuItemActionCollapse(MenuItem menuItem) {
                if (fragment != null) {
                    fragment.resetQueryFilter();
                }

                return true;
            }
        });
    }

    private void startRewardsApp() {
        String packageName = "org.grovecity.drizzlerewards";
        Intent intent = getPackageManager().getLaunchIntentForPackage(packageName);

        if(intent == null){
            intent = new Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id="+packageName));
        }
        startActivity(intent);
    }

    private void logout() {
        if(ParseUser.getCurrentUser()!=null) {
            ParseUser currentUser = ParseUser.getCurrentUser();
            ParseUser.logOut();
        }
        DrizzleSmsPreferences.setPromptedPushRegistration(ConversationListActivity.this, false);
        Intent intent = new Intent(this, LoginActivityNew.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
    }

    private void createGroup() {
        Intent intent = new Intent(this, GroupCreateActivity.class);
        startActivity(intent);
    }

    private void createConversation(long threadId, Recipients recipients, int distributionType) {
        Intent intent = new Intent(this, ConversationActivity.class);
        intent.putExtra(ConversationActivity.RECIPIENTS_EXTRA, recipients.getIds());
        intent.putExtra(ConversationActivity.THREAD_ID_EXTRA, threadId);
        intent.putExtra(ConversationActivity.DISTRIBUTION_TYPE_EXTRA, distributionType);

        startActivity(intent);
    }

    private void handleDisplaySettings() {
        Intent preferencesIntent = new Intent(this, ApplicationPreferencesActivity.class);
        startActivity(preferencesIntent);
    }

    private void handleClearPassphrase() {
        Intent intent = new Intent(this, KeyCachingService.class);
        intent.setAction(KeyCachingService.CLEAR_KEY_ACTION);
        startService(intent);
    }

    private void handleImportExport() {
        startActivity(new Intent(this, ImportExportActivity.class));
    }

    private void handleMyIdentity() {
        startActivity(new Intent(this, ViewLocalIdentityActivity.class));
    }

    private void handleMarkAllRead() {
        new AsyncTask<Void, Void, Void>() {
            @Override
            protected Void doInBackground(Void... params) {
                DatabaseFactory.getThreadDatabase(ConversationListActivity.this).setAllThreadsRead();
                MessageNotifier.updateNotification(ConversationListActivity.this, masterSecret);
                return null;
            }
        }.execute();
    }

    private void initializeContactUpdatesReceiver() {
        observer = new ContentObserver(null) {
            @Override
            public void onChange(boolean selfChange) {
                super.onChange(selfChange);
                Log.w(TAG, "detected android contact data changed, refreshing cache");
                // TODO only clear updated recipients from cache
                RecipientFactory.clearCache();
                ConversationListActivity.this.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        fragment.getListAdapter().notifyDataSetChanged();
                    }
                });
            }
        };

        getContentResolver().registerContentObserver(ContactsContract.Contacts.CONTENT_URI,
                true, observer);
    }
}
