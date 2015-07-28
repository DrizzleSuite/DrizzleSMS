package org.grovecity.drizzlesms.crypto.storage;

import android.content.Context;

import org.grovecity.drizzlesms.crypto.IdentityKeyUtil;
import org.grovecity.drizzlesms.crypto.MasterSecret;
import org.grovecity.drizzlesms.database.DatabaseFactory;
import org.grovecity.drizzlesms.util.DrizzleSmsPreferences;
import org.grovecity.drizzlesms.recipients.RecipientFactory;
import org.whispersystems.libaxolotl.IdentityKey;
import org.whispersystems.libaxolotl.IdentityKeyPair;
import org.whispersystems.libaxolotl.state.IdentityKeyStore;

public class TextSecureIdentityKeyStore implements IdentityKeyStore {

  private final Context      context;
  private final MasterSecret masterSecret;

  public TextSecureIdentityKeyStore(Context context, MasterSecret masterSecret) {
    this.context      = context;
    this.masterSecret = masterSecret;
  }

  @Override
  public IdentityKeyPair getIdentityKeyPair() {
    return IdentityKeyUtil.getIdentityKeyPair(context, masterSecret);
  }

  @Override
  public int getLocalRegistrationId() {
    return DrizzleSmsPreferences.getLocalRegistrationId(context);
  }

  @Override
  public void saveIdentity(String name, IdentityKey identityKey) {
    long recipientId = RecipientFactory.getRecipientsFromString(context, name, true).getPrimaryRecipient().getRecipientId();
    DatabaseFactory.getIdentityDatabase(context).saveIdentity(masterSecret, recipientId, identityKey);
  }

  @Override
  public boolean isTrustedIdentity(String name, IdentityKey identityKey) {
    long recipientId = RecipientFactory.getRecipientsFromString(context, name, true).getPrimaryRecipient().getRecipientId();
    return DatabaseFactory.getIdentityDatabase(context)
                          .isValidIdentity(masterSecret, recipientId, identityKey);
  }
}
