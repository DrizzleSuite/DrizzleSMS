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

import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.provider.ContactsContract;
import android.support.annotation.NonNull;
import android.telephony.PhoneNumberUtils;
import android.text.Editable;
import android.text.InputType;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.Log;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnFocusChangeListener;
import android.view.View.OnKeyListener;
import android.view.ViewStub;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import com.afollestad.materialdialogs.AlertDialogWrapper;
import com.google.android.gms.ads.AdListener;
import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.AdView;
import com.google.protobuf.ByteString;

import org.grovecity.drizzlesms.components.AnimatingToggle;
import org.grovecity.drizzlesms.components.ComposeText;
import org.grovecity.drizzlesms.components.SendButton;
import org.grovecity.drizzlesms.components.emoji.EmojiDrawer;
import org.grovecity.drizzlesms.components.emoji.EmojiToggle;
import org.grovecity.drizzlesms.contacts.ContactAccessor;
import org.grovecity.drizzlesms.crypto.MasterCipher;
import org.grovecity.drizzlesms.crypto.MasterSecret;
import org.grovecity.drizzlesms.crypto.SecurityEvent;
import org.grovecity.drizzlesms.database.DatabaseFactory;
import org.grovecity.drizzlesms.database.DraftDatabase;
import org.grovecity.drizzlesms.database.MmsSmsColumns;
import org.grovecity.drizzlesms.database.ThreadDatabase;
import org.grovecity.drizzlesms.mms.AttachmentManager;
import org.grovecity.drizzlesms.mms.AttachmentTypeSelectorAdapter;
import org.grovecity.drizzlesms.mms.OutgoingGroupMediaMessage;
import org.grovecity.drizzlesms.mms.OutgoingMediaMessage;
import org.grovecity.drizzlesms.mms.Slide;
import org.grovecity.drizzlesms.mms.SlideDeck;
import org.grovecity.drizzlesms.notifications.MessageNotifier;
import org.grovecity.drizzlesms.recipients.Recipient;
import org.grovecity.drizzlesms.recipients.Recipients;
import org.grovecity.drizzlesms.sms.OutgoingTextMessage;
import org.grovecity.drizzlesms.util.BitmapDecodingException;
import org.grovecity.drizzlesms.util.CharacterCalculator;
import org.grovecity.drizzlesms.util.Dialogs;
import org.grovecity.drizzlesms.util.DirectoryHelper;
import org.grovecity.drizzlesms.util.DynamicLanguage;
import org.grovecity.drizzlesms.util.DynamicTheme;
import org.grovecity.drizzlesms.util.GroupUtil;
import org.grovecity.drizzlesms.util.DrizzleSmsPreferences;
import org.grovecity.drizzlesms.R;
import org.grovecity.drizzlesms.TransportOptions.OnTransportChangedListener;
import org.grovecity.drizzlesms.database.GroupDatabase;
import org.grovecity.drizzlesms.mms.MediaTooLargeException;
import org.grovecity.drizzlesms.mms.MmsMediaConstraints;
import org.grovecity.drizzlesms.mms.OutgoingSecureMediaMessage;
import org.grovecity.drizzlesms.recipients.RecipientFactory;
import org.grovecity.drizzlesms.recipients.RecipientFormattingException;
import org.grovecity.drizzlesms.service.KeyCachingService;
import org.grovecity.drizzlesms.sms.MessageSender;
import org.grovecity.drizzlesms.sms.OutgoingEncryptedMessage;
import org.grovecity.drizzlesms.sms.OutgoingEndSessionMessage;
import org.grovecity.drizzlesms.util.Util;
import org.whispersystems.libaxolotl.InvalidMessageException;
import org.whispersystems.libaxolotl.util.guava.Optional;

import java.io.IOException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.List;

import static org.grovecity.drizzlesms.database.GroupDatabase.GroupRecord;
import static org.whispersystems.textsecure.internal.push.PushMessageProtos.PushMessageContent.GroupContext;

import com.parse.GetCallback;
import com.parse.Parse;
import com.parse.ParseException;
import com.parse.ParseObject;
import com.parse.ParseQuery;
import com.parse.ParseUser;

/**
 * Activity for displaying a message thread, as well as
 * composing/sending a new message into that thread.
 *
 * @author Moxie Marlinspike
 */
