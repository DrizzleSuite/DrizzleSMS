package org.grovecity.drizzlesms.jobs;

import android.content.Context;
import android.util.Log;
import android.util.Pair;

import org.grovecity.drizzlesms.crypto.MasterSecret;
import org.grovecity.drizzlesms.crypto.SecurityEvent;
import org.grovecity.drizzlesms.groups.GroupMessageProcessor;
import org.grovecity.drizzlesms.jobs.requirements.MasterSecretRequirement;
import org.grovecity.drizzlesms.notifications.MessageNotifier;
import org.grovecity.drizzlesms.sms.OutgoingTextMessage;
import org.grovecity.drizzlesms.util.Base64;
import org.grovecity.drizzlesms.ApplicationContext;
import org.grovecity.drizzlesms.crypto.storage.TextSecureAxolotlStore;
import org.grovecity.drizzlesms.crypto.storage.TextSecureSessionStore;
import org.grovecity.drizzlesms.database.DatabaseFactory;
import org.grovecity.drizzlesms.database.EncryptingSmsDatabase;
import org.grovecity.drizzlesms.database.MmsDatabase;
import org.grovecity.drizzlesms.database.NoSuchMessageException;
import org.grovecity.drizzlesms.database.PushDatabase;
import org.grovecity.drizzlesms.mms.IncomingMediaMessage;
import org.grovecity.drizzlesms.mms.OutgoingMediaMessage;
import org.grovecity.drizzlesms.mms.OutgoingSecureMediaMessage;
import org.grovecity.drizzlesms.recipients.RecipientFactory;
import org.grovecity.drizzlesms.recipients.Recipients;
import org.grovecity.drizzlesms.service.KeyCachingService;
import org.grovecity.drizzlesms.sms.IncomingEncryptedMessage;
import org.grovecity.drizzlesms.sms.IncomingEndSessionMessage;
import org.grovecity.drizzlesms.sms.IncomingPreKeyBundleMessage;
import org.grovecity.drizzlesms.sms.IncomingTextMessage;
import org.grovecity.drizzlesms.util.GroupUtil;
import org.grovecity.drizzlesms.util.DrizzleSmsPreferences;
import org.whispersystems.jobqueue.JobParameters;
import org.whispersystems.libaxolotl.DuplicateMessageException;
import org.whispersystems.libaxolotl.IdentityKey;
import org.whispersystems.libaxolotl.InvalidKeyException;
import org.whispersystems.libaxolotl.InvalidKeyIdException;
import org.whispersystems.libaxolotl.InvalidMessageException;
import org.whispersystems.libaxolotl.InvalidVersionException;
import org.whispersystems.libaxolotl.LegacyMessageException;
import org.whispersystems.libaxolotl.NoSessionException;
import org.whispersystems.libaxolotl.UntrustedIdentityException;
import org.whispersystems.libaxolotl.protocol.PreKeyWhisperMessage;
import org.whispersystems.libaxolotl.state.AxolotlStore;
import org.whispersystems.libaxolotl.state.SessionStore;
import org.whispersystems.libaxolotl.util.guava.Optional;
import org.whispersystems.textsecure.api.crypto.TextSecureCipher;
import org.whispersystems.textsecure.api.messages.TextSecureEnvelope;
import org.whispersystems.textsecure.api.messages.TextSecureGroup;
import org.whispersystems.textsecure.api.messages.TextSecureMessage;
import org.whispersystems.textsecure.api.messages.TextSecureSyncContext;
import org.whispersystems.textsecure.api.push.TextSecureAddress;

import java.util.concurrent.TimeUnit;

import ws.com.google.android.mms.MmsException;

public class PushDecryptJob extends MasterSecretJob {

  public static final String TAG = PushDecryptJob.class.getSimpleName();

  private final long messageId;
  private final long smsMessageId;

  public PushDecryptJob(Context context, long pushMessageId, String sender) {
    this(context, pushMessageId, -1, sender);
  }

