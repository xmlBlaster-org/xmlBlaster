/*------------------------------------------------------------------------------
Name:      SimpleChat.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Demo of a simple chat client for xmlBlaster as java application
Version:   $Id: SimpleChat.java,v 1.23 2001/09/05 12:48:47 ruff Exp $
------------------------------------------------------------------------------*/
package javaclients.chat;

import org.xmlBlaster.util.Log;
import org.jutils.init.Args;
import org.jutils.JUtilsException;
import org.jutils.io.FileUtil;
import org.jutils.time.TimeHelper;

import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.XmlBlasterProperty;
import org.xmlBlaster.util.XmlKeyBase;
import org.xmlBlaster.client.protocol.XmlBlasterConnection;
import org.xmlBlaster.util.ConnectQos;
import org.xmlBlaster.client.UpdateKey;
import org.xmlBlaster.client.UpdateQoS;
import org.xmlBlaster.client.I_Callback;
import org.xmlBlaster.client.I_ConnectionProblems;
import org.xmlBlaster.engine.helper.CallbackAddress;
import org.xmlBlaster.engine.helper.MessageUnit;

import org.omg.CosNaming.*;
import java.awt.event.*;
import java.awt.*;
import java.util.Enumeration;
import java.util.Vector;
import java.util.StringTokenizer;
import java.text.DateFormat;
import java.text.DateFormat;
import java.util.Locale;
import java.util.Date;

import org.xmlBlaster.client.GetKeyWrapper;

/**
 * This client is a simple chat application using xmlBlaster.
 * <p>
 * It demonstrates 'raw' Corba access.
 * Usage:
 *    ${JacORB_HOME}/bin/jaco javaclients.chat.SimpleChat -name "nickname"
 * @author Mike Groezinger
 */

public class SimpleChat extends Frame implements I_Callback, ActionListener, I_ConnectionProblems {

   // XmlBlaster attributes
   private XmlBlasterConnection xmlBlasterConnection = null;
   private static String ME = "Mike's TestClient";
   private static String passwd ="some";
   private static String qos = "<qos></qos>";
   private String publishOid = "javaclients.chat.SimpleChat";
   private String xmlKey = null;
   private String logFileName = null;

