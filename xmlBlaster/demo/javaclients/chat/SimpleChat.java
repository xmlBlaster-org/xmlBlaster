/*------------------------------------------------------------------------------
Name:      SimpleChat.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/
package javaclients.chat;

import org.jutils.JUtilsException;
import org.jutils.io.FileUtil;
import org.jutils.time.TimeHelper;

import org.jutils.log.LogChannel;
import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.dispatch.ConnectionStateEnum;
import org.xmlBlaster.util.qos.address.CallbackAddress;
import org.xmlBlaster.util.MsgUnit;
import org.xmlBlaster.client.qos.ConnectQos;
import org.xmlBlaster.client.key.UpdateKey;
import org.xmlBlaster.client.qos.UpdateQos;
import org.xmlBlaster.client.key.GetKey;
import org.xmlBlaster.client.qos.PublishReturnQos;
import org.xmlBlaster.client.I_Callback;
import org.xmlBlaster.client.I_ConnectionStateListener;
import org.xmlBlaster.client.I_XmlBlasterAccess;

import java.awt.event.*;
import java.awt.*;
import java.util.Enumeration;
import java.util.Vector;
import java.util.StringTokenizer;
import java.text.DateFormat;
import java.text.DateFormat;
import java.util.Locale;
import java.util.Date;


/**
 * This client is a simple chat application using xmlBlaster.
 * <p>
 * It demonstrates 'raw' Corba access.
 * Usage:
 *    java javaclients.chat.SimpleChat -loginName "nickname"
 * @author Mike Groezinger
 */
public class SimpleChat extends Frame implements I_Callback, ActionListener, I_ConnectionStateListener {

   // XmlBlaster attributes
   private final Global glob;
   private final LogChannel log;
   private I_XmlBlasterAccess xmlBlasterConnection = null;
   private static String ME = "Mike's TestClient";
   private static String passwd ="some";
   private static String qos = "<qos></qos>";
   private String publishOid = "javaclients.chat.SimpleChat";
   private String xmlKey = null;
   private String logFileName = null;
   private boolean withSound = true;

   // UI elements
   private Button connectButton, actionButton, whoisThereButton, soundButton;
   private Panel fPanel;
   private TextArea output;
   private TextField input;
   private Label label;

   private java.lang.reflect.Method speakMethod = null;
   private Object                   speaker     = null;

   public SimpleChat(Global glob){
      super(glob.getProperty().get("loginName", "SimpleChat - <NoName>"));
      this.glob = glob;
      this.log = glob.getLog("client");

      this.addWindowListener(
         new WindowAdapter() {
            public void windowClosing(WindowEvent event)
            {
               logout();
               System.exit(0);
            }
         }
      );

      initUI();
      pack();
      logFileName = glob.getProperty().get("logFile", System.getProperty("user.home") + System.getProperty("file.separator") + "xmlBlasterChat.log");
      log.info(ME, "Logging messages to " + logFileName);

                //prepare the speach synthetizer ...
      try {
        Class speech = Class.forName("com.eclettic.speech.DefaultInputSpeaker");
        java.lang.reflect.Constructor constr = speech.getConstructor(null);
        this.speaker = constr.newInstance(null);
        Class[] argClasses = new Class[1];
        argClasses[0] = String.class;
        this.speakMethod = speech.getMethod("speak", argClasses);
      }
      catch (Throwable ex) {
         log.error(ME, "Audio output of messages not activated, please use JDK 1.4 or better and add speech.jar to your classpath: " + ex.toString());
      }

      label.setText(logFileName);
      readOldMessagesFromFile();
   }


   protected void publishMessage(String content) {
      try {
         xmlKey = "<?xml version='1.0' encoding='ISO-8859-1' ?>\n" +
                       "<key oid='" + publishOid + "' contentMime='text/plain'>\n" +
                       "</key>";
         MsgUnit msgUnit = new MsgUnit(xmlKey, content.getBytes(), "<qos></qos>");
         log.trace(ME, "Publishing ...");
         xmlBlasterConnection.publish(msgUnit);
      } catch(XmlBlasterException e) {
         log.warn(ME, "XmlBlasterException: " + e.getMessage());
      }
      log.trace(ME, "Publishing done");
   }

   protected void getUserList() {
      if (xmlBlasterConnection == null) {
         log.error(ME, "Please log in first");
         return;
      }

      publishMessage("I am retrieving the connected users list (ignore this)");
      try {
         GetKey getKeyWrapper = new GetKey(glob, "__sys__UserList");
         MsgUnit[] msgUnit = xmlBlasterConnection.get(getKeyWrapper.toXml(),"<qos></qos>");
         if (msgUnit != null) {
            for (int i=0; i < msgUnit.length; i++) {
                appendOutput("users: " + System.getProperty("line.separator") +
                            new String(msgUnit[i].getContent()) + 
                            System.getProperty("line.separator"));
            }
            appendOutput("these where all users connected" + 
                         System.getProperty("line.separator"));
         }
      }
      catch (XmlBlasterException ex) {
         log.error(ME, "error when getting the list of users");
      }
   }

   /** initialize UI */
   public void initUI() {
      // MAIN-Frame
      this.setLayout(new BorderLayout());
      fPanel = new Panel();
      fPanel.setLayout(new BorderLayout());
      this.add("North", fPanel);

      // Button to connect to xmlBlaster
      connectButton = new Button("Connect");
      connectButton.setActionCommand("connect");
      connectButton.addActionListener(this);
      fPanel.add(connectButton, BorderLayout.WEST);


      label = new Label("LOGGING");
      add(label, BorderLayout.SOUTH);

      // Button for sending message (Publish )
      actionButton = new Button("Send");
      actionButton.setActionCommand("send");
      actionButton.addActionListener(this);
      fPanel.add(actionButton, BorderLayout.EAST);

      // Button for requesting list of who is connected
      whoisThereButton = new Button("Who is There ?");
      whoisThereButton.setActionCommand("whoisThere");
      whoisThereButton.addActionListener(this);
      fPanel.add(whoisThereButton, BorderLayout.SOUTH);




      // Button for requesting list of who is connected
      String soundText = "with Sound";
      if (!this.withSound) soundText = "no Sound";
      soundButton = new Button(soundText);
      soundButton.setActionCommand("sound");
      soundButton.addActionListener(this);
      fPanel.add(soundButton, BorderLayout.NORTH);

      // Textfield for input
      input = new TextField(60);
      input.addActionListener(this);
      fPanel.add(input, BorderLayout.CENTER);

      // Textfield for output (which comes from xmlBlaster callback)
      output = new TextArea("",12,80);
      output.setEditable(false);
//      add("South",output);
      add("Center",output);
   }

   // event-handler
   public void actionPerformed(ActionEvent ev) {
      String command = ev.getActionCommand();
      Object obj = ev.getSource();

      if ("whoisThere".equals(command)) {
        getUserList();
        return;
      }

          if ("sound".equals(command)) {
        if (this.withSound) {
            this.withSound = false;
            this.soundButton.setLabel("no Sound");
            fPanel.repaint();

        }
        else {
            this.withSound = true;
            this.soundButton.setLabel("with Sound");
            fPanel.repaint();
        }
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
            log.error(ME, "Please log in first");
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
//      output.insert(text, 0);

      try { FileUtil.appendToFile(logFileName, text); } catch(JUtilsException e) { log.warn(ME, "Can't log message:" + e.toString()); }
      //output.repaint();
      this.repaint();
   }

   /**
    * This is the callback method invoked from xmlBlaster
    * delivering us a new asynchronous message. 
    * @see org.xmlBlaster.client.I_Callback#update(String, UpdateKey, byte[], UpdateQos)
    */
   public String update(String cbSessionId, UpdateKey updateKey, byte[] content, UpdateQos updateQos)
   {
      String msgContent = new String(content);

      if (this.withSound) {
         java.awt.Toolkit.getDefaultToolkit().beep();

         if ((this.speakMethod != null) && (this.speaker !=null)) {
            try {
               Object[] args = new Object[1];
               args[0] = msgContent;
               this.speakMethod.invoke(this.speaker, args);
            }
            catch (Throwable ex){
               log.error(ME, "Audio output of messages not activated, please use JDK 1.4 or better and add speech.jar to your classpath: " + ex.toString());
            }
         }
         toFront();
      }


      DateFormat df = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT);
      String text = df.format(new java.util.Date());
      text += "[" + updateQos.getSender() +"]: ";
      text += msgContent;
      appendOutput(text +System.getProperty("line.separator"));
      log.info(ME, "CallBack\n");

      return "";
   }

   /** find xmlBlaster server, login and subscribe  */
   public void initBlaster(){
      try {
         xmlBlasterConnection = glob.getXmlBlasterAccess();
         ConnectQos qos = new ConnectQos(glob);
         xmlBlasterConnection.connect(qos, this);
         xmlBlasterConnection.registerConnectionListener(this);  // configure settings on command line or in xmlBlaster.properties
      }
      catch (Exception e) {
         log.error(ME, e.toString());
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
         log.warn(ME, "Can't read old logs from " + logFileName + ": " + e.toString());
      }
   }

   /** subscribe to messages */
   public void subscription() {
      try {
         //----------- Subscribe to OID -------
         log.trace(ME, "Subscribing using the exact oid ...");
         String xmlKeyPub = "<key oid='" + publishOid + "' queryType='EXACT'>\n" +
                         "</key>";
         xmlBlasterConnection.subscribe(xmlKeyPub, "<qos></qos>");
         log.trace(ME, "Subscribed to '" + publishOid + "' ...");
      } catch(XmlBlasterException e) {
         log.warn(ME, "XmlBlasterException: " + e.getMessage());
      }
   }

   /** unsubsrcibe and logout from xmlBlaster */
   public void logout(){
      if (xmlBlasterConnection == null) return;
      //----------- Unsubscribe from the previous message --------
      if (xmlKey != null) {
         log.trace(ME, "Unsubscribe ...");
         try {
            xmlBlasterConnection.unSubscribe(xmlKey, qos);
         } catch(XmlBlasterException e) {
            log.warn(ME, "XmlBlasterException: " + e.getMessage());
         }
         log.info(ME, "Unsubscribe done");
      }

      //----------- Logout --------------------------------------
      log.trace(ME, "Logout ...");
      xmlBlasterConnection.disconnect(null);
   }

   private static void usage(Global glob) {
      System.out.println(glob.usage());
      System.err.println("Example: java javaclients.chat.SimpleChat -loginName Heidi");
      System.exit(1);
   }

   /**
     * This is the callback method invoked from I_XmlBlasterAccess
     * informing the client in an asynchronous mode if the connection was established.
     * @see I_ConnectionStateListener
     */
   public void reachedAlive(ConnectionStateEnum oldState, I_XmlBlasterAccess connection)
   {
      subscription();
      if (connection.getQueue().getNumOfEntries() > 0) {
         log.info(ME, "We were lucky, reconnected to xmlBlaster, sending backup " +
                        connection.getQueue().getNumOfEntries() + " messages ...");
      }
      else
         log.info(ME, "We were lucky, reconnected to xmlBlaster, no backup messages to flush");
   }

   /**
     * This is the callback method invoked from I_XmlBlasterAccess
     * informing the client in an asynchronous mode if the connection was lost.
     * @see I_ConnectionStateListener
     */
   public void reachedPolling(ConnectionStateEnum oldState, I_XmlBlasterAccess connection)
   {
      log.warn(ME, "I_ConnectionStateListener: Lost connection to xmlBlaster");
   }

   /**
     * @see I_ConnectionStateListener
     */
   public void reachedDead(ConnectionStateEnum oldState, I_XmlBlasterAccess connection)
   {
      log.warn(ME, "I_ConnectionStateListener: DEAD - Lost connection to xmlBlaster, giving up.");
   }

   public static void main(String args[]) {
      Global glob = new Global();
      if (glob.init(args) != 0) {
         usage(glob);
         System.exit(1);
      }

      SimpleChat chat = new SimpleChat(glob);
      chat.setSize(460,350);
      chat.show();
   }
}



