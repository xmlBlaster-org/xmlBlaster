/*------------------------------------------------------------------------------
Name:      HelloWorld.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Applet test for xmlBlaster
Version:   $Id: HelloWorld.java,v 1.12 2000/10/18 20:45:41 ruff Exp $
------------------------------------------------------------------------------*/
package javaclients.HelloWorldApplet;

import org.xmlBlaster.client.protocol.XmlBlasterConnection;
import org.xmlBlaster.client.I_Callback;
import org.xmlBlaster.client.UpdateKey;
import org.xmlBlaster.client.UpdateQoS;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.engine.helper.MessageUnit;

import org.xmlBlaster.util.Log;
import org.xmlBlaster.util.XmlBlasterProperty;
import org.jutils.init.Args;
import org.jutils.time.StopWatch;

import java.applet.*;
import java.awt.event.*;
import java.awt.*;

// export CLASSPATH=$CLASSPATH:/opt/netscape/java/classes/java40.jar
// import netscape.security.*; // PrivilegeManager


/**
 * This client does test sending/receiving messages with hello world.<br />
 * <p />
 * Invoke examples:<br />
 * <pre>
 *    server:       jaco org.xmlBlaster.Main
 *    netscape:     file:///home/ruff/xmlBlaster/demo/javaclients/HelloWorldApplet/HelloWorld.html
 *    application:  jaco javaclients.HelloWorldApplet.HelloWorld
 * </pre>
 */
public class HelloWorld extends Applet implements I_Callback, ActionListener, org.jutils.log.LogableDevice, WindowListener
{
   private static String ME = "HelloWorld";
   public static boolean isApplet = true;      // usually true; but jacorb.orb.ORB.init(Applet, Properties) is buggy !!!
   public static HelloWorld helloWorld = null; // reference to myself (only for main)

   private String oid = "HelloWorld-Message";
   private XmlBlasterConnection corbaConnection;
   private String senderName = "HelloWorld-Applet";

   private MessageUnit msgUnit;     // a message to play with
   private final String contentMime = "text/plain";
   private final String contentMimeExtended = "1.0";

   // UI elements
   private Button actionButton;
   private Panel fPanel;
   private TextArea output;
   private TextField logOutput = null;


   /**
    * Constructs the HelloWorld object.
    */
   public void init()
   {
      /* // Aware: this is netscape specific code:
      try {
         PrivilegeManager MyPrivilegeManager=PrivilegeManager.getPrivilegeManager();
         MyPrivilegeManager.enablePrivilege("UniversalPropertyRead");
      }
      catch (netscape.security.ForbiddenTargetException e) {
        System.out.println("Ignoring netscape.security.ForbiddenTargetException");
      }
      */

      try {
         XmlBlasterProperty.init(this);
      } catch(org.jutils.JUtilsException e) {
         Log.panic(ME, e.toString());
      }

      initUI();

      connect();

      Log.getLogChannel().addLogDevice(this);
      Log.info(ME, "Connected to xmlBlaster");
      validate();
   }


   /**
    * initialize UI
    */
   private void initUI()
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
   void connect()
   {
      try {
         String passwd = "secret";

         if (isApplet)
            corbaConnection = new XmlBlasterConnection(this, "IOR"); // Find orb
         else
            corbaConnection = new XmlBlasterConnection(); // Find orb
         corbaConnection.login(senderName, passwd, null, this); // Login to xmlBlaster

         // a sample message unit
         String xmlKey = "<key oid='" + oid + "' contentMime='" + contentMime + "' contentMimeExtended='" + contentMimeExtended + "'>\n" +
                         "</key>";
         String senderContent = "Hello world!";
         String qos = "<qos><forceUpdate /></qos>";
         msgUnit = new MessageUnit(xmlKey, senderContent.getBytes(), qos);

         doSubscribe();
      }
      catch (Exception e) {
          Log.error(ME, e.toString());
          e.printStackTrace();
      }
      Log.info(ME, "Success: Login to xmlBlaster");
   }


   /**
    * Cleaning up .... erase() the previous message OID and logout
    */
   protected void tearDown()
   {
      if (corbaConnection != null) {
         String xmlKey = "<key oid='" + oid + "' queryType='EXACT'>\n</key>";
         String qos = "<qos></qos>";
         String[] strArr = null;
         try {
            strArr = corbaConnection.erase(xmlKey, qos);
         } catch(XmlBlasterException e) { Log.error(ME+"-tearDown()", "XmlBlasterException in erase(): " + e.reason); }
      }

      if (corbaConnection != null)
         corbaConnection.logout();
      Log.info(ME+"-tearDown", "Success: Logged out");
   }


   /**
    * Subscribe to message.
    */
   public void doSubscribe()
   {
      String xmlKey = "<key oid='" + oid + "' queryType='EXACT'>\n" +
                      "</key>";
      String qos = "<qos></qos>";
      try {
         corbaConnection.subscribe(xmlKey, qos);
         Log.info(ME, "Success: Subscribe on " + oid + " done");
      } catch(XmlBlasterException e) {
         Log.warn(ME+"-doSubscribe", "XmlBlasterException: " + e.reason);
      }
   }


   /**
    * Construct a message and publish it.
    */
   public void doPublish()
   {
      try {
         // With ForceUpdate, following messages with the same content will be updated
         corbaConnection.publish(msgUnit);
      } catch(XmlBlasterException e) {
         Log.warn(ME+"-doPublish", "XmlBlasterException: " + e.reason);
      }
      Log.info(ME, "Success: Published message");
   }


   /**
    * This is the callback method (I_Callback) invoked from XmlBlasterConnection
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
    * GUI event handler
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
    * Event fired by Log.java through interface LogableDevice.
    * <p />
    * Log output into TextField<br />
    */
   public void log(int level, String source, String str)
   {
      if (logOutput != null)
         logOutput.setText(str + "\n");
      else
         System.out.println(str + "\n");
   }


   /**
    * WindowListener events for applet
    */
   public void windowOpened(WindowEvent e) { Log.info(ME, "Event windowOpened"); }
   public void windowClosing(WindowEvent e) { Log.info(ME, "Event windowClosing"); tearDown(); }
   public void windowClosed(WindowEvent e) { Log.info(ME, "Event windowClosed"); tearDown(); }
   public void windowIconified(WindowEvent e) { Log.info(ME, "Event windowIconified"); }
   public void windowDeiconified(WindowEvent e) { Log.info(ME, "Event windowDeiconified"); }
   public void windowActivated(WindowEvent e) { Log.info(ME, "Event windowActivated"); }
   public void windowDeactivated(WindowEvent e) { Log.info(ME, "Event windowDeactivated"); }


   /**
    * To start it as an application invoke: jaco javaclients.HelloWorldApplet.HelloWorld
    * <p />
    * Note you need 'jaco' instead of 'java' to start the TestRunner, otherwise the JDK ORB is used
    * instead of the JacORB ORB, which won't work.
    */
   public static void main(String args[])
   {
      HelloWorld.isApplet = false;
      Frame frame = new Frame("HelloWorld v1.0");
      HelloWorld helloWorld = new HelloWorld();
      HelloWorld.helloWorld = helloWorld;
      //helloWorld.isApp_ = true;
      helloWorld.init();
      helloWorld.start();

      frame.addWindowListener(
         new WindowAdapter() {
            public void windowClosing(WindowEvent event) {
               HelloWorld.helloWorld.tearDown();
               Log.exit(ME, "Exit!");
            }
         }
      );

      frame.add("Center", helloWorld);
      frame.setSize(350, 250);
      frame.validate();
      frame.show();
   }
}