public class ConversationActivity extends PassphraseRequiredActionBarActivity
        implements ConversationFragment.ConversationFragmentListener,
        AttachmentManager.AttachmentListener,
        Recipient.RecipientModifiedListener {
    private static final String TAG = ConversationActivity.class.getSimpleName();

    public static final String RECIPIENTS_EXTRA = "recipients";
    public static final String THREAD_ID_EXTRA = "thread_id";
    public static final String DRAFT_TEXT_EXTRA = "draft_text";
    public static final String DRAFT_IMAGE_EXTRA = "draft_image";
    public static final String DRAFT_AUDIO_EXTRA = "draft_audio";
    public static final String DRAFT_VIDEO_EXTRA = "draft_video";
    public static final String DISTRIBUTION_TYPE_EXTRA = "distribution_type";

    private static final int PICK_IMAGE = 1;
    private static final int PICK_VIDEO = 2;
    private static final int PICK_AUDIO = 3;
    private static final int PICK_CONTACT_INFO = 4;
    private static final int GROUP_EDIT = 5;
    private static final int CAPTURE_PHOTO = 6;

    private MasterSecret masterSecret;
    private ComposeText composeText;
    private AnimatingToggle buttonToggle;
    private SendButton sendButton;
    private ImageButton attachButton;
    private TextView charactersLeft;
    private ConversationFragment fragment;

    private AttachmentTypeSelectorAdapter attachmentAdapter;
    private AttachmentManager attachmentManager;
    private BroadcastReceiver securityUpdateReceiver;
    private BroadcastReceiver groupUpdateReceiver;
    private Optional<EmojiDrawer> emojiDrawer = Optional.absent();
    private EmojiToggle emojiToggle;

    private Recipients recipients;
    private long threadId;
    private int distributionType;
    private boolean isEncryptedConversation;
    private boolean isMmsEnabled = true;

    private DynamicTheme dynamicTheme = new DynamicTheme();
    private DynamicLanguage dynamicLanguage = new DynamicLanguage();
    private AdView mAdView;
    //public AdLayout adView; // The ad view used to load and display the ad.
    private static final String APP_KEY = "removed";
    private static final String LOG_TAG = "SimpleAdSample"; // Tag used to prefix all log messages.
    //private Timer timer = new Timer();
    int delay = 0;
    int period = 45000; // repeat every 45 secs.
    //private Handler handler = new Handler();

    @Override
    protected void onPreCreate() {
        dynamicTheme.onCreate(this);
        dynamicLanguage.onCreate(this);
        overridePendingTransition(R.anim.slide_from_right, R.anim.fade_scale_out);
    }

//    private Runnable runnable = new Runnable() {
//
//        public void run() {
//            loadAd();
//            handler.postDelayed(this, period);
//        }
//    };


    @Override
    protected void onCreate(Bundle state, @NonNull MasterSecret masterSecret) {
        this.masterSecret = masterSecret;

        setContentView(R.layout.conversation_activity);

//        // For debugging purposes enable logging, but disable for production builds.
//        AdRegistration.enableLogging(true);
//        // For debugging purposes flag all ad requests as tests, but set to false for production builds.
//        AdRegistration.enableTesting(true);
//
//        this.adView = (AdLayout) findViewById(R.id.ad_view);
//        this.adView.setListener(new SampleAdListener());
//
//        try {
//            AdRegistration.setAppKey(APP_KEY);
//        } catch (final IllegalArgumentException e) {
//            Log.e(LOG_TAG, "IllegalArgumentException thrown: " + e.toString());
//            return;
//        }
        //this.adView.loadAd();

        mAdView = (AdView) findViewById(R.id.ad_view);
        AdRequest adRequest = new AdRequest.Builder()
                .build();
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        mAdView.loadAd(adRequest);
        fragment = initFragment(R.id.fragment_content, new ConversationFragment(), masterSecret, dynamicLanguage.getCurrentLocale());

        initializeReceivers();
        initializeViews();
        initializeResources();
        initializeDraft();
        mAdView.setAdListener(new AdListener() {
            @Override
            public void onAdLoaded() {
                super.onAdLoaded();
                ParseQuery<ParseUser> query = ParseUser.getQuery();
                if (ParseUser.getCurrentUser() != null) {
                    query.getInBackground(ParseUser.getCurrentUser().getObjectId(), new GetCallback<ParseUser>() {
                        @Override
                        public void done(ParseUser parseUser, ParseException e) {
                            parseUser.increment("bannerCount");
                            parseUser.saveInBackground();
                        }
                    });
                }
            }
        });
    }



    @Override
    protected void onNewIntent(Intent intent) {
        Log.w(TAG, "onNewIntent()");
        if (!Util.isEmpty(composeText) || attachmentManager.isAttachmentPresent()) {
            saveDraft();
            attachmentManager.clear();
            composeText.setText("");
        }

        setIntent(intent);
        initializeResources();
        initializeDraft();

        if (fragment != null) {
            fragment.onNewIntent();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        dynamicTheme.onResume(this);
        dynamicLanguage.onResume(this);
        //handler.postDelayed(runnable, 5000);
        initializeSecurity();
        initializeTitleBar();
        initializeEnabledCheck();
        initializeMmsEnabledCheck();
        initializeIme();
        calculateCharactersRemaining();
        if (mAdView != null) {
            mAdView.resume();
        }
        MessageNotifier.setVisibleThread(threadId);
        markThreadAsRead();
    }

    @Override
    protected void onStop() {
        super.onStop();
//        if(runnable != null)
//            handler.removeCallbacks(runnable);
//        timer.cancel();
//        timer.purge();
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (mAdView != null) {
            mAdView.pause();
        }
        MessageNotifier.setVisibleThread(-1L);
        if (isFinishing()) overridePendingTransition(R.anim.fade_scale_in, R.anim.slide_to_right);
    }

    @Override
    protected void onDestroy() {
        saveDraft();
//        if(runnable != null)
//        handler.removeCallbacks(runnable);
        if (mAdView != null) {
            mAdView.destroy();
        }
        if (recipients != null) recipients.removeListener(this);
        if (securityUpdateReceiver != null) unregisterReceiver(securityUpdateReceiver);
        if (groupUpdateReceiver != null) unregisterReceiver(groupUpdateReceiver);
        super.onDestroy();
    }

    @Override
    public void onActivityResult(int reqCode, int resultCode, Intent data) {
        Log.w(TAG, "onActivityResult called: " + reqCode + ", " + resultCode + " , " + data);
        super.onActivityResult(reqCode, resultCode, data);

        if ((data == null && reqCode != CAPTURE_PHOTO) || resultCode != RESULT_OK) return;

        switch (reqCode) {
            case PICK_IMAGE:
                addAttachmentImage(data.getData());
                break;
            case PICK_VIDEO:
                addAttachmentVideo(data.getData());
                break;
            case PICK_AUDIO:
                addAttachmentAudio(data.getData());
                break;
            case PICK_CONTACT_INFO:
                addAttachmentContactInfo(data.getData());
                break;
            case CAPTURE_PHOTO:
                if (attachmentManager.getCaptureFile() != null) {
                    addAttachmentImage(Uri.fromFile(attachmentManager.getCaptureFile()));
                }
                break;
            case GROUP_EDIT:
                this.recipients = RecipientFactory.getRecipientsForIds(this, data.getLongArrayExtra(GroupCreateActivity.GROUP_RECIPIENT_EXTRA), true);
                initializeTitleBar();
                break;
        }
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        MenuInflater inflater = this.getMenuInflater();
        menu.clear();

        if (isSingleConversation() && isEncryptedConversation) {
            inflater.inflate(R.menu.conversation_secure_identity, menu);
            inflater.inflate(R.menu.conversation_secure_sms, menu.findItem(R.id.menu_security).getSubMenu());
        } else if (isSingleConversation()) {
            inflater.inflate(R.menu.conversation_insecure, menu);
        }

        if (isSingleConversation()) {
            inflater.inflate(R.menu.conversation_callable, menu);
        } else if (isGroupConversation()) {
            inflater.inflate(R.menu.conversation_group_options, menu);

            if (!isPushGroupConversation()) {
                inflater.inflate(R.menu.conversation_mms_group_options, menu);
                if (distributionType == ThreadDatabase.DistributionTypes.BROADCAST) {
                    menu.findItem(R.id.menu_distribution_broadcast).setChecked(true);
                } else {
                    menu.findItem(R.id.menu_distribution_conversation).setChecked(true);
                }
            } else if (isActiveGroup()) {
                inflater.inflate(R.menu.conversation_push_group_options, menu);
            }
        }

        inflater.inflate(R.menu.conversation, menu);

        if (isSingleConversation() && getRecipients().getPrimaryRecipient().getContactUri() == null) {
            inflater.inflate(R.menu.conversation_add_to_contacts, menu);
        }

        super.onPrepareOptionsMenu(menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        super.onOptionsItemSelected(item);
        switch (item.getItemId()) {
            case R.id.menu_call:
                handleDial(getRecipients().getPrimaryRecipient());
                return true;
            case R.id.menu_delete_thread:
                handleDeleteThread();
                return true;
            case R.id.menu_add_attachment:
                handleAddAttachment();
                return true;
            case R.id.menu_view_media:
                handleViewMedia();
                return true;
            case R.id.menu_add_to_contacts:
                handleAddToContacts();
                return true;
            case R.id.menu_abort_session:
                handleAbortSecureSession();
                return true;
            case R.id.menu_verify_identity:
                handleVerifyIdentity();
                return true;
            case R.id.menu_group_recipients:
                handleDisplayGroupRecipients();
                return true;
            case R.id.menu_distribution_broadcast:
                handleDistributionBroadcastEnabled(item);
                return true;
            case R.id.menu_distribution_conversation:
                handleDistributionConversationEnabled(item);
                return true;
            case R.id.menu_edit_group:
                handleEditPushGroup();
                return true;
            case R.id.menu_leave:
                handleLeavePushGroup();
                return true;
            case R.id.menu_invite:
                handleInviteLink();
                return true;
            case android.R.id.home:
                handleReturnToConversationList();
                return true;
        }

        return false;
    }

    @Override
    public void onBackPressed() {
        if (isEmojiDrawerOpen()) {
            getEmojiDrawer().hide();
            emojiToggle.toggle();
        } else {
            super.onBackPressed();
        }
    }

    //////// Event Handlers

    private void handleReturnToConversationList() {
        Intent intent = new Intent(this, ConversationListActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        startActivity(intent);
        finish();
    }

    private void handleInviteLink() {
        try {
            boolean a = SecureRandom.getInstance("SHA1PRNG").nextBoolean();
            if (a)
                composeText.appendInvite(getString(R.string.ConversationActivity_get_with_it, "https://play.google.com/store/apps/details?id=org.grovecity.drizzlesms"));
            else
                composeText.appendInvite(getString(R.string.ConversationActivity_lets_use_this_to_chat, "https://play.google.com/store/apps/details?id=org.grovecity.drizzlesms"));
        } catch (NoSuchAlgorithmException e) {
            throw new AssertionError(e);
        }
    }

    private void handleVerifyIdentity() {
        Intent verifyIdentityIntent = new Intent(this, VerifyIdentityActivity.class);
        verifyIdentityIntent.putExtra("recipient", getRecipients().getPrimaryRecipient().getRecipientId());
        startActivity(verifyIdentityIntent);
    }

    private void handleAbortSecureSession() {
        AlertDialogWrapper.Builder builder = new AlertDialogWrapper.Builder(this);
        builder.setTitle(R.string.ConversationActivity_abort_secure_session_confirmation);
        builder.setIconAttribute(R.attr.dialog_alert_icon);
        builder.setCancelable(true);
        builder.setMessage(R.string.ConversationActivity_are_you_sure_that_you_want_to_abort_this_secure_session_question);
        builder.setPositiveButton(R.string.yes, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                if (isSingleConversation()) {
                    final Context context = getApplicationContext();

                    OutgoingEndSessionMessage endSessionMessage =
                            new OutgoingEndSessionMessage(new OutgoingTextMessage(getRecipients(), "TERMINATE"));

                    new AsyncTask<OutgoingEndSessionMessage, Void, Long>() {
                        @Override
                        protected Long doInBackground(OutgoingEndSessionMessage... messages) {
                            return MessageSender.send(context, masterSecret, messages[0], threadId, false);
                        }

                        @Override
                        protected void onPostExecute(Long result) {
                            sendComplete(result);
                        }
                    }.execute(endSessionMessage);
                }
            }
        });
        builder.setNegativeButton(R.string.no, null);
        builder.show();
    }

    private void handleViewMedia() {
        Intent intent = new Intent(this, MediaOverviewActivity.class);
        intent.putExtra(MediaOverviewActivity.THREAD_ID_EXTRA, threadId);
        intent.putExtra(MediaOverviewActivity.RECIPIENT_EXTRA, recipients.getPrimaryRecipient().getRecipientId());
        startActivity(intent);
    }

    private void handleLeavePushGroup() {
        if (getRecipients() == null) {
            Toast.makeText(this, getString(R.string.ConversationActivity_invalid_recipient),
                    Toast.LENGTH_LONG).show();
            return;
        }

        AlertDialogWrapper.Builder builder = new AlertDialogWrapper.Builder(this);
        builder.setTitle(getString(R.string.ConversationActivity_leave_group));
        builder.setIconAttribute(R.attr.dialog_info_icon);
        builder.setCancelable(true);
        builder.setMessage(getString(R.string.ConversationActivity_are_you_sure_you_want_to_leave_this_group));
        builder.setPositiveButton(R.string.yes, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                Context self = ConversationActivity.this;
                try {
                    byte[] groupId = GroupUtil.getDecodedId(getRecipients().getPrimaryRecipient().getNumber());
                    DatabaseFactory.getGroupDatabase(self).setActive(groupId, false);

                    GroupContext context = GroupContext.newBuilder()
                            .setId(ByteString.copyFrom(groupId))
                            .setType(GroupContext.Type.QUIT)
                            .build();

                    OutgoingGroupMediaMessage outgoingMessage = new OutgoingGroupMediaMessage(self, getRecipients(),
                            context, null);
                    MessageSender.send(self, masterSecret, outgoingMessage, threadId, false);
                    DatabaseFactory.getGroupDatabase(self).remove(groupId, DrizzleSmsPreferences.getLocalNumber(self));
                    initializeEnabledCheck();
                } catch (IOException e) {
                    Log.w(TAG, e);
                    Toast.makeText(self, R.string.ConversationActivity_error_leaving_group, Toast.LENGTH_LONG).show();
                }
            }
        });

        builder.setNegativeButton(R.string.no, null);
        builder.show();
    }

    private void handleEditPushGroup() {
        Intent intent = new Intent(ConversationActivity.this, GroupCreateActivity.class);
        intent.putExtra(GroupCreateActivity.GROUP_RECIPIENT_EXTRA, recipients.getPrimaryRecipient().getRecipientId());
        startActivityForResult(intent, GROUP_EDIT);
    }

    private void handleDistributionBroadcastEnabled(MenuItem item) {
        distributionType = ThreadDatabase.DistributionTypes.BROADCAST;
        item.setChecked(true);

        if (threadId != -1) {
            new AsyncTask<Void, Void, Void>() {
                @Override
                protected Void doInBackground(Void... params) {
                    DatabaseFactory.getThreadDatabase(ConversationActivity.this)
                            .setDistributionType(threadId, ThreadDatabase.DistributionTypes.BROADCAST);
                    return null;
                }
            }.execute();
        }
    }

    private void handleDistributionConversationEnabled(MenuItem item) {
        distributionType = ThreadDatabase.DistributionTypes.CONVERSATION;
        item.setChecked(true);

        if (threadId != -1) {
            new AsyncTask<Void, Void, Void>() {
                @Override
                protected Void doInBackground(Void... params) {
                    DatabaseFactory.getThreadDatabase(ConversationActivity.this)
                            .setDistributionType(threadId, ThreadDatabase.DistributionTypes.CONVERSATION);
                    return null;
                }
            }.execute();
        }
    }

    private void handleDial(Recipient recipient) {
        try {
            if (recipient == null) return;

            Intent dialIntent = new Intent(Intent.ACTION_DIAL,
                    Uri.parse("tel:" + recipient.getNumber()));
            startActivity(dialIntent);
        } catch (ActivityNotFoundException anfe) {
            Log.w(TAG, anfe);
            Dialogs.showAlertDialog(this,
                    getString(R.string.ConversationActivity_calls_not_supported),
                    getString(R.string.ConversationActivity_this_device_does_not_appear_to_support_dial_actions));
        }
    }

    private void handleDisplayGroupRecipients() {
        new GroupMembersDialog(this, getRecipients()).display();
    }

    private void handleDeleteThread() {
        AlertDialogWrapper.Builder builder = new AlertDialogWrapper.Builder(this);
        builder.setTitle(R.string.ConversationActivity_delete_thread_confirmation);
        builder.setIconAttribute(R.attr.dialog_alert_icon);
        builder.setCancelable(true);
        builder.setMessage(R.string.ConversationActivity_are_you_sure_that_you_want_to_permanently_delete_this_conversation_question);
        builder.setPositiveButton(R.string.yes, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                if (threadId > 0) {
                    DatabaseFactory.getThreadDatabase(ConversationActivity.this).deleteConversation(threadId);
                }
                composeText.getText().clear();
                threadId = -1;
                finish();
            }
        });

        builder.setNegativeButton(R.string.no, null);
        builder.show();
    }

    private void handleAddToContacts() {
        final Intent intent = new Intent(Intent.ACTION_INSERT_OR_EDIT);
        intent.putExtra(ContactsContract.Intents.Insert.PHONE, recipients.getPrimaryRecipient().getNumber());
        intent.setType(ContactsContract.Contacts.CONTENT_ITEM_TYPE);
        startActivity(intent);
    }

    private void handleAddAttachment() {
        if (this.isMmsEnabled || DirectoryHelper.isPushDestination(this, getRecipients())) {
            new AlertDialogWrapper.Builder(this).setAdapter(attachmentAdapter, new AttachmentTypeListener())
                    .show();
        } else {
            handleManualMmsRequired();
        }
    }

    private void handleManualMmsRequired() {
        Toast.makeText(this, R.string.MmsDownloader_error_reading_mms_settings, Toast.LENGTH_LONG).show();

        Intent intent = new Intent(this, PromptMmsActivity.class);
        intent.putExtras(getIntent().getExtras());
        startActivity(intent);
    }

    ///// Initializers

    private void initializeTitleBar() {
        final String title;
        final String subtitle;
        final Recipient recipient = getRecipients().getPrimaryRecipient();

        if (isSingleConversation()) {
            if (TextUtils.isEmpty(recipient.getName())) {
                title = recipient.getNumber();
                subtitle = null;
            } else {
                title = recipient.getName();
                subtitle = PhoneNumberUtils.formatNumber(recipient.getNumber());
            }
        } else if (isGroupConversation()) {
            if (isPushGroupConversation()) {
                final String groupName = recipient.getName();

                title = (!TextUtils.isEmpty(groupName)) ? groupName : getString(R.string.ConversationActivity_unnamed_group);
                subtitle = null;
            } else {
                final int size = getRecipients().getRecipientsList().size();

                title = getString(R.string.ConversationActivity_group_conversation);
                subtitle = (size == 1) ? getString(R.string.ConversationActivity_d_recipients_in_group_singular)
                        : String.format(getString(R.string.ConversationActivity_d_recipients_in_group), size);
            }
        } else {
            title = getString(R.string.ConversationActivity_compose_message);
            subtitle = null;
        }

        getSupportActionBar().setTitle(title);
        getSupportActionBar().setSubtitle(subtitle);

        getWindow().getDecorView().setContentDescription(getString(R.string.conversation_activity__window_description, title));

        this.supportInvalidateOptionsMenu();
    }

    private void initializeDraft() {
        String draftText = getIntent().getStringExtra(DRAFT_TEXT_EXTRA);
        Uri draftImage = getIntent().getParcelableExtra(DRAFT_IMAGE_EXTRA);
        Uri draftAudio = getIntent().getParcelableExtra(DRAFT_AUDIO_EXTRA);
        Uri draftVideo = getIntent().getParcelableExtra(DRAFT_VIDEO_EXTRA);

        if (draftText != null) composeText.setText(draftText);
        if (draftImage != null) addAttachmentImage(draftImage);
        if (draftAudio != null) addAttachmentAudio(draftAudio);
        if (draftVideo != null) addAttachmentVideo(draftVideo);

        if (draftText == null && draftImage == null && draftAudio == null && draftVideo == null) {
            initializeDraftFromDatabase();
        } else {
            updateToggleButtonState();
        }
    }

    private void initializeEnabledCheck() {
        boolean enabled = !(isPushGroupConversation() && !isActiveGroup());
        composeText.setEnabled(enabled);
        sendButton.setEnabled(enabled);
    }

    private void initializeDraftFromDatabase() {
        new AsyncTask<Void, Void, List<DraftDatabase.Draft>>() {
            @Override
            protected List<DraftDatabase.Draft> doInBackground(Void... params) {
                MasterCipher masterCipher = new MasterCipher(masterSecret);
                DraftDatabase draftDatabase = DatabaseFactory.getDraftDatabase(ConversationActivity.this);
                List<DraftDatabase.Draft> results = draftDatabase.getDrafts(masterCipher, threadId);

                draftDatabase.clearDrafts(threadId);

                return results;
            }

            @Override
            protected void onPostExecute(List<DraftDatabase.Draft> drafts) {
                for (DraftDatabase.Draft draft : drafts) {
                    if (draft.getType().equals(DraftDatabase.Draft.TEXT)) {
                        composeText.setText(draft.getValue());
                    } else if (draft.getType().equals(DraftDatabase.Draft.IMAGE)) {
                        addAttachmentImage(Uri.parse(draft.getValue()));
                    } else if (draft.getType().equals(DraftDatabase.Draft.AUDIO)) {
                        addAttachmentAudio(Uri.parse(draft.getValue()));
                    } else if (draft.getType().equals(DraftDatabase.Draft.VIDEO)) {
                        addAttachmentVideo(Uri.parse(draft.getValue()));
                    }
                }

                updateToggleButtonState();
            }
        }.execute();
    }

    private void initializeSecurity() {
        boolean isMediaMessage = !recipients.isSingleRecipient() || attachmentManager.isAttachmentPresent();
        this.isEncryptedConversation = DirectoryHelper.isPushDestination(this, getRecipients());

        sendButton.resetAvailableTransports(isMediaMessage);

        if (!isEncryptedConversation) sendButton.disableTransport(TransportOption.Type.TEXTSECURE);
        if (recipients.isGroupRecipient()) sendButton.disableTransport(TransportOption.Type.SMS);

        if (isEncryptedConversation) sendButton.setDefaultTransport(TransportOption.Type.TEXTSECURE);
        else sendButton.setDefaultTransport(TransportOption.Type.SMS);

        calculateCharactersRemaining();
    }

    private void initializeMmsEnabledCheck() {
        new AsyncTask<Void, Void, Boolean>() {
            @Override
            protected Boolean doInBackground(Void... params) {
                return Util.isMmsCapable(ConversationActivity.this);
            }

            @Override
            protected void onPostExecute(Boolean isMmsEnabled) {
                ConversationActivity.this.isMmsEnabled = isMmsEnabled;
            }
        }.execute();
    }

    private void initializeIme() {
        if (DrizzleSmsPreferences.isEnterSendsEnabled(this)) {
            composeText.setInputType(composeText.getInputType() & ~InputType.TYPE_TEXT_FLAG_MULTI_LINE);
            composeText.setImeOptions(composeText.getImeOptions() & ~EditorInfo.IME_FLAG_NO_ENTER_ACTION);
        } else {
            composeText.setInputType(composeText.getInputType() | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
            composeText.setImeOptions(composeText.getImeOptions() | EditorInfo.IME_FLAG_NO_ENTER_ACTION);
        }
    }

    private void initializeViews() {
        buttonToggle = (AnimatingToggle) findViewById(R.id.button_toggle);
        sendButton = (SendButton) findViewById(R.id.send_button);
        attachButton = (ImageButton) findViewById(R.id.attach_button);
        composeText = (ComposeText) findViewById(R.id.embedded_text_editor);
        charactersLeft = (TextView) findViewById(R.id.space_left);
        emojiToggle = (EmojiToggle) findViewById(R.id.emoji_toggle);

        attachmentAdapter = new AttachmentTypeSelectorAdapter(this);
        attachmentManager = new AttachmentManager(this, this);

        SendButtonListener sendButtonListener = new SendButtonListener();
        ComposeKeyPressedListener composeKeyPressedListener = new ComposeKeyPressedListener();

        attachButton.setOnClickListener(new AttachButtonListener());
        sendButton.setOnClickListener(sendButtonListener);
        sendButton.setEnabled(true);
        sendButton.addOnTransportChangedListener(new OnTransportChangedListener() {
            @Override
            public void onChange(TransportOption newTransport) {
                calculateCharactersRemaining();
                composeText.setHint(newTransport.getComposeHint());
            }
        });

        composeText.setOnKeyListener(composeKeyPressedListener);
        composeText.addTextChangedListener(composeKeyPressedListener);
        composeText.setOnEditorActionListener(sendButtonListener);
        composeText.setOnClickListener(composeKeyPressedListener);
        composeText.setOnFocusChangeListener(composeKeyPressedListener);
        emojiToggle.setOnClickListener(new EmojiToggleListener());
    }

    private EmojiDrawer getEmojiDrawer() {
        if (emojiDrawer.isPresent()) return emojiDrawer.get();
        EmojiDrawer emojiDrawer = (EmojiDrawer) ((ViewStub) findViewById(R.id.emoji_drawer_stub)).inflate();
        emojiDrawer.setComposeEditText(composeText);
        this.emojiDrawer = Optional.of(emojiDrawer);
        return emojiDrawer;
    }

    private boolean isEmojiDrawerOpen() {
        return emojiDrawer.isPresent() && emojiDrawer.get().isOpen();
    }

    private void initializeResources() {
        recipients = RecipientFactory.getRecipientsForIds(this, getIntent().getLongArrayExtra(RECIPIENTS_EXTRA), true);
        threadId = getIntent().getLongExtra(THREAD_ID_EXTRA, -1);
        distributionType = getIntent().getIntExtra(DISTRIBUTION_TYPE_EXTRA, ThreadDatabase.DistributionTypes.DEFAULT);

        recipients.addListener(this);
    }

    @Override
    public void onModified(Recipient recipient) {
        initializeTitleBar();
    }

    private void initializeReceivers() {
        securityUpdateReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                long eventThreadId = intent.getLongExtra("thread_id", -1);

                if (eventThreadId == threadId || eventThreadId == -2) {
                    initializeSecurity();
                    initializeTitleBar();
                    calculateCharactersRemaining();
                }
            }
        };

        groupUpdateReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                Log.w("ConversationActivity", "Group update received...");
                if (recipients != null) {
                    long[] ids = recipients.getIds();
                    Log.w("ConversationActivity", "Looking up new recipients...");
                    recipients = RecipientFactory.getRecipientsForIds(context, ids, false);
                    initializeTitleBar();
                }
            }
        };

        registerReceiver(securityUpdateReceiver,
                new IntentFilter(SecurityEvent.SECURITY_UPDATE_EVENT),
                KeyCachingService.KEY_PERMISSION, null);

        registerReceiver(groupUpdateReceiver,
                new IntentFilter(GroupDatabase.DATABASE_UPDATE_ACTION));
    }

    //////// Helper Methods

    private void addAttachment(int type) {
        Log.w("ComposeMessageActivity", "Selected: " + type);
        switch (type) {
            case AttachmentTypeSelectorAdapter.TAKE_PHOTO:
                attachmentManager.capturePhoto(this, CAPTURE_PHOTO);
                break;
            case AttachmentTypeSelectorAdapter.ADD_IMAGE:
                AttachmentManager.selectImage(this, PICK_IMAGE);
                break;
            case AttachmentTypeSelectorAdapter.ADD_VIDEO:
                AttachmentManager.selectVideo(this, PICK_VIDEO);
                break;
            case AttachmentTypeSelectorAdapter.ADD_SOUND:
                AttachmentManager.selectAudio(this, PICK_AUDIO);
                break;
            case AttachmentTypeSelectorAdapter.ADD_CONTACT_INFO:
                AttachmentManager.selectContactInfo(this, PICK_CONTACT_INFO);
                break;
        }
    }

    private void addAttachmentImage(Uri imageUri) {
        try {
            attachmentManager.setImage(imageUri);
        } catch (IOException | BitmapDecodingException e) {
            Log.w(TAG, e);
            attachmentManager.clear();
            Toast.makeText(this, R.string.ConversationActivity_sorry_there_was_an_error_setting_your_attachment,
                    Toast.LENGTH_LONG).show();
        }
    }

    private void addAttachmentVideo(Uri videoUri) {
        try {
            attachmentManager.setVideo(videoUri);
        } catch (IOException e) {
            attachmentManager.clear();
            Toast.makeText(this, R.string.ConversationActivity_sorry_there_was_an_error_setting_your_attachment,
                    Toast.LENGTH_LONG).show();
            Log.w("ComposeMessageActivity", e);
        } catch (MediaTooLargeException e) {
            attachmentManager.clear();

            Toast.makeText(this, getString(R.string.ConversationActivity_sorry_the_selected_video_exceeds_message_size_restrictions,
                            (MmsMediaConstraints.MAX_MESSAGE_SIZE / 1024)),
                    Toast.LENGTH_LONG).show();
            Log.w("ComposeMessageActivity", e);
        }
    }

    private void addAttachmentAudio(Uri audioUri) {
        try {
            attachmentManager.setAudio(audioUri);
        } catch (IOException e) {
            attachmentManager.clear();
            Toast.makeText(this, R.string.ConversationActivity_sorry_there_was_an_error_setting_your_attachment,
                    Toast.LENGTH_LONG).show();
            Log.w("ComposeMessageActivity", e);
        } catch (MediaTooLargeException e) {
            attachmentManager.clear();
            Toast.makeText(this, getString(R.string.ConversationActivity_sorry_the_selected_audio_exceeds_message_size_restrictions,
                            (MmsMediaConstraints.MAX_MESSAGE_SIZE / 1024)),
                    Toast.LENGTH_LONG).show();
            Log.w("ComposeMessageActivity", e);
        }
    }

    private void addAttachmentContactInfo(Uri contactUri) {
        ContactAccessor contactDataList = ContactAccessor.getInstance();
        ContactAccessor.ContactData contactData = contactDataList.getContactData(this, contactUri);

        if (contactData.numbers.size() == 1) composeText.append(contactData.numbers.get(0).number);
        else if (contactData.numbers.size() > 1) selectContactInfo(contactData);
    }

    private void selectContactInfo(ContactAccessor.ContactData contactData) {
        final CharSequence[] numbers = new CharSequence[contactData.numbers.size()];
        final CharSequence[] numberItems = new CharSequence[contactData.numbers.size()];

        for (int i = 0; i < contactData.numbers.size(); i++) {
            numbers[i] = contactData.numbers.get(i).number;
            numberItems[i] = contactData.numbers.get(i).type + ": " + contactData.numbers.get(i).number;
        }

        AlertDialogWrapper.Builder builder = new AlertDialogWrapper.Builder(this);
        builder.setIconAttribute(R.attr.conversation_attach_contact_info);
        builder.setTitle(R.string.ConversationActivity_select_contact_info);

        builder.setItems(numberItems, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                composeText.append(numbers[which]);
            }
        });
        builder.show();
    }

    private DraftDatabase.Drafts getDraftsForCurrentState() {
        DraftDatabase.Drafts drafts = new DraftDatabase.Drafts();

        if (!Util.isEmpty(composeText)) {
            drafts.add(new DraftDatabase.Draft(DraftDatabase.Draft.TEXT, composeText.getText().toString()));
        }

        for (Slide slide : attachmentManager.getSlideDeck().getSlides()) {
            if (slide.hasAudio()) drafts.add(new DraftDatabase.Draft(DraftDatabase.Draft.AUDIO, slide.getUri().toString()));
            else if (slide.hasVideo())
                drafts.add(new DraftDatabase.Draft(DraftDatabase.Draft.VIDEO, slide.getUri().toString()));
            else if (slide.hasImage())
                drafts.add(new DraftDatabase.Draft(DraftDatabase.Draft.IMAGE, slide.getUri().toString()));
        }

        return drafts;
    }

    private void saveDraft() {
        if (this.recipients == null || this.recipients.isEmpty())
            return;

        final DraftDatabase.Drafts drafts = getDraftsForCurrentState();
        final long thisThreadId = this.threadId;
        final MasterSecret thisMasterSecret = this.masterSecret.parcelClone();
        final int thisDistributionType = this.distributionType;

        new AsyncTask<Long, Void, Void>() {
            @Override
            protected Void doInBackground(Long... params) {
                ThreadDatabase threadDatabase = DatabaseFactory.getThreadDatabase(ConversationActivity.this);
                DraftDatabase draftDatabase = DatabaseFactory.getDraftDatabase(ConversationActivity.this);
                long threadId = params[0];

                if (drafts.size() > 0) {
                    if (threadId == -1)
                        threadId = threadDatabase.getThreadIdFor(getRecipients(), thisDistributionType);

                    draftDatabase.insertDrafts(new MasterCipher(thisMasterSecret), threadId, drafts);
                    threadDatabase.updateSnippet(threadId, drafts.getSnippet(ConversationActivity.this), System.currentTimeMillis(), MmsSmsColumns.Types.BASE_DRAFT_TYPE);
                } else if (threadId > 0) {
                    threadDatabase.update(threadId);
                }
                return null;
            }
        }.execute(thisThreadId);
    }

    private void calculateCharactersRemaining() {
        int charactersSpent = composeText.getText().toString().length();
        TransportOption transportOption = sendButton.getSelectedTransport();

        CharacterCalculator.CharacterState characterState = transportOption.calculateCharacters(charactersSpent);

        if (characterState.charactersRemaining <= 15 || characterState.messagesSpent > 1) {
            charactersLeft.setText(characterState.charactersRemaining + "/" + characterState.maxMessageSize
                    + " (" + characterState.messagesSpent + ")");
            charactersLeft.setVisibility(View.VISIBLE);
        } else {
            charactersLeft.setVisibility(View.GONE);
        }
    }

    private boolean isSingleConversation() {
        return getRecipients() != null && getRecipients().isSingleRecipient() && !getRecipients().isGroupRecipient();
    }

    private boolean isActiveGroup() {
        if (!isGroupConversation()) return false;

        try {
            byte[] groupId = GroupUtil.getDecodedId(getRecipients().getPrimaryRecipient().getNumber());
            GroupRecord record = DatabaseFactory.getGroupDatabase(this).getGroup(groupId);

            return record != null && record.isActive();
        } catch (IOException e) {
            Log.w("ConversationActivity", e);
            return false;
        }
    }

    private boolean isGroupConversation() {
        return getRecipients() != null &&
                (!getRecipients().isSingleRecipient() || getRecipients().isGroupRecipient());
    }

    private boolean isPushGroupConversation() {
        return getRecipients() != null && getRecipients().isGroupRecipient();
    }

    private Recipients getRecipients() {
        return this.recipients;
    }

    private String getMessage() throws InvalidMessageException {
        String rawText = composeText.getText().toString();

        if (rawText.length() < 1 && !attachmentManager.isAttachmentPresent())
            throw new InvalidMessageException(getString(R.string.ConversationActivity_message_is_empty_exclamation));

        return rawText;
    }

    private void markThreadAsRead() {
        new AsyncTask<Long, Void, Void>() {
            @Override
            protected Void doInBackground(Long... params) {
                DatabaseFactory.getThreadDatabase(ConversationActivity.this).setRead(params[0]);
                MessageNotifier.updateNotification(ConversationActivity.this, masterSecret);
                return null;
            }
        }.execute(threadId);
    }

    private void sendComplete(long threadId) {
        boolean refreshFragment = (threadId != this.threadId);
        this.threadId = threadId;

        if (fragment == null || !fragment.isVisible() || isFinishing()) {
            return;
        }

        if (refreshFragment) {
            fragment.reload(recipients, threadId);

            initializeTitleBar();
            initializeSecurity();
        }

        fragment.scrollToBottom();
        attachmentManager.cleanup();
    }

    private void sendMessage() {
        try {
            Recipients recipients = getRecipients();
            boolean forceSms = sendButton.isManualSelection() && sendButton.getSelectedTransport().isSms();

            Log.w(TAG, "isManual Selection: " + sendButton.isManualSelection());
            Log.w(TAG, "forceSms: " + forceSms);

            if (recipients == null) {
                throw new RecipientFormattingException("Badly formatted");
            }

            if ((!recipients.isSingleRecipient() || recipients.isEmailRecipient()) && !isMmsEnabled) {
                handleManualMmsRequired();
            } else if (attachmentManager.isAttachmentPresent() || !recipients.isSingleRecipient() || recipients.isGroupRecipient() || recipients.isEmailRecipient()) {
                sendMediaMessage(forceSms);
            } else {
                sendTextMessage(forceSms);
            }
        } catch (RecipientFormattingException ex) {
            Toast.makeText(ConversationActivity.this,
                    R.string.ConversationActivity_recipient_is_not_a_valid_sms_or_email_address_exclamation,
                    Toast.LENGTH_LONG).show();
            Log.w(TAG, ex);
        } catch (InvalidMessageException ex) {
            Toast.makeText(ConversationActivity.this, R.string.ConversationActivity_message_is_empty_exclamation,
                    Toast.LENGTH_SHORT).show();
            Log.w(TAG, ex);
        }
    }

    private void sendMediaMessage(final boolean forceSms)
            throws InvalidMessageException {
        final Context context = getApplicationContext();
        SlideDeck slideDeck;

        if (attachmentManager.isAttachmentPresent())
            slideDeck = new SlideDeck(attachmentManager.getSlideDeck());
        else slideDeck = new SlideDeck();

        OutgoingMediaMessage outgoingMessage = new OutgoingMediaMessage(this, recipients, slideDeck,
                getMessage(), distributionType);

        if (isEncryptedConversation && !forceSms) {
            outgoingMessage = new OutgoingSecureMediaMessage(outgoingMessage);
        }

        attachmentManager.clear();
        composeText.setText("");

        new AsyncTask<OutgoingMediaMessage, Void, Long>() {
            @Override
            protected Long doInBackground(OutgoingMediaMessage... messages) {
                return MessageSender.send(context, masterSecret, messages[0], threadId, forceSms);
            }

            @Override
            protected void onPostExecute(Long result) {
                sendComplete(result);
            }
        }.execute(outgoingMessage);
    }

    private void sendTextMessage(final boolean forceSms)
            throws InvalidMessageException {
        final Context context = getApplicationContext();
        OutgoingTextMessage message;

        if (isEncryptedConversation && !forceSms) {
            message = new OutgoingEncryptedMessage(recipients, getMessage());
        } else {
            message = new OutgoingTextMessage(recipients, getMessage());
        }

        this.composeText.setText("");

        new AsyncTask<OutgoingTextMessage, Void, Long>() {
            @Override
            protected Long doInBackground(OutgoingTextMessage... messages) {
                return MessageSender.send(context, masterSecret, messages[0], threadId, forceSms);
            }

            @Override
            protected void onPostExecute(Long result) {
                sendComplete(result);
            }
        }.execute(message);
    }

    private void updateToggleButtonState() {
        if (composeText.getText().length() == 0 && !attachmentManager.isAttachmentPresent()) {
            buttonToggle.display(attachButton);
        } else {
            buttonToggle.display(sendButton);
        }
    }

    // Listeners

    private class AttachmentTypeListener implements DialogInterface.OnClickListener {
        @Override
        public void onClick(DialogInterface dialog, int which) {
            addAttachment(attachmentAdapter.buttonToCommand(which));
            dialog.dismiss();
        }
    }

    private class EmojiToggleListener implements OnClickListener {
        @Override
        public void onClick(View v) {
            InputMethodManager input = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);

            if (isEmojiDrawerOpen()) {
                input.showSoftInput(composeText, 0);
                getEmojiDrawer().hide();
            } else {
                input.hideSoftInputFromWindow(composeText.getWindowToken(), 0);

                getEmojiDrawer().show();
            }
        }
    }

    private class SendButtonListener implements OnClickListener, TextView.OnEditorActionListener {
        @Override
        public void onClick(View v) {
            sendMessage();
        }

        @Override
        public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
            if (actionId == EditorInfo.IME_ACTION_SEND) {
                sendButton.performClick();
                return true;
            }
            return false;
        }
    }

    private class AttachButtonListener implements OnClickListener {
        @Override
        public void onClick(View v) {
            handleAddAttachment();
        }
    }


    private class ComposeKeyPressedListener implements OnKeyListener, OnClickListener, TextWatcher, OnFocusChangeListener {

        int beforeLength;

        @Override
        public boolean onKey(View v, int keyCode, KeyEvent event) {
            if (event.getAction() == KeyEvent.ACTION_DOWN) {
                if (keyCode == KeyEvent.KEYCODE_ENTER) {
                    if (DrizzleSmsPreferences.isEnterSendsEnabled(ConversationActivity.this)) {
                        sendButton.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER));
                        sendButton.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_ENTER));
                        return true;
                    }
                }
            }
            return false;
        }

        @Override
        public void onClick(View v) {
            if (isEmojiDrawerOpen()) {
                emojiToggle.performClick();
            }
        }

        @Override
        public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            beforeLength = composeText.getText().length();
        }

        @Override
        public void afterTextChanged(Editable s) {
            calculateCharactersRemaining();

            if (composeText.getText().length() == 0 || beforeLength == 0) {
                composeText.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        updateToggleButtonState();
                    }
                }, 50);
            }
        }

        @Override
        public void onTextChanged(CharSequence s, int start, int before, int count) {
        }

        @Override
        public void onFocusChange(View v, boolean hasFocus) {
            if (hasFocus && isEmojiDrawerOpen()) {
                emojiToggle.performClick();
            }
        }
    }

    @Override
    public void setComposeText(String text) {
        this.composeText.setText(text);
    }

    @Override
    public void setThreadId(long threadId) {
        this.threadId = threadId;
    }

    @Override
    public void onAttachmentChanged() {
        initializeSecurity();
        updateToggleButtonState();
    }


    protected void refreshAds(){
        final Handler h = new Handler();
        final int delay = 45000; //milliseconds

        h.postDelayed(new Runnable(){
            public void run(){
                loadAd();
                h.postDelayed(this, delay);
            }
        }, delay);
    }

    protected  void loadAd(){

        //this.adView.loadAd();

    }

    /**
     * This class is for an event listener that tracks ad lifecycle events.
     * It extends DefaultAdListener, so you can override only the methods that you need.
     */
