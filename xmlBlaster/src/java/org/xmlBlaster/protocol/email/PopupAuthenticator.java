package org.xmlBlaster.protocol.email;

import javax.mail.*;
import javax.swing.*;
import java.util.*;

/*
 * Demo code from SUN to retrieve mails from a POP3 account
 * Needs to be integrated into xmlBlaster
 */
public class PopupAuthenticator 
    extends Authenticator {

  public PasswordAuthentication 
      getPasswordAuthentication() {
    String username, password;

    String result = JOptionPane.showInputDialog(
      "Enter 'username,password'");

    StringTokenizer st = 
      new StringTokenizer(result, ",");
    username = st.nextToken();
    password = st.nextToken();

    return new PasswordAuthentication(
      username, password);
  }
}


