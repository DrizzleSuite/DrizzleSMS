package org.grovecity.drizzlesms.dependencies;

import android.content.Context;

import org.grovecity.drizzlesms.crypto.MasterSecret;
import org.grovecity.drizzlesms.crypto.storage.TextSecureAxolotlStore;
import org.grovecity.drizzlesms.jobs.CleanPreKeysJob;
import org.whispersystems.libaxolotl.state.SignedPreKeyStore;

import dagger.Module;
import dagger.Provides;

@Module (complete = false, injects = {CleanPreKeysJob.class})
public class AxolotlStorageModule {

  private final Context context;

  public AxolotlStorageModule(Context context) {
    this.context = context;
  }

  @Provides SignedPreKeyStoreFactory provideSignedPreKeyStoreFactory() {
    return new SignedPreKeyStoreFactory() {
      @Override
      public SignedPreKeyStore create(MasterSecret masterSecret) {
        return new TextSecureAxolotlStore(context, masterSecret);
      }
    };
  }

  public static interface SignedPreKeyStoreFactory {
    public SignedPreKeyStore create(MasterSecret masterSecret);
  }
}
