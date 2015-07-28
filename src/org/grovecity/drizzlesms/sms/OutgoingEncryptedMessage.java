package org.grovecity.drizzlesms.sms;

import org.grovecity.drizzlesms.recipients.Recipient;
import org.grovecity.drizzlesms.recipients.Recipients;

public class OutgoingEncryptedMessage extends OutgoingTextMessage {

  public OutgoingEncryptedMessage(Recipients recipients, String body) {
    super(recipients, body);
  }

  public OutgoingEncryptedMessage(Recipient recipient, String body) {
    super(recipient, body);
  }

  private OutgoingEncryptedMessage(OutgoingEncryptedMessage base, String body) {
    super(base, body);
  }

  @Override
  public boolean isSecureMessage() {
    return true;
  }

  @Override
  public OutgoingTextMessage withBody(String body) {
    return new OutgoingEncryptedMessage(this, body);
  }
}