   // UI elements
   private Button connectButton, actionButton, whoisThereButton;
   private Panel fPanel;
   private TextArea output;
   private TextField input;
   private Label label;
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
      try {
         logFileName = Args.getArg(args, "-logFile", System.getProperty("user.home") + System.getProperty("file.separator") + "xmlBlasterChat.log");
         Log.info(ME, "Logging messages to " + logFileName);
         label.setText(logFileName);
      } catch (JUtilsException e) {
         Log.error(ME, e.toString());
      }
      readOldMessagesFromFile();
   }


   protected void publishMessage(String content) {
      xmlKey = "<?xml version='1.0' encoding='ISO-8859-1' ?>\n" +
                       "<key oid='" + publishOid + "' contentMime='text/plain'>\n" +
                       "</key>";
      MessageUnit msgUnit = new MessageUnit(xmlKey, content.getBytes(), "<qos></qos>");
      Log.trace(ME, "Publishing ...");
      try {
         String str = xmlBlasterConnection.publish(msgUnit);
      } catch(XmlBlasterException e) {
         Log.warn(ME, "XmlBlasterException: " + e.reason);
      }
      Log.trace(ME, "Publishing done");
   }

   protected void getUserList() {
      if (xmlBlasterConnection == null) {
         Log.error(ME, "Please log in first");
         return;
      }

      publishMessage("I am retrieving the connected users list (ignore this)");
      try {
         GetKeyWrapper getKeyWrapper = new GetKeyWrapper("__sys__UserList");
         MessageUnit[] msgUnit = xmlBlasterConnection.get(getKeyWrapper.toXml(),"<qos></qos>");
         if (msgUnit != null) {
            for (int i=0; i < msgUnit.length; i++) {
                appendOutput("users: " + System.getProperty("line.separator") +
                            new String(msgUnit[i].content) + 
                            System.getProperty("line.separator"));
            }
            appendOutput("these where all users connected" + 
                         System.getProperty("line.separator"));
         }
      }
      catch (XmlBlasterException ex) {
         Log.error(ME, "error when getting the list of users");
      }
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

      label = new Label("LOGGING");
      fPanel.add("West",label);

      // Button for sending message (Publish )
      actionButton = new Button("Send");
      actionButton.setActionCommand("send");
      actionButton.addActionListener(this);
      fPanel.add("South",actionButton);

      // Button for requesting list of who is connected
      whoisThereButton = new Button("Who is There ?");
      whoisThereButton.setActionCommand("whoisThere");
      whoisThereButton.addActionListener(this);
      fPanel.add("South", whoisThereButton);

      // Textfield for input
      input = new TextField(60);
      input.addActionListener(this);
      fPanel.add("South",input);

      // Textfield for output (which comes from xmlBlaster callback)
      output = new TextArea("",12,80);
      add("South",output);
   }

   // event-handler
   public void actionPerformed(ActionEvent ev) {
      String command = ev.getActionCommand();
      Object obj = ev.getSource();

      if ("whoisThere".equals(command)) {
        getUserList();
        return;
      }

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
      else if( command.equals("send") ||
        ( (ev.getSource()) instanceof TextField )){

         if (xmlBlasterConnection == null) {
            Log.error(ME, "Please log in first");
            return;
         }

         //----------- Construct a message and publish it ---------
         String content = input.getText();

         publishMessage(content);
         input.setText("");
     }
   }

   /** adds text to output-field */
   public void appendOutput(String text){
      output.append(text);
      try { FileUtil.appendToFile(logFileName, text); } catch(JUtilsException e) { Log.warn(ME, "Can't log message:" + e.toString()); }
      output.repaint();
   }

   /** CallBack of xmlBlaster via I_Callback */
   public void update(String loginName, UpdateKey updateKey, byte[] content, UpdateQoS updateQoS)
   {
      String msgContent = new String(content);
      DateFormat df = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT);
      String text = df.format(new java.util.Date());
      text += "[" + updateQoS.getSender() +"]: ";
      text += msgContent;
      appendOutput(text +System.getProperty("line.separator"));
      Log.info(ME, "CallBack\n");
   }

   /** find xmlBlaster server, login and subscribe  */
   public void initBlaster(){
      try {
         try { // check if parameter -name <userName> is given at startup of client
            ME = Args.getArg(args, "-name", ME);
         } catch (JUtilsException e) {
            throw new XmlBlasterException(e);
         }

         xmlBlasterConnection = new XmlBlasterConnection(args);
         xmlBlasterConnection.login(ME, passwd, null, this);
         xmlBlasterConnection.initFailSave(this);  // configure settings on command line or in xmlBlaster.properties
      }
      catch (Exception e) {
         Log.error(ME, e.toString());
         e.printStackTrace();
      }
      subscription();
   }

   private void readOldMessagesFromFile()
   {
      // Read old messages from log file ...
      try {
         String data = FileUtil.readAsciiFile(logFileName);
         StringTokenizer st = new StringTokenizer(data, "\n");
         while (st.hasMoreTokens()) {
            String tmp = st.nextToken();
            output.append(tmp+System.getProperty("line.separator"));
         }
      } catch (JUtilsException e) {
         Log.warn(ME, "Can't read old logs from " + logFileName + ": " + e.toString());
      }
   }

   /** subscribe to messages */
   public void subscription() {
      try {
         //----------- Subscribe to OID -------
         Log.trace(ME, "Subscribing using the exact oid ...");
         String xmlKeyPub = "<key oid='" + publishOid + "' queryType='EXACT'>\n" +
                         "</key>";
         xmlBlasterConnection.subscribe(xmlKeyPub, "<qos></qos>");
         Log.trace(ME, "Subscribed to '" + publishOid + "' ...");
      } catch(XmlBlasterException e) {
         Log.warn(ME, "XmlBlasterException: " + e.reason);
      }
   }

   /** unsubsrcibe and logout from xmlBlaster */
   public void logout(){
      if (xmlBlasterConnection == null) return;
      //----------- Unsubscribe from the previous message --------
      if (xmlKey != null) {
         Log.trace(ME, "Unsubscribe ...");
         try {
            xmlBlasterConnection.unSubscribe(xmlKey, qos);
         } catch(XmlBlasterException e) {
            Log.warn(ME, "XmlBlasterException: " + e.reason);
         }
         Log.info(ME, "Unsubscribe done");
      }

      //----------- Logout --------------------------------------
      Log.trace(ME, "Logout ...");
      xmlBlasterConnection.logout();
   }

   private void usage() {
      Log.plain("\nAvailable options:");
      Log.plain("   -name               The login name");
      Log.plain("   -logFile            A file (with path) to log messages");
      XmlBlasterConnection.usage();
      Log.usage();
      Log.exit(ME, "Example: jaco javaclients.chat.SimpleChat -name Heidi");
   }

   /**
     * This is the callback method invoked from XmlBlasterConnection
     * informing the client in an asynchronous mode if the connection was lost.
     * <p />
     * This method is enforced through interface I_ConnectionProblems
     */
   public void lostConnection()
   {
      Log.warn(ME, "I_ConnectionProblems: Lost connection to xmlBlaster");
   }

   /**
     * This is the callback method invoked from XmlBlasterConnection
     * informing the client in an asynchronous mode if the connection was established.
     * <p />
     * This method is enforced through interface I_ConnectionProblems
     */
   public void reConnected()
   {
      subscription();
      try {
         if (xmlBlasterConnection.queueSize() > 0) {
            Log.info(ME, "We were lucky, reconnected to xmlBlaster, sending backup " + xmlBlasterConnection.queueSize() + " messages ...");
            xmlBlasterConnection.flushQueue();
         }
         else
            Log.info(ME, "We were lucky, reconnected to xmlBlaster, no backup messages to flush");
      }
      catch (XmlBlasterException e) {
         Log.error(ME, "Sorry, flushing of backup messages failed, they are lost: " + e.toString());
      }
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
      chat.setSize(460,350);
      chat.show();
   }
}



