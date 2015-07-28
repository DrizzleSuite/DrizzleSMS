package org.grovecity.drizzlesms.jobs;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.util.Log;

import org.grovecity.drizzlesms.crypto.MasterSecret;
import org.grovecity.drizzlesms.database.DatabaseFactory;
import org.grovecity.drizzlesms.database.GroupDatabase;
import org.grovecity.drizzlesms.jobs.requirements.MasterSecretRequirement;
import org.grovecity.drizzlesms.push.TextSecurePushTrustStore;
import org.grovecity.drizzlesms.recipients.Recipient;
import org.grovecity.drizzlesms.util.BitmapDecodingException;
import org.grovecity.drizzlesms.util.BitmapUtil;
import org.grovecity.drizzlesms.util.GroupUtil;
import org.grovecity.drizzlesms.util.DrizzleSmsPreferences;
import org.grovecity.drizzlesms.Release;
import org.grovecity.drizzlesms.recipients.RecipientFactory;
import org.whispersystems.jobqueue.JobParameters;
import org.whispersystems.jobqueue.requirements.NetworkRequirement;
import org.whispersystems.libaxolotl.InvalidMessageException;
import org.whispersystems.textsecure.api.crypto.AttachmentCipherInputStream;
import org.whispersystems.textsecure.internal.push.PushServiceSocket;
import org.whispersystems.textsecure.api.push.exceptions.NonSuccessfulResponseCodeException;
import org.whispersystems.textsecure.internal.util.StaticCredentialsProvider;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;

public class AvatarDownloadJob extends MasterSecretJob {

  private static final String TAG = AvatarDownloadJob.class.getSimpleName();

  private final byte[] groupId;

  public AvatarDownloadJob(Context context, byte[] groupId) {
    super(context, JobParameters.newBuilder()
                                .withRequirement(new MasterSecretRequirement(context))
                                .withRequirement(new NetworkRequirement(context))
                                .withPersistence()
                                .create());

    this.groupId = groupId;
  }

  @Override
  public void onAdded() {}

  @Override
  public void onRun(MasterSecret masterSecret) throws IOException {
    GroupDatabase database   = DatabaseFactory.getGroupDatabase(context);
    GroupDatabase.GroupRecord record     = database.getGroup(groupId);
    File                      attachment = null;

    try {
      if (record != null) {
        long   avatarId = record.getAvatarId();
        byte[] key      = record.getAvatarKey();
        String relay    = record.getRelay();

        if (avatarId == -1 || key == null) {
          return;
        }

        attachment = downloadAttachment(relay, avatarId);

        InputStream scaleInputStream   = new AttachmentCipherInputStream(attachment, key);
        InputStream measureInputStream = new AttachmentCipherInputStream(attachment, key);
        Bitmap      avatar             = BitmapUtil.createScaledBitmap(measureInputStream, scaleInputStream, 500, 500);

        database.updateAvatar(groupId, avatar);

        Recipient groupRecipient = RecipientFactory.getRecipientsFromString(context, GroupUtil.getEncodedId(groupId), true)
                                                   .getPrimaryRecipient();
        groupRecipient.setContactPhoto(new BitmapDrawable(avatar));
      }
    } catch (InvalidMessageException | BitmapDecodingException | NonSuccessfulResponseCodeException e) {
      Log.w(TAG, e);
    } finally {
      if (attachment != null)
        attachment.delete();
    }
  }

  @Override
  public void onCanceled() {}

  @Override
  public boolean onShouldRetryThrowable(Exception exception) {
    if (exception instanceof IOException) return true;
    return false;
  }

  private File downloadAttachment(String relay, long contentLocation) throws IOException {
    PushServiceSocket socket = new PushServiceSocket(Release.PUSH_URL,
                                                     new TextSecurePushTrustStore(context),
                                                     new StaticCredentialsProvider(DrizzleSmsPreferences.getLocalNumber(context),
                                                                                   DrizzleSmsPreferences.getPushServerPassword(context),
                                                                                   null));

    File destination = File.createTempFile("avatar", "tmp");

    destination.deleteOnExit();

    socket.retrieveAttachment(relay, contentLocation, destination);

    return destination;
  }

}