//    class SampleAdListener extends DefaultAdListener {
//        /**
//         * This event is called once an ad loads successfully.
//         */
//        @Override
//        public void onAdLoaded(final Ad ad, final AdProperties adProperties) {
//            Log.i(LOG_TAG, adProperties.getAdType().toString() + " ad loaded successfully.");
//            //refreshAds();
//            ParseQuery<ParseUser> query = ParseUser.getQuery();
//            if(ParseUser.getCurrentUser()!=null) {
//                query.getInBackground(ParseUser.getCurrentUser().getObjectId(), new GetCallback<ParseUser>() {
//                    @Override
//                    public void done(ParseUser parseUser, ParseException e) {
//                        parseUser.increment("bannerCount");
//                        parseUser.saveInBackground();
//                    }
//                });
//
//            }
//
//        }
//
//        /**
//         * This event is called if an ad fails to load.
//         */
//        @Override
//        public void onAdFailedToLoad(final Ad ad, final AdError error) {
//            Log.w(LOG_TAG, "Ad failed to load. Code: " + error.getCode() + ", Message: " + error.getMessage());
//        }
//
//        /**
//         * This event is called after a rich media ad expands.
//         */
//        @Override
//        public void onAdExpanded(final Ad ad) {
//            Log.i(LOG_TAG, "Ad expanded.");
//            // You may want to pause your activity here.
//        }
//
//        /**
//         * This event is called after a rich media ad has collapsed from an expanded state.
//         */
//        @Override
//        public void onAdCollapsed(final Ad ad) {
//            Log.i(LOG_TAG, "Ad collapsed.");
//            // Resume your activity here, if it was paused in onAdExpanded.
//        }
//    }
}