  public PushDecryptJob(Context context, long pushMessageId, long smsMessageId, String sender) {
    super(context, JobParameters.newBuilder()
                                .withPersistence()
                                .withRequirement(new MasterSecretRequirement(context))
                                .withGroupId(sender)
                                .withWakeLock(true, 5, TimeUnit.SECONDS)
                                .create());
    this.messageId    = pushMessageId;
    this.smsMessageId = smsMessageId;
  }

  @Override
  public void onAdded() {
    if (KeyCachingService.getMasterSecret(context) == null) {
      MessageNotifier.updateNotification(context, null, -2);
    }
  }

  @Override
  public void onRun(MasterSecret masterSecret) throws NoSuchMessageException {
    PushDatabase       database             = DatabaseFactory.getPushDatabase(context);
    TextSecureEnvelope envelope             = database.get(messageId);
    Optional<Long>     optionalSmsMessageId = smsMessageId > 0 ? Optional.of(smsMessageId) :
                                                                 Optional.<Long>absent();

    handleMessage(masterSecret, envelope, optionalSmsMessageId);
    database.delete(messageId);
  }

  @Override
  public boolean onShouldRetryThrowable(Exception exception) {
    return false;
  }

  @Override
  public void onCanceled() {

  }

  private void handleMessage(MasterSecret masterSecret, TextSecureEnvelope envelope, Optional<Long> smsMessageId) {
    try {
      AxolotlStore      axolotlStore = new TextSecureAxolotlStore(context, masterSecret);
      TextSecureAddress localAddress = new TextSecureAddress(DrizzleSmsPreferences.getLocalNumber(context));
      TextSecureCipher  cipher       = new TextSecureCipher(localAddress, axolotlStore);

      TextSecureMessage message = cipher.decrypt(envelope);

      if      (message.isEndSession())               handleEndSessionMessage(masterSecret, envelope, message, smsMessageId);
      else if (message.isGroupUpdate())              handleGroupMessage(masterSecret, envelope, message, smsMessageId);
      else if (message.getAttachments().isPresent()) handleMediaMessage(masterSecret, envelope, message, smsMessageId);
      else                                           handleTextMessage(masterSecret, envelope, message, smsMessageId);

      if (envelope.isPreKeyWhisperMessage()) {
        ApplicationContext.getInstance(context).getJobManager().add(new RefreshPreKeysJob(context));
      }
    } catch (InvalidVersionException e) {
      Log.w(TAG, e);
      handleInvalidVersionMessage(masterSecret, envelope, smsMessageId);
    } catch (InvalidMessageException | InvalidKeyIdException | InvalidKeyException | MmsException e) {
      Log.w(TAG, e);
      handleCorruptMessage(masterSecret, envelope, smsMessageId);
    } catch (NoSessionException e) {
      Log.w(TAG, e);
      handleNoSessionMessage(masterSecret, envelope, smsMessageId);
    } catch (LegacyMessageException e) {
      Log.w(TAG, e);
      handleLegacyMessage(masterSecret, envelope, smsMessageId);
    } catch (DuplicateMessageException e) {
      Log.w(TAG, e);
      handleDuplicateMessage(masterSecret, envelope, smsMessageId);
    } catch (UntrustedIdentityException e) {
      Log.w(TAG, e);
      handleUntrustedIdentityMessage(masterSecret, envelope, smsMessageId);
    }
  }

  private void handleEndSessionMessage(MasterSecret masterSecret, TextSecureEnvelope envelope,
                                       TextSecureMessage message, Optional<Long> smsMessageId)
  {
    EncryptingSmsDatabase smsDatabase         = DatabaseFactory.getEncryptingSmsDatabase(context);
    IncomingTextMessage   incomingTextMessage = new IncomingTextMessage(envelope.getSource(),
                                                                        envelope.getSourceDevice(),
                                                                        message.getTimestamp(),
                                                                        "", Optional.<TextSecureGroup>absent());

    long threadId;

    if (!smsMessageId.isPresent()) {
      IncomingEndSessionMessage incomingEndSessionMessage = new IncomingEndSessionMessage(incomingTextMessage);
      Pair<Long, Long>          messageAndThreadId        = smsDatabase.insertMessageInbox(masterSecret, incomingEndSessionMessage);
      threadId = messageAndThreadId.second;
    } else {
      smsDatabase.markAsEndSession(smsMessageId.get());
      threadId = smsDatabase.getThreadIdForMessage(smsMessageId.get());
    }

    SessionStore sessionStore = new TextSecureSessionStore(context, masterSecret);
    sessionStore.deleteAllSessions(envelope.getSource());

    SecurityEvent.broadcastSecurityUpdateEvent(context, threadId);
    MessageNotifier.updateNotification(context, masterSecret, threadId);
  }

