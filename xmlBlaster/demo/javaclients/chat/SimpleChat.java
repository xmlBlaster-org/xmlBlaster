/*------------------------------------------------------------------------------
Name:      SimpleChat.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Demo of a simple chat client for xmlBlaster as java application
Version:   $Id: SimpleChat.java,v 1.16 2000/11/14 16:39:07 freidlin Exp $
------------------------------------------------------------------------------*/
package javaclients.chat;

import org.xmlBlaster.util.Log;
import org.jutils.init.Args;
import org.jutils.JUtilsException;

import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.XmlBlasterProperty;
import org.xmlBlaster.util.XmlKeyBase;
import org.xmlBlaster.client.protocol.XmlBlasterConnection;
import org.xmlBlaster.client.LoginQosWrapper;
import org.xmlBlaster.client.UpdateKey;
import org.xmlBlaster.client.UpdateQoS;
import org.xmlBlaster.client.I_Callback;
import org.xmlBlaster.engine.helper.CallbackAddress;
import org.xmlBlaster.engine.helper.MessageUnit;

import org.omg.CosNaming.*;
import java.awt.event.*;
import java.awt.*;


/**
 * This client is a simple chat application using xmlBlaster.
 * <p>
 * It demonstrates 'raw' Corba access.
 * Usage:
 *    ${JacORB_HOME}/bin/jaco javaclients.chat.SimpleChat -name "nickname"
 * @author Mike Groezinger
 */

public class SimpleChat extends Frame implements I_Callback, ActionListener{

   // XmlBlaster attributes
   private XmlBlasterConnection corbaConnection = null;
   private static String ME = "Mike's TestClient";
   private static String passwd ="some";
   private static String qos = "<qos></qos>";
   private String publishOid = "javaclients.chat.SimpleChat";
   private String xmlKey = null;

   // UI elements
   private Button connectButton, actionButton;
   private Panel fPanel;
   private TextArea output;
   private TextField input;
   private String args[];

   public SimpleChat(String title, String args[]){
      super(title);
      try {
         if (XmlBlasterProperty.init(args))
            usage();

      } catch(org.jutils.JUtilsException e) {
         usage();
         Log.panic(ME, e.toString());
      }

      this.addWindowListener(
         new WindowAdapter() {
            public void windowClosing(WindowEvent event)
            {
               logout();
               System.exit(0);
            }
         }
      );

      this.args = args;
      initUI();
      pack();
   }


   /** initialize UI */
   public void initUI() {
      // MAIN-Frame
      this.setLayout(new BorderLayout());
      fPanel = new Panel();
      this.add(fPanel);

      // Button to connect to xmlBlaster
      connectButton = new Button("Connect");
      connectButton.setActionCommand("connect");
      connectButton.addActionListener(this);
      fPanel.add("North",connectButton);

      // Button for sending message (Publish )
      actionButton = new Button("Send");
      actionButton.setActionCommand("send");
      actionButton.addActionListener(this);
      fPanel.add("South",actionButton);

      // Textfield for input
      input = new TextField(40);
      input.addActionListener(this);
      fPanel.add("South",input);

      // Textfield for output (which comes from xmlBlaster callback)
      output = new TextArea("",10,50);
      add("South",output);
   }

   // event-handler
   public void actionPerformed(ActionEvent ev) {
      String command = ev.getActionCommand();
      Object obj = ev.getSource();

      if(command.equals("connect")){
         // Connect to xmlBlaster server
         if((connectButton.getLabel()).equals("Connect")){
            initBlaster();
            connectButton.setLabel("Logout");
         }
         //logout from server
         else if((connectButton.getLabel()).equals("Logout")){
            logout();
            connectButton.setLabel("Connect");
         }
      }
      // publish new message
      else if(command.equals("send") ||( (ev.getSource()) instanceof TextField )){

         if (corbaConnection == null) {
            Log.error(ME, "Please log in first");
            return;
         }

         //----------- Construct a message and publish it ---------
         String content = input.getText();
         xmlKey = "<?xml version='1.0' encoding='ISO-8859-1' ?>\n" +
                         "<key oid='" + publishOid + "' contentMime='text/plain'>\n" +
                         "</key>";
         MessageUnit msgUnit = new MessageUnit(xmlKey, content.getBytes(), "<qos></qos>");
         Log.trace(ME, "Publishing ...");
         try {
            String str = corbaConnection.publish(msgUnit);
         } catch(XmlBlasterException e) {
            Log.warn(ME, "XmlBlasterException: " + e.reason);
         }
         Log.trace(ME, "Publishing done");
         input.setText("");
     }
   }

   /** adds text to output-field */
   public void appendOutput(String text){
      output.append(text);
   }

   /** CallBack of xmlBlaster via I_Callback */
   public void update(String loginName, UpdateKey updateKey, byte[] content, UpdateQoS updateQoS)
   {
      String msgContent = new String(content);
      appendOutput("[" + updateQoS.getSender() +"]: " + msgContent +"\n");
      Log.info(ME, "CallBack\n");
   }

   /** find xmlBlaster server, login and subscribe  */
   public void initBlaster(){
      try {
         try {
            // check if parameter -name <userName> is given at startup of client
            ME = Args.getArg(args, "-name", ME);
         } catch (JUtilsException e) {
            throw new XmlBlasterException(e);
         }

         corbaConnection = new XmlBlasterConnection(args);
         corbaConnection.login(ME, passwd, null, this);

         //----------- Subscribe to OID -------
         Log.trace(ME, "Subscribing using the exact oid ...");
         String xmlKeyPub = "<key oid='" + publishOid + "' queryType='EXACT'>\n" +
                         "</key>";
         try {
            corbaConnection.subscribe(xmlKeyPub, "<qos></qos>");
         } catch(XmlBlasterException e) {
            Log.warn(ME, "XmlBlasterException: " + e.reason);
         }
         Log.trace(ME, "Subscribed to '" + publishOid + "' ...");
      }
      catch (Exception e) {
          e.printStackTrace();
      }
   }

   /** unsubsrcibe and logout from xmlBlaster */
   public void logout(){
      if (corbaConnection == null) return;
      //----------- Unsubscribe from the previous message --------
      if (xmlKey != null) {
         Log.trace(ME, "Unsubscribe ...");
         try {
            corbaConnection.unSubscribe(xmlKey, qos);
         } catch(XmlBlasterException e) {
            Log.warn(ME, "XmlBlasterException: " + e.reason);
         }
         Log.info(ME, "Unsubscribe done");
      }

      //----------- Logout --------------------------------------
      Log.trace(ME, "Logout ...");
      corbaConnection.logout();
   }

   private void usage() {
      Log.plain("\nAvailable options:");
      Log.plain("   -name               The login name");
      XmlBlasterConnection.usage();
      Log.usage();
      Log.exit(ME, "Example: jaco javaclients.chat.SimpleChat -name Heidi");
   }

   public static void main(String args[]) {
      String title = "SimpleChat";
      String loginName = null;
      try {
         loginName = Args.getArg(args, "-name", "Otto");
      } catch (JUtilsException e) {
         Log.panic(title, e.toString());
      }
      SimpleChat chat = new SimpleChat(loginName, args);
      chat.setSize(320,250);
      chat.show();
   }
}



