package org.grovecity.drizzlesms.push;

import android.content.Context;

import org.grovecity.drizzlesms.crypto.SecurityEvent;
import org.grovecity.drizzlesms.database.DatabaseFactory;
import org.grovecity.drizzlesms.recipients.RecipientFactory;
import org.grovecity.drizzlesms.recipients.Recipients;
import org.whispersystems.textsecure.api.TextSecureMessageSender;
import org.whispersystems.textsecure.api.push.TextSecureAddress;

public class SecurityEventListener implements TextSecureMessageSender.EventListener {

  private static final String TAG = SecurityEventListener.class.getSimpleName();

  private final Context context;

  public SecurityEventListener(Context context) {
    this.context = context.getApplicationContext();
  }

  @Override
  public void onSecurityEvent(TextSecureAddress textSecureAddress) {
    Recipients recipients = RecipientFactory.getRecipientsFromString(context, textSecureAddress.getNumber(), false);
    long       threadId   = DatabaseFactory.getThreadDatabase(context).getThreadIdFor(recipients);

    SecurityEvent.broadcastSecurityUpdateEvent(context, threadId);
  }
}
