package org.grovecity.drizzlesms.push;

import android.content.Context;

import org.grovecity.drizzlesms.util.DrizzleSmsPreferences;
import org.grovecity.drizzlesms.Release;
import org.whispersystems.textsecure.api.TextSecureAccountManager;

public class TextSecureCommunicationFactory {

  public static TextSecureAccountManager createManager(Context context) {
    return new TextSecureAccountManager(Release.PUSH_URL,
                                        new TextSecurePushTrustStore(context),
                                        DrizzleSmsPreferences.getLocalNumber(context),
                                        DrizzleSmsPreferences.getPushServerPassword(context));
  }

  public static TextSecureAccountManager createManager(Context context, String number, String password) {
    return new TextSecureAccountManager(Release.PUSH_URL, new TextSecurePushTrustStore(context),
                                        number, password);
  }

}
