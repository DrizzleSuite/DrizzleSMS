package org.grovecity.drizzlesms.mms;

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import org.grovecity.drizzlesms.transport.UndeliverableMessageException;

import ws.com.google.android.mms.pdu.SendConf;

public interface OutgoingMmsConnection {
  @Nullable SendConf send(@NonNull byte[] pduBytes) throws UndeliverableMessageException;
}
