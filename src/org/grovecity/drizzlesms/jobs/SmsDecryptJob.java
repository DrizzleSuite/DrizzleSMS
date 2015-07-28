package org.grovecity.drizzlesms.jobs;

import android.content.Context;
import android.util.Log;

import org.grovecity.drizzlesms.crypto.AsymmetricMasterSecret;
import org.grovecity.drizzlesms.crypto.MasterSecret;
import org.grovecity.drizzlesms.database.DatabaseFactory;
import org.grovecity.drizzlesms.jobs.requirements.MasterSecretRequirement;
import org.grovecity.drizzlesms.notifications.MessageNotifier;
import org.grovecity.drizzlesms.sms.IncomingEncryptedMessage;
import org.grovecity.drizzlesms.crypto.AsymmetricMasterCipher;
import org.grovecity.drizzlesms.crypto.MasterSecretUtil;
import org.grovecity.drizzlesms.database.EncryptingSmsDatabase;
import org.grovecity.drizzlesms.database.NoSuchMessageException;
import org.grovecity.drizzlesms.database.model.SmsMessageRecord;
import org.grovecity.drizzlesms.sms.IncomingTextMessage;
import org.whispersystems.jobqueue.JobParameters;
import org.whispersystems.libaxolotl.InvalidMessageException;
import org.whispersystems.libaxolotl.util.guava.Optional;
import org.whispersystems.textsecure.api.messages.TextSecureGroup;

import java.io.IOException;

public class SmsDecryptJob extends MasterSecretJob {

  private static final String TAG = SmsDecryptJob.class.getSimpleName();

  private final long messageId;

  public SmsDecryptJob(Context context, long messageId) {
    super(context, JobParameters.newBuilder()
                                .withPersistence()
                                .withRequirement(new MasterSecretRequirement(context))
                                .create());

    this.messageId = messageId;
  }

  @Override
  public void onAdded() {}

  @Override
  public void onRun(MasterSecret masterSecret) throws NoSuchMessageException {
    EncryptingSmsDatabase database = DatabaseFactory.getEncryptingSmsDatabase(context);

    try {
      SmsMessageRecord    record    = database.getMessage(masterSecret, messageId);
      IncomingTextMessage message   = createIncomingTextMessage(masterSecret, record);
      long                messageId = record.getId();

      if (message.isSecureMessage()) {
        database.markAsLegacyVersion(messageId);
      } else {
        database.updateMessageBody(masterSecret, messageId, message.getMessageBody());
      }

      MessageNotifier.updateNotification(context, masterSecret);
    } catch (InvalidMessageException e) {
      Log.w(TAG, e);
      database.markAsDecryptFailed(messageId);
    }
  }

  @Override
  public boolean onShouldRetryThrowable(Exception exception) {
    return false;
  }

  @Override
  public void onCanceled() {
    // TODO
  }

  private String getAsymmetricDecryptedBody(MasterSecret masterSecret, String body)
      throws InvalidMessageException
  {
    try {
      AsymmetricMasterSecret asymmetricMasterSecret = MasterSecretUtil.getAsymmetricMasterSecret(context, masterSecret);
      AsymmetricMasterCipher asymmetricMasterCipher = new AsymmetricMasterCipher(asymmetricMasterSecret);

      return asymmetricMasterCipher.decryptBody(body);
    } catch (IOException e) {
      throw new InvalidMessageException(e);
    }
  }

  private IncomingTextMessage createIncomingTextMessage(MasterSecret masterSecret, SmsMessageRecord record)
      throws InvalidMessageException
  {
    IncomingTextMessage message = new IncomingTextMessage(record.getRecipients().getPrimaryRecipient().getNumber(),
                                                          record.getRecipientDeviceId(),
                                                          record.getDateSent(),
                                                          record.getBody().getBody(),
                                                          Optional.<TextSecureGroup>absent());

    if (record.isAsymmetricEncryption()) {
      String plaintextBody = getAsymmetricDecryptedBody(masterSecret, record.getBody().getBody());
      return new IncomingTextMessage(message, plaintextBody);
    } else {
      return new IncomingEncryptedMessage(message, message.getMessageBody());
    }
  }
}
