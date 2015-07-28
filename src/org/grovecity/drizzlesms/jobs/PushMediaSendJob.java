package org.grovecity.drizzlesms.jobs;

import android.content.Context;
import android.util.Log;

import org.grovecity.drizzlesms.crypto.MasterSecret;
import org.grovecity.drizzlesms.database.DatabaseFactory;
import org.grovecity.drizzlesms.dependencies.TextSecureCommunicationModule;
import org.grovecity.drizzlesms.mms.PartParser;
import org.grovecity.drizzlesms.ApplicationContext;
import org.grovecity.drizzlesms.database.MmsDatabase;
import org.grovecity.drizzlesms.database.NoSuchMessageException;
import org.grovecity.drizzlesms.dependencies.InjectableType;
import org.grovecity.drizzlesms.mms.MediaConstraints;
import org.grovecity.drizzlesms.recipients.RecipientFactory;
import org.grovecity.drizzlesms.recipients.Recipients;
import org.grovecity.drizzlesms.transport.InsecureFallbackApprovalException;
import org.grovecity.drizzlesms.transport.RetryLaterException;
import org.grovecity.drizzlesms.transport.UndeliverableMessageException;
import org.whispersystems.textsecure.api.TextSecureMessageSender;
import org.whispersystems.textsecure.api.crypto.UntrustedIdentityException;
import org.whispersystems.textsecure.api.messages.TextSecureAttachment;
import org.whispersystems.textsecure.api.messages.TextSecureMessage;
import org.whispersystems.textsecure.api.push.TextSecureAddress;
import org.whispersystems.textsecure.api.push.exceptions.UnregisteredUserException;
import org.whispersystems.textsecure.api.util.InvalidNumberException;

import java.io.IOException;
import java.util.List;

import javax.inject.Inject;

import ws.com.google.android.mms.MmsException;
import ws.com.google.android.mms.pdu.SendReq;

public class PushMediaSendJob extends PushSendJob implements InjectableType {

  private static final String TAG = PushMediaSendJob.class.getSimpleName();

  @Inject transient TextSecureCommunicationModule.TextSecureMessageSenderFactory messageSenderFactory;

  private final long messageId;

  public PushMediaSendJob(Context context, long messageId, String destination) {
    super(context, constructParameters(context, destination));
    this.messageId = messageId;
  }

  @Override
  public void onAdded() {
    MmsDatabase mmsDatabase = DatabaseFactory.getMmsDatabase(context);
    mmsDatabase.markAsSending(messageId);
    mmsDatabase.markAsPush(messageId);
  }

  @Override
  public void onSend(MasterSecret masterSecret)
      throws RetryLaterException, MmsException, NoSuchMessageException,
             UndeliverableMessageException
  {
    MmsDatabase database = DatabaseFactory.getMmsDatabase(context);
    SendReq     message  = database.getOutgoingMessage(masterSecret, messageId);

    try {
      deliver(masterSecret, message);
      database.markAsPush(messageId);
      database.markAsSecure(messageId);
      database.markAsSent(messageId, "push".getBytes(), 0);
    } catch (InsecureFallbackApprovalException ifae) {
      Log.w(TAG, ifae);
      database.markAsPendingIndrizzlesmsFallback(messageId);
      notifyMediaMessageDeliveryFailed(context, messageId);
      ApplicationContext.getInstance(context).getJobManager().add(new DirectoryRefreshJob(context));
    } catch (UntrustedIdentityException uie) {
      Log.w(TAG, uie);
      Recipients recipients  = RecipientFactory.getRecipientsFromString(context, uie.getE164Number(), false);
      long       recipientId = recipients.getPrimaryRecipient().getRecipientId();

      database.addMismatchedIdentity(messageId, recipientId, uie.getIdentityKey());
      database.markAsSentFailed(messageId);
      database.markAsPush(messageId);
    }
  }

  @Override
  public boolean onShouldRetryThrowable(Exception exception) {
    if (exception instanceof RequirementNotMetException) return true;
    return false;
  }

  @Override
  public void onCanceled() {
    DatabaseFactory.getMmsDatabase(context).markAsSentFailed(messageId);
    notifyMediaMessageDeliveryFailed(context, messageId);
  }


  private void deliver(MasterSecret masterSecret, SendReq message)
      throws RetryLaterException, InsecureFallbackApprovalException, UntrustedIdentityException,
             UndeliverableMessageException
  {
    TextSecureMessageSender messageSender = messageSenderFactory.create(masterSecret);
    String                  destination   = message.getTo()[0].getString();

    try {
      message = getResolvedMessage(masterSecret, message, MediaConstraints.PUSH_CONSTRAINTS, false);

      TextSecureAddress          address      = getPushAddress(destination);
      List<TextSecureAttachment> attachments  = getAttachments(masterSecret, message);
      String                     body         = PartParser.getMessageText(message.getBody());
      TextSecureMessage          mediaMessage = TextSecureMessage.newBuilder()
                                                                 .withBody(body)
                                                                 .withAttachments(attachments)
                                                                 .withTimestamp(message.getSentTimestamp())
                                                                 .build();

      messageSender.sendMessage(address, mediaMessage);
    } catch (InvalidNumberException | UnregisteredUserException e) {
      Log.w(TAG, e);
      throw new InsecureFallbackApprovalException(e);
    } catch (IOException e) {
      Log.w(TAG, e);
      throw new RetryLaterException(e);
    }
  }
}