  private void handleGroupMessage(MasterSecret masterSecret, TextSecureEnvelope envelope, TextSecureMessage message, Optional<Long> smsMessageId) {
    GroupMessageProcessor.process(context, masterSecret, envelope, message);

    if (smsMessageId.isPresent()) {
      DatabaseFactory.getSmsDatabase(context).deleteMessage(smsMessageId.get());
    }
  }

  private void handleMediaMessage(MasterSecret masterSecret, TextSecureEnvelope envelope,
                                  TextSecureMessage message, Optional<Long> smsMessageId)
      throws MmsException
  {
    Pair<Long, Long> messageAndThreadId;

    if (message.getSyncContext().isPresent()) {
      messageAndThreadId = insertSyncMediaMessage(masterSecret, envelope, message);
    } else {
      messageAndThreadId = insertStandardMediaMessage(masterSecret, envelope, message);
    }

    ApplicationContext.getInstance(context)
                      .getJobManager()
                      .add(new AttachmentDownloadJob(context, messageAndThreadId.first));

    if (smsMessageId.isPresent()) {
      DatabaseFactory.getSmsDatabase(context).deleteMessage(smsMessageId.get());
    }

    MessageNotifier.updateNotification(context, masterSecret, messageAndThreadId.second);
  }

  private void handleTextMessage(MasterSecret masterSecret, TextSecureEnvelope envelope,
                                 TextSecureMessage message, Optional<Long> smsMessageId)
  {
    Pair<Long, Long> messageAndThreadId;

    if (message.getSyncContext().isPresent()) {
      messageAndThreadId = insertSyncTextMessage(masterSecret, envelope, message, smsMessageId);
    } else {
      messageAndThreadId = insertStandardTextMessage(masterSecret, envelope, message, smsMessageId);
    }

    MessageNotifier.updateNotification(context, masterSecret, messageAndThreadId.second);
  }

  private void handleInvalidVersionMessage(MasterSecret masterSecret, TextSecureEnvelope envelope, Optional<Long> smsMessageId) {
    EncryptingSmsDatabase smsDatabase = DatabaseFactory.getEncryptingSmsDatabase(context);

    if (!smsMessageId.isPresent()) {
      Pair<Long, Long> messageAndThreadId = insertPlaceholder(masterSecret, envelope);
      smsDatabase.markAsInvalidVersionKeyExchange(messageAndThreadId.first);
      MessageNotifier.updateNotification(context, masterSecret, messageAndThreadId.second);
    } else {
      smsDatabase.markAsInvalidVersionKeyExchange(smsMessageId.get());
    }
  }

  private void handleCorruptMessage(MasterSecret masterSecret, TextSecureEnvelope envelope, Optional<Long> smsMessageId) {
    EncryptingSmsDatabase smsDatabase = DatabaseFactory.getEncryptingSmsDatabase(context);

    if (!smsMessageId.isPresent()) {
      Pair<Long, Long> messageAndThreadId = insertPlaceholder(masterSecret, envelope);
      smsDatabase.markAsDecryptFailed(messageAndThreadId.first);
      MessageNotifier.updateNotification(context, masterSecret, messageAndThreadId.second);
    } else {
      smsDatabase.markAsDecryptFailed(smsMessageId.get());
    }
  }

  private void handleNoSessionMessage(MasterSecret masterSecret, TextSecureEnvelope envelope, Optional<Long> smsMessageId) {
    EncryptingSmsDatabase smsDatabase = DatabaseFactory.getEncryptingSmsDatabase(context);

    if (!smsMessageId.isPresent()) {
      Pair<Long, Long> messageAndThreadId = insertPlaceholder(masterSecret, envelope);
      smsDatabase.markAsNoSession(messageAndThreadId.first);
      MessageNotifier.updateNotification(context, masterSecret, messageAndThreadId.second);
    } else {
      smsDatabase.markAsNoSession(smsMessageId.get());
    }
  }

