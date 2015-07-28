/*
 * Copyright (C) 2013 Open Whisper Systems
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

import android.app.Application;
import android.content.Context;
import android.support.multidex.MultiDex;
import android.util.Log;

import com.parse.FunctionCallback;
import com.parse.Parse;
import com.parse.ParseACL;
import com.parse.ParseCloud;
import com.parse.ParseException;
import com.parse.ParseInstallation;
import com.parse.ParsePush;
import com.parse.ParseTwitterUtils;
import com.parse.ParseUser;
import com.parse.SaveCallback;

import org.grovecity.drizzlesms.crypto.PRNGFixes;
import org.grovecity.drizzlesms.dependencies.AxolotlStorageModule;
import org.grovecity.drizzlesms.dependencies.InjectableType;
import org.grovecity.drizzlesms.dependencies.TextSecureCommunicationModule;
import org.grovecity.drizzlesms.util.DrizzleSmsPreferences;
import org.grovecity.drizzlesms.R;
import org.grovecity.drizzlesms.jobs.GcmRefreshJob;
import org.grovecity.drizzlesms.jobs.persistence.EncryptingJobSerializer;
import org.grovecity.drizzlesms.jobs.requirements.MasterSecretRequirementProvider;
import org.grovecity.drizzlesms.jobs.requirements.ServiceRequirementProvider;
import org.whispersystems.jobqueue.JobManager;
import org.whispersystems.jobqueue.dependencies.DependencyInjector;
import org.whispersystems.jobqueue.requirements.NetworkRequirementProvider;
import org.whispersystems.libaxolotl.logging.AxolotlLoggerProvider;
import org.whispersystems.libaxolotl.util.AndroidAxolotlLogger;

import java.util.HashMap;

import dagger.ObjectGraph;

/**
 * Will be called once when the TextSecure process is created.
 *
 * We're using this as an insertion point to patch up the Android PRNG disaster,
 * to initialize the job manager, and to check for GCM registration freshness.
 *
 * @author Moxie Marlinspike
 */
public class ApplicationContext extends Application implements DependencyInjector {

  private JobManager jobManager;
  private ObjectGraph objectGraph;

  public static ApplicationContext getInstance(Context context) {
    return (ApplicationContext)context.getApplicationContext();
  }

  @Override
  public void onCreate() {
    initializeRandomNumberFix();
    initializeLogging();
    initializeDependencyInjection();
    initializeJobManager();
    initializeGcmCheck();
    initializeParse();
  }

  @Override
  public void injectDependencies(Object object) {
    if (object instanceof InjectableType) {
      objectGraph.inject(object);
    }
  }

    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(base);
        MultiDex.install(this);
    }

    public JobManager getJobManager() {
    return jobManager;
  }

  private void initializeRandomNumberFix() {
    PRNGFixes.apply();
  }

  private void initializeLogging() {
    AxolotlLoggerProvider.setProvider(new AndroidAxolotlLogger());
  }

  private void initializeJobManager() {
    this.jobManager = JobManager.newBuilder(this)
                                .withName("DrizzleSMSJobs")
                                .withDependencyInjector(this)
                                .withJobSerializer(new EncryptingJobSerializer())
                                .withRequirementProviders(new MasterSecretRequirementProvider(this),
                                                          new ServiceRequirementProvider(this),
                                                          new NetworkRequirementProvider(this))
                                .withConsumerThreads(5)
                                .build();
  }

  private void initializeDependencyInjection() {
    this.objectGraph = ObjectGraph.create(new TextSecureCommunicationModule(this),
                                          new AxolotlStorageModule(this));
  }

  private void initializeGcmCheck() {
    if (DrizzleSmsPreferences.isPushRegistered(this) &&
        DrizzleSmsPreferences.getGcmRegistrationId(this) == null)
    {
      this.jobManager.add(new GcmRefreshJob(this));
    }
  }

    private void initializeParse() {
       /* Parse.initialize(this, getString(R.string.parse_app_id), getString(R.string.parse_client_key));
        ParseACL defaultACL = new ParseACL();
        defaultACL.setPublicReadAccess(true);
        ParseACL.setDefaultACL(defaultACL, true);

        ParseCloud.callFunctionInBackground("Parse.User", new HashMap<String, Object>(), new FunctionCallback<String>() {
            public void done(String result, ParseException e) {
                if (e == null) {
                    // result is "Hello world!"
                }
            }
        });

        ParsePush.subscribeInBackground("", new SaveCallback() {
            @Override
            public void done(ParseException e) {
                if (e == null) {
                    Log.d("com.parse.push", "successfully subscribed to the broadcast channel.");
                } else {
                    Log.e("com.parse.push", "failed to subscribe for push", e);
                }
            }
        });*/

        // Required - Initialize the Parse SDK
        Parse.initialize(this, getString(R.string.parse_app_id),
                getString(R.string.parse_client_key));

        ParseInstallation.getCurrentInstallation().saveInBackground();


        ParseCloud.callFunctionInBackground("Parse.User", new HashMap<String, Object>(), new FunctionCallback<String>() {
            public void done(String result, ParseException e) {
                if (e == null) {
                    // result is "Hello world!"
                }
            }
        });

        ParsePush.subscribeInBackground("", new SaveCallback() {
            @Override
            public void done(ParseException e) {
                if (e == null) {
                    Log.d("com.parse.push", "successfully subscribed to the broadcast channel.");
                } else {
                    Log.e("com.parse.push", "failed to subscribe for push", e);
                }
            }
        });

    }
}
