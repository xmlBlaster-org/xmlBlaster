package org.xmlBlaster.protocol.email;

import java.io.*;
import java.util.Properties;
import javax.mail.*;
import javax.mail.internet.*;

/**
 * Demo code from SUN,
 * need to be integrated into xmlBlaster
 * To test it invoke:
 * java org.xmlBlaster.protocol.email.FetchEmail <yourEmailServer>
 */
public class FetchEmail {
  public static void main (String args[]) 
      throws Exception {
    String host = args[0];

    // Get system properties
    Properties props = System.getProperties();
    props.put("mail.pop3.host", host);

    // Setup authentication, get session
    Authenticator auth = new PopupAuthenticator();
    Session session = 
      Session.getDefaultInstance(props, auth);

    // Get the store
    Store store = session.getStore("pop3");
    store.connect();

    // Get folder
    Folder folder = store.getFolder("INBOX");
    folder.open(Folder.READ_ONLY);

    // Get directory
    Message message[] = folder.getMessages();
    for (int i=0, n=message.length; i<n; i++) {

       System.out.println(i + ": " 
         + message[i].getFrom()[0] 
         + "\t" + message[i].getSubject());
       String content = 
         message[i].getContent().toString();
       if (content.length() > 200) {
         content = content.substring(0, 200);
       }
       System.out.print(content);
    }

    // Close connection 
    folder.close(false);
    store.close();
    System.exit(0);
  }
}


