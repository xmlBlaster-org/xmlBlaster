/*------------------------------------------------------------------------------
Name:      SimpleChat.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Demo of a simple chat client for xmlBlaster as java application
Version:   $Id: SimpleChat.java,v 1.12 2000/06/26 07:12:34 ruff Exp $
------------------------------------------------------------------------------*/
package javaclients.chat;

import org.jutils.log.Log;
import org.jutils.init.Args;
import org.jutils.JUtilsException;

import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.XmlBlasterProperty;
import org.xmlBlaster.util.XmlKeyBase;
import org.xmlBlaster.engine.helper.CallbackAddress;
import org.xmlBlaster.protocol.corba.serverIdl.Server;
import org.xmlBlaster.protocol.corba.clientIdl.BlasterCallback;
import org.xmlBlaster.protocol.corba.clientIdl.BlasterCallbackOperations;
import org.xmlBlaster.protocol.corba.clientIdl.BlasterCallbackPOATie;
import org.xmlBlaster.protocol.corba.clientIdl.BlasterCallbackHelper;
import org.xmlBlaster.client.CorbaConnection;
import org.xmlBlaster.client.LoginQosWrapper;
import org.xmlBlaster.client.UpdateQoS;

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

public class SimpleChat extends Frame implements BlasterCallbackOperations, ActionListener{

   // XmlBlaster attributes
   private Server xmlBlaster = null;
   private static String ME = "Mike�s TestClient";
   private static String passwd ="some";
   private static String qos = "<qos></qos>";
   private String publishOid = "javaclients.chat.SimpleChat";
   private String xmlKey = "";
   private CorbaConnection corbaConnection = null;

   // UI elements
   private Button connectButton, actionButton;
   private Panel fPanel;
   private TextArea output;
   private TextField input;
   private String args[];

   public SimpleChat(String title, String args[]){
      super(title);
      try {
         XmlBlasterProperty.init(args);
      } catch(org.jutils.JUtilsException e) {
         Log.panic(ME, e.toString());
      }

      this.addWindowListener(
         new WindowAdapter() {
            public void windowClosing(WindowEvent event)
            {
               if (xmlBlaster != null){
                  logout();
               }
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
            xmlBlaster = null;
            connectButton.setLabel("Connect");
         }
      }
      // publish new message
      else if(command.equals("send") ||( (ev.getSource()) instanceof TextField )){

         if (xmlBlaster == null) {
            Log.error(ME, "Please log in first");
            return;
         }

         //----------- Construct a message and publish it ---------
         String content = input.getText();
         xmlKey = "<?xml version='1.0' encoding='ISO-8859-1' ?>\n" +
                         "<key oid='" + publishOid + "' contentMime='text/plain'>\n" +
                         "</key>";
         org.xmlBlaster.protocol.corba.serverIdl.MessageUnit msgUnit = new org.xmlBlaster.protocol.corba.serverIdl.MessageUnit(xmlKey, content.getBytes(), "<qos></qos>");
         Log.trace(ME, "Publishing ...");
         try {
            String str = xmlBlaster.publish(msgUnit);
         } catch(org.xmlBlaster.protocol.corba.serverIdl.XmlBlasterException e) {
            Log.warning(ME, "XmlBlasterException: " + e.reason);
         }
         Log.trace(ME, "Publishing done");
         input.setText("");
     }
   }

   /** adds text to output-field */
   public void appendOutput(String text){
      output.append(text);
   }

   /** CallBack of xmlBlaster */
   public void update(org.xmlBlaster.protocol.corba.serverIdl.MessageUnit[] msgUnitArr)
   {

      for (int ii=0; ii<msgUnitArr.length; ii++) {
         org.xmlBlaster.protocol.corba.serverIdl.MessageUnit msgUnit = msgUnitArr[ii];
         XmlKeyBase xmlKey = null;
         UpdateQoS updateQoS = null;
         try {
            xmlKey = new XmlKeyBase(msgUnit.xmlKey);
            updateQoS = new UpdateQoS(msgUnit.qos);
            String tmp = updateQoS.printOn().toString();

         } catch (XmlBlasterException e) {
            Log.error(ME, e.reason);
         }
         String msgContent = new String(msgUnit.content);
         appendOutput("[" + updateQoS.getSender() +"]: " + msgContent +"\n");
         Log.info(ME, "CallBack\n");
      }

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

         //----------- Find orb ----------------------------------
         corbaConnection = new CorbaConnection(args);

         //---------- Building a Callback server ----------------------
         // Getting the default POA implementation "RootPOA"
         org.omg.PortableServer.POA rootPOA =
            org.omg.PortableServer.POAHelper.narrow(corbaConnection.getOrb().resolve_initial_references("RootPOA"));
         rootPOA.the_POAManager().activate();

         // Intializing my Callback interface:
         BlasterCallbackPOATie callbackTie = new BlasterCallbackPOATie(this);
         BlasterCallback callback = BlasterCallbackHelper.narrow(rootPOA.servant_to_reference( callbackTie ));

         //----------- Login to xmlBlaster -----------------------
         CallbackAddress addr = new CallbackAddress("IOR", corbaConnection.getOrb().object_to_string(callback));
         LoginQosWrapper qos = new LoginQosWrapper(addr); // == "<qos><callback type='IOR'>IOR:00113220001...</callback></qos>";

         xmlBlaster = corbaConnection.login(ME, passwd, qos);

         //----------- Subscribe to OID -------
         Log.trace(ME, "Subscribing using the exact oid ...");
         String xmlKey = "<key oid='" + publishOid + "' queryType='EXACT'>\n" +
                         "</key>";
         try {
            xmlBlaster.subscribe(xmlKey, "<qos></qos>");
         } catch(org.xmlBlaster.protocol.corba.serverIdl.XmlBlasterException e) {
            Log.warning(ME, "XmlBlasterException: " + e.reason);
         }
         Log.trace(ME, "Subscribed to '" + publishOid + "' ...");
      }
      catch (Exception e) {
          e.printStackTrace();
      }
   }

   /** unsubsrcibe and logout from xmlBlaster */
   public void logout(){
      if (xmlBlaster == null) return;
      //----------- Unsubscribe from the previous message --------
      Log.trace(ME, "Unsubscribe ...");
      try {
         xmlBlaster.unSubscribe(xmlKey, qos);
      } catch(org.xmlBlaster.protocol.corba.serverIdl.XmlBlasterException e) {
         Log.warning(ME, "XmlBlasterException: " + e.reason);
      }
      Log.info(ME, "Unsubscribe done");

      //----------- Logout --------------------------------------
      Log.trace(ME, "Logout ...");
      corbaConnection.logout();
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