  private void handleLegacyMessage(MasterSecret masterSecret, TextSecureEnvelope envelope, Optional<Long> smsMessageId) {
    EncryptingSmsDatabase smsDatabase = DatabaseFactory.getEncryptingSmsDatabase(context);

    if (!smsMessageId.isPresent()) {
      Pair<Long, Long> messageAndThreadId = insertPlaceholder(masterSecret, envelope);
      smsDatabase.markAsLegacyVersion(messageAndThreadId.first);
      MessageNotifier.updateNotification(context, masterSecret, messageAndThreadId.second);
    } else {
      smsDatabase.markAsLegacyVersion(smsMessageId.get());
    }
  }

  private void handleDuplicateMessage(MasterSecret masterSecret, TextSecureEnvelope envelope, Optional<Long> smsMessageId) {
    // Let's start ignoring these now
//    SmsDatabase smsDatabase = DatabaseFactory.getEncryptingSmsDatabase(context);
//
//    if (smsMessageId <= 0) {
//      Pair<Long, Long> messageAndThreadId = insertPlaceholder(masterSecret, envelope);
//      smsDatabase.markAsDecryptDuplicate(messageAndThreadId.first);
//      MessageNotifier.updateNotification(context, masterSecret, messageAndThreadId.second);
//    } else {
//      smsDatabase.markAsDecryptDuplicate(smsMessageId);
//    }
  }

  private void handleUntrustedIdentityMessage(MasterSecret masterSecret, TextSecureEnvelope envelope, Optional<Long> smsMessageId) {
    try {
      EncryptingSmsDatabase database       = DatabaseFactory.getEncryptingSmsDatabase(context);
      Recipients            recipients     = RecipientFactory.getRecipientsFromString(context, envelope.getSource(), false);
      long                  recipientId    = recipients.getPrimaryRecipient().getRecipientId();
      PreKeyWhisperMessage  whisperMessage = new PreKeyWhisperMessage(envelope.getMessage());
      IdentityKey           identityKey    = whisperMessage.getIdentityKey();
      String                encoded        = Base64.encodeBytes(envelope.getMessage());
      IncomingTextMessage   textMessage    = new IncomingTextMessage(envelope.getSource(), envelope.getSourceDevice(),
                                                                     envelope.getTimestamp(), encoded,
                                                                     Optional.<TextSecureGroup>absent());

      if (!smsMessageId.isPresent()) {
        IncomingPreKeyBundleMessage bundleMessage      = new IncomingPreKeyBundleMessage(textMessage, encoded);
        Pair<Long, Long>            messageAndThreadId = database.insertMessageInbox(masterSecret, bundleMessage);

        database.addMismatchedIdentity(messageAndThreadId.first, recipientId, identityKey);
        MessageNotifier.updateNotification(context, masterSecret, messageAndThreadId.second);
      } else {
        database.updateMessageBody(masterSecret, smsMessageId.get(), encoded);
        database.markAsPreKeyBundle(smsMessageId.get());
        database.addMismatchedIdentity(smsMessageId.get(), recipientId, identityKey);
      }
    } catch (InvalidMessageException | InvalidVersionException e) {
      throw new AssertionError(e);
    }
  }

  private Pair<Long, Long> insertPlaceholder(MasterSecret masterSecret, TextSecureEnvelope envelope) {
    EncryptingSmsDatabase database = DatabaseFactory.getEncryptingSmsDatabase(context);

    IncomingTextMessage textMessage = new IncomingTextMessage(envelope.getSource(), envelope.getSourceDevice(),
                                                              envelope.getTimestamp(), "",
                                                              Optional.<TextSecureGroup>absent());

    textMessage = new IncomingEncryptedMessage(textMessage, "");

    return database.insertMessageInbox(masterSecret, textMessage);
  }

