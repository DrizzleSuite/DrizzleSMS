package org.grovecity.drizzlesms.tutorial;

import android.content.Context;
import android.content.SharedPreferences;

public class SharedPreferencesBuilder {

   private static final String DrizzleSmsSharedPreferences = "DrizzleSmsPref";
   private static SharedPreferences mSharedPreferences;


   public static SharedPreferences getSharedPreferences(Context var0) {
      if(mSharedPreferences == null) {
         mSharedPreferences = var0.getSharedPreferences("DrizzleSmsPref", 0);
      }

      return mSharedPreferences;
   }
}

