package org.grovecity.drizzlesms.dependencies;

import android.content.Context;

import org.grovecity.drizzlesms.crypto.MasterSecret;
import org.grovecity.drizzlesms.jobs.AttachmentDownloadJob;
import org.grovecity.drizzlesms.push.SecurityEventListener;
import org.grovecity.drizzlesms.push.TextSecurePushTrustStore;
import org.grovecity.drizzlesms.service.MessageRetrievalService;
import org.grovecity.drizzlesms.Release;
import org.grovecity.drizzlesms.crypto.storage.TextSecureAxolotlStore;
import org.grovecity.drizzlesms.jobs.CleanPreKeysJob;
import org.grovecity.drizzlesms.jobs.CreateSignedPreKeyJob;
import org.grovecity.drizzlesms.jobs.DeliveryReceiptJob;
import org.grovecity.drizzlesms.jobs.PushGroupSendJob;
import org.grovecity.drizzlesms.jobs.PushMediaSendJob;
import org.grovecity.drizzlesms.jobs.PushNotificationReceiveJob;
import org.grovecity.drizzlesms.jobs.PushTextSendJob;
import org.grovecity.drizzlesms.jobs.RefreshPreKeysJob;
import org.grovecity.drizzlesms.util.DrizzleSmsPreferences;
import org.whispersystems.libaxolotl.util.guava.Optional;
import org.whispersystems.textsecure.api.TextSecureAccountManager;
import org.whispersystems.textsecure.api.TextSecureMessageReceiver;
import org.whispersystems.textsecure.api.TextSecureMessageSender;
import org.whispersystems.textsecure.api.util.CredentialsProvider;

import dagger.Module;
import dagger.Provides;

@Module(complete = false, injects = {CleanPreKeysJob.class,
                                     CreateSignedPreKeyJob.class,
                                     DeliveryReceiptJob.class,
                                     PushGroupSendJob.class,
                                     PushTextSendJob.class,
                                     PushMediaSendJob.class,
                                     AttachmentDownloadJob.class,
                                     RefreshPreKeysJob.class,
                                     MessageRetrievalService.class,
                                     PushNotificationReceiveJob.class})
public class TextSecureCommunicationModule {

  private final Context context;

  public TextSecureCommunicationModule(Context context) {
    this.context = context;
  }

  @Provides TextSecureAccountManager provideTextSecureAccountManager() {
    return new TextSecureAccountManager(Release.PUSH_URL,
                                        new TextSecurePushTrustStore(context),
                                        DrizzleSmsPreferences.getLocalNumber(context),
                                        DrizzleSmsPreferences.getPushServerPassword(context));
  }

  @Provides TextSecureMessageSenderFactory provideTextSecureMessageSenderFactory() {
    return new TextSecureMessageSenderFactory() {
      @Override
      public TextSecureMessageSender create(MasterSecret masterSecret) {
        return new TextSecureMessageSender(Release.PUSH_URL,
                                           new TextSecurePushTrustStore(context),
                                           DrizzleSmsPreferences.getLocalNumber(context),
                                           DrizzleSmsPreferences.getPushServerPassword(context),
                                           new TextSecureAxolotlStore(context, masterSecret),
                                           Optional.of((TextSecureMessageSender.EventListener)
                                                           new SecurityEventListener(context)));
      }
    };
  }

  @Provides TextSecureMessageReceiver provideTextSecureMessageReceiver() {
    return new TextSecureMessageReceiver(Release.PUSH_URL,
                                         new TextSecurePushTrustStore(context),
                                         new DynamicCredentialsProvider(context));
  }

  public static interface TextSecureMessageSenderFactory {
    public TextSecureMessageSender create(MasterSecret masterSecret);
  }

  private static class DynamicCredentialsProvider implements CredentialsProvider {

    private final Context context;

    private DynamicCredentialsProvider(Context context) {
      this.context = context.getApplicationContext();
    }

    @Override
    public String getUser() {
      return DrizzleSmsPreferences.getLocalNumber(context);
    }

    @Override
    public String getPassword() {
      return DrizzleSmsPreferences.getPushServerPassword(context);
    }

    @Override
    public String getSignalingKey() {
      return DrizzleSmsPreferences.getSignalingKey(context);
    }
  }

}