  private Pair<Long, Long> insertSyncTextMessage(MasterSecret masterSecret,
                                                 TextSecureEnvelope envelope,
                                                 TextSecureMessage message,
                                                 Optional<Long> smsMessageId)
  {
    EncryptingSmsDatabase database            = DatabaseFactory.getEncryptingSmsDatabase(context);
    Recipients            recipients          = getSyncMessageDestination(message);
    String                body                = message.getBody().or("");
    OutgoingTextMessage outgoingTextMessage = new OutgoingTextMessage(recipients, body);

    long threadId  = DatabaseFactory.getThreadDatabase(context).getThreadIdFor(recipients);
    long messageId = database.insertMessageOutbox(masterSecret, threadId, outgoingTextMessage, false, message.getSyncContext().get().getTimestamp());

    database.markAsSent(messageId);
    database.markAsPush(messageId);
    database.markAsSecure(messageId);

    if (smsMessageId.isPresent()) {
      database.deleteMessage(smsMessageId.get());
    }

    return new Pair<>(messageId, threadId);
  }

  private Pair<Long, Long> insertStandardTextMessage(MasterSecret masterSecret,
                                                     TextSecureEnvelope envelope,
                                                     TextSecureMessage message,
                                                     Optional<Long> smsMessageId)
  {
    EncryptingSmsDatabase database = DatabaseFactory.getEncryptingSmsDatabase(context);
    String                body     = message.getBody().isPresent() ? message.getBody().get() : "";

    if (smsMessageId.isPresent()) {
      return database.updateBundleMessageBody(masterSecret, smsMessageId.get(), body);
    } else {
      IncomingTextMessage textMessage = new IncomingTextMessage(envelope.getSource(),
                                                                envelope.getSourceDevice(),
                                                                message.getTimestamp(), body,
                                                                message.getGroupInfo());

      textMessage = new IncomingEncryptedMessage(textMessage, body);

      return database.insertMessageInbox(masterSecret, textMessage);
    }
  }

  private Pair<Long, Long> insertSyncMediaMessage(MasterSecret masterSecret,
                                                  TextSecureEnvelope envelope,
                                                  TextSecureMessage message)
      throws MmsException
  {
    MmsDatabase           database     = DatabaseFactory.getMmsDatabase(context);
    TextSecureSyncContext syncContext  = message.getSyncContext().get();
    Recipients            recipients   = getSyncMessageDestination(message);
    OutgoingMediaMessage  mediaMessage = new OutgoingMediaMessage(context, masterSecret, recipients,
                                                                  message.getAttachments().get(),
                                                                  message.getBody().orNull());

    mediaMessage = new OutgoingSecureMediaMessage(mediaMessage);

    long threadId  = DatabaseFactory.getThreadDatabase(context).getThreadIdFor(recipients);
    long messageId = database.insertMessageOutbox(masterSecret, mediaMessage, threadId, false, syncContext.getTimestamp());

    database.markAsSent(messageId, "push".getBytes(), 0);
    database.markAsPush(messageId);

    return new Pair<>(messageId, threadId);
  }

  private Pair<Long, Long> insertStandardMediaMessage(MasterSecret masterSecret,
                                                      TextSecureEnvelope envelope,
                                                      TextSecureMessage message)
      throws MmsException
  {
    MmsDatabase          database     = DatabaseFactory.getMmsDatabase(context);
    String               localNumber  = DrizzleSmsPreferences.getLocalNumber(context);
    IncomingMediaMessage mediaMessage = new IncomingMediaMessage(masterSecret, envelope.getSource(),
                                                                 localNumber, message.getTimestamp(),
                                                                 Optional.fromNullable(envelope.getRelay()),
                                                                 message.getBody(),
                                                                 message.getGroupInfo(),
                                                                 message.getAttachments());

    return database.insertSecureDecryptedMessageInbox(masterSecret, mediaMessage, -1);
  }

  private Recipients getSyncMessageDestination(TextSecureMessage message) {
    if (message.getGroupInfo().isPresent()) {
      return RecipientFactory.getRecipientsFromString(context, GroupUtil.getEncodedId(message.getGroupInfo().get().getGroupId()), false);
    } else {
      return RecipientFactory.getRecipientsFromString(context, message.getSyncContext().get().getDestination(), false);
    }
  }
}