/*------------------------------------------------------------------------------
Name:      HelloWorld.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Applet test for xmlBlaster
Version:   $Id: HelloWorld.java,v 1.1 2000/01/17 16:50:33 ruff Exp $
------------------------------------------------------------------------------*/
package javaclients.applet;

import org.xmlBlaster.client.CorbaConnection;
import org.xmlBlaster.client.I_Callback;
import org.xmlBlaster.client.UpdateKey;
import org.xmlBlaster.client.UpdateQoS;
import org.xmlBlaster.util.Log;
import org.xmlBlaster.util.Args;
import org.xmlBlaster.util.StopWatch;
import org.xmlBlaster.serverIdl.*;
import org.xmlBlaster.clientIdl.*;

import java.awt.event.*;
import java.awt.*;


/**
 * This client does test sending/receiving messages with hello world.<br />
 * <p />
 * Invoke examples:<br />
 * <code>
 *    netscape:     file:/$XMLBLASTER_HOME/demo/javaclients/applet/HelloWorld.html
 *    application:  jaco javaclients.applet.HelloWorld
 * </code>
 */
public class HelloWorld extends Frame implements I_Callback, ActionListener, org.xmlBlaster.util.LogListener
{
   private static String ME = "HelloWorld";

   private String publishOid = "";
   private String oid = "HelloWorld-Message";
   private CorbaConnection senderConnection;
   private Server xmlBlaster = null;
   private String senderName;
   private String senderContent;

   private MessageUnit messageUnit;     // a message to play with
   private final String contentMime = "text/plain";
   private final String contentMimeExtended = "1.0";

   // UI elements
   private Button actionButton;
   private Panel fPanel;
   private TextArea output;
   private TextField logOutput;
   private String args[];


   /**
    * Constructs the HelloWorld object.
    * <p />
    * @param testName  The name used as title
    * @param loginName The name to login to the xmlBlaster
    */
   public HelloWorld(String testName, String senderName)
   {
      super(testName);
      this.senderName = senderName;
      this.args = args;

      Log.setDefaultLogLevel();
      Log.addLogListener(this);

      this.addWindowListener(
         new WindowAdapter() {
            public void windowClosing(WindowEvent event)
            {
               if (xmlBlaster != null) {
                  tearDown();
               }
               Log.exit(HelloWorld.ME, "Exit!");
            }
         }
      );

      initUI();

      setUp();

      pack();
   }


   /**
    * initialize UI
    */
   public void initUI()
   {
      // MAIN-Frame
      this.setLayout(new BorderLayout());
      fPanel = new Panel();
      fPanel.setLayout(new BorderLayout());
      this.add(fPanel);

      // Button for sending message (Publish )
      actionButton = new Button("Send 'Hello world'");
      actionButton.setActionCommand("send");
      actionButton.addActionListener(this);
      fPanel.add(actionButton, BorderLayout.NORTH);

      // Textfield for output (which comes from xmlBlaster callback)
      output = new TextArea("",10,50);
      fPanel.add(output, BorderLayout.CENTER);

      // Textfield for logging output
      logOutput = new TextField();
      logOutput.addActionListener(this);
      fPanel.add(logOutput, BorderLayout.SOUTH);
   }


   /**
    * Sets up connection.
    * <p />
    * Connect to xmlBlaster and login
    */
   protected void setUp()
   {
      try {
         String passwd = "secret";
         String qos = "<qos></qos>";

         senderConnection = new CorbaConnection(); // Find orb
         xmlBlaster = senderConnection.login(senderName, passwd, qos, this); // Login to xmlBlaster

         // a sample message unit
         String xmlKey = "<key oid='" + oid + "' contentMime='" + contentMime + "' contentMimeExtended='" + contentMimeExtended + "'>\n" +
                         "</key>";
         senderContent = "Hello world!";
         messageUnit = new MessageUnit(xmlKey, senderContent.getBytes());

         doSubscribe();
      }
      catch (Exception e) {
          Log.error(ME, e.toString());
          e.printStackTrace();
      }
      Log.info(ME, "Success: Login to xmlBlaster");
   }


   /**
    * Tears down the fixture.
    * <p />
    * cleaning up .... erase() the previous message OID and logout
    */
   protected void tearDown()
   {
      String xmlKey = "<key oid='" + oid + "' queryType='EXACT'>\n</key>";
      String qos = "<qos></qos>";
      String[] strArr = null;
      try {
         strArr = xmlBlaster.erase(xmlKey, qos);
      } catch(XmlBlasterException e) { Log.error(ME+"-tearDown()", "XmlBlasterException in erase(): " + e.reason); }

      senderConnection.logout(xmlBlaster);
      Log.info(ME+"-tearDown", "Success: Logged out");
   }


   /**
    * TEST: Subscribe to message.
    */
   public void doSubscribe()
   {
      String xmlKey = "<key oid='" + oid + "' queryType='EXACT'>\n" +
                      "</key>";
      String qos = "<qos></qos>";
      try {
         xmlBlaster.subscribe(xmlKey, qos);
         Log.info(ME, "Success: Subscribe on " + oid + " done");
      } catch(XmlBlasterException e) {
         Log.warning(ME+"-doSubscribe", "XmlBlasterException: " + e.reason);
      }
   }


   /**
    * TEST: Construct a message and publish it.
    * <p />
    * The returned publishOid is checked
    */
   public void doPublish()
   {
      try {
         String qos = "<qos><ForceUpdate /></qos>";
         publishOid = xmlBlaster.publish(messageUnit, qos);
      } catch(XmlBlasterException e) {
         Log.warning(ME+"-doPublish", "XmlBlasterException: " + e.reason);
      }
      Log.info(ME, "Success: Published message");
   }


   /**
    * This is the callback method (I_Callback) invoked from CorbaConnection
    * informing the client in an asynchronous mode about a new message.
    * <p />
    * The raw CORBA-BlasterCallback.update() is unpacked and for each arrived message
    * this update is called.
    *
    * @param loginName The name to whom the callback belongs
    * @param updateKey The arrived key
    * @param content   The arrived message content
    * @param qos       Quality of Service of the MessageUnit
    */
   public void update(String loginName, UpdateKey updateKey, byte[] content, UpdateQoS updateQoS)
   {
      Log.info(ME, "Success: Update of message");
      String msgContent = new String(content);
      output.append(msgContent +"\n");
   }


   /**
    * event-handler
    */
   public void actionPerformed(ActionEvent ev)
   {
      String command = ev.getActionCommand();
      Object obj = ev.getSource();
      if (command.equals("send")) {
         doPublish();  // publish new message
     }
   }


   /**
    * Event fired by Log.java through interface LogListener.
    * <p />
    * Log output into TextField<br />
    */
   public void log(String str)
   {
      logOutput.setText(str + "\n");
   }


   /**
    * Invoke: jaco testsuite.org.xmlBlaster.HelloWorld
    * <p />
    * Note you need 'jaco' instead of 'java' to start the TestRunner, otherwise the JDK ORB is used
    * instead of the JacORB ORB, which won't work.
    * <br />
    * @deprecated Use the TestRunner from the testsuite to run it:<p />
    * <code>   jaco -Djava.compiler= test.textui.TestRunner testsuite.org.xmlBlaster.HelloWorld</code>
    */
   public static void main(String args[])
   {
      HelloWorld helloWorld = new HelloWorld("HelloWorld", "Tim");
      //helloWorld.setSize(320,350);
      helloWorld.show();
   }
}

