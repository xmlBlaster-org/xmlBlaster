/*------------------------------------------------------------------------------
Name:      PublishFile.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Code for a client to publish files to xmlBlaster
Version:   $Id$
------------------------------------------------------------------------------*/
package org.xmlBlaster.client.feeder;

import org.xmlBlaster.client.qos.ConnectQos;
import org.xmlBlaster.client.I_XmlBlasterAccess;
import org.xmlBlaster.client.key.PublishKey;
import org.xmlBlaster.client.qos.PublishQos;
import org.xmlBlaster.client.qos.PublishReturnQos;
import org.xmlBlaster.util.FileLocator;
import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.MsgUnit;

import java.util.logging.Logger;
import java.util.logging.Level;
import org.xmlBlaster.util.StopWatch;

import java.io.File;


/**
 * Publish files to xmlBlaster.
 * <br />
 * Use this as a command line tool to publish files, images, etc. as messages to xmlBlaster.
 * Invoke examples:<br />
 * <pre>
 *    java org.xmlBlaster.client.feeder.PublishFile -c &lt;content-file> -k &lt;key-file> -q &lt;qos-file> -m &lt;mime-type>
 * </pre>
 * For other supported options type
 * <pre>
 *    java org.xmlBlaster.client.feeder.PublishFile -?
 * </pre>
 */
public class PublishFile
{
   private static final String ME = "PublishFile";
   private I_XmlBlasterAccess senderConnection;
   private Global glob;
   private static Logger log = Logger.getLogger(PublishFile.class.getName());
   private String loginName;
   private String passwd;

   /**
    * Constructs the PublishFile object.
    * <p />
    * Tries to support you in guessing the missing command line parameters.
    * <p />
    * Start with parameter -? to get a usage description.<br />
    * These command line parameters are not merged with xmlBlaster.property properties.
    * <p />
    * By default the classes in the java.io package always resolve relative pathnames
    * against the current user directory. This directory is named by the system property user.dir,
    * and is typically the directory in which the Java virtual machine was invoked
    *
    * @param args      Command line arguments
    */
   public PublishFile(String[] args) throws XmlBlasterException
   {
      glob = new Global();
      if (glob.init(args) != 0) {
         usage();
         System.err.println(ME + ": Bye");
         System.exit(1);
      }


      loginName = glob.getProperty().get("loginName", ME);
      passwd = glob.getProperty().get("passwd", "secret");

      String contentMime = glob.getProperty().get("m", (String)null);
      String contentMimeExtended = glob.getProperty().get("me", "1.0"); // optional

      // if passing the stuff in files:
      String contentFile = glob.getProperty().get("c", (String)null);
      String keyFile = glob.getProperty().get("k", (String)null);
      String qosFile = glob.getProperty().get("q", (String)null);

      // if passing the text directly on the command line:
      String xmlKeyGiven = glob.getProperty().get("xmlKey", (String)null);
      String xmlQosGiven = glob.getProperty().get("xmlQos", (String)null);
      String contentGiven = glob.getProperty().get("content", (String)null);

      String body = null;
      String exte = null;
      if (contentFile != null) {
         File contentHandle = new File(contentFile);
         String name = contentHandle.getPath();
         body = FileLocator.getBody(name);      // filename without extension
         exte = FileLocator.getExtension(name);
      }

      // Determine content ...
      byte[] content = null;
      if (contentFile != null) {
         try { content = FileLocator.readFile(contentFile); }
         catch (XmlBlasterException e) {
            log.severe(e.toString());
         }
      }
      if (content == null && contentGiven != null) {
         content = contentGiven.getBytes();
      }
      if (content == null) {
         content = new byte[0];
         // allow empty contents
         //log.panic(ME, "File content is missing, specify content as '-c <file>' or '-content <the content text>' (get help with -?)");
      }

      // Determine XmlKey ...
      String xmlKey = null;
      if (keyFile != null) {
         try { xmlKey = FileLocator.readAsciiFile(keyFile); }
         catch (XmlBlasterException e) {
            log.severe(e.toString());
         }
      }
      if (xmlKey == null) {
         xmlKey = xmlKeyGiven;
      }
      if (contentMime == null && exte != null) {
         contentMime = FileLocator.extensionToMime(exte, null);
      }
      if (xmlKey == null && body != null) { // The filename will be the key-oid ...
         if (contentMime == null) {
            log.severe("File MIME type is unknown, specify MIME type as '-m <MIME>', for example '-m \"image/gif\"' (get help with -?)");
            System.exit(1);
         }
         PublishKey publishKey = new PublishKey(glob, body, contentMime, contentMimeExtended);
         xmlKey = publishKey.toXml();  // default <key oid=......></key>
      }
      if (xmlKey == null) {
         log.severe("XmlKey is missing, specify key as '-k <file>' or '-xmlKey <the XML key>' (get help with -?)");
         System.exit(1);
      }

      // Determine XmlQoS ...
      String xmlQos = null;
      if (qosFile != null) {
         try { xmlQos = FileLocator.readAsciiFile(qosFile); }
         catch (XmlBlasterException e) {
            log.severe(e.toString());
         }
      }
      if (xmlQos == null) {
         xmlQos = xmlQosGiven;
      }
      if (xmlQos == null) {
         PublishQos publishQos = new PublishQos(glob);
         xmlQos = publishQos.toXml();  // default qos = "<qos></qos>"
      }

      feed(xmlKey, content, xmlQos);
   }


   /**
    * Open the connection, publish the message, close the connection.
    */
   public PublishFile(String loginName, String passwd, String xmlKey, byte[] content, String xmlQos)
   {
      this.loginName = loginName;
      this.passwd = passwd;
      feed(xmlKey, content, xmlQos);
   }


   /**
    * open the connection, publish the message, close the connection
    */
   protected void feed(String xmlKey, byte[] content, String xmlQos)
   {
      setUp();  // login

      publish(xmlKey, content, xmlQos); // publish message

      tearDown();  // logout
   }


   /**
    * Sets up the fixture.
    * <p />
    * Connect to xmlBlaster and login
    */
   protected void setUp()
   {
      try {
         senderConnection = glob.getXmlBlasterAccess();   // Find orb
         ConnectQos connectQos = new ConnectQos(glob, loginName, passwd);
         senderConnection.connect(connectQos, null); // Login to xmlBlaster
      }
      catch (Exception e) {
          log.severe(e.toString());
          e.printStackTrace();
      }
   }


   /**
    * Logout from xmlBlaster
    */
   protected void tearDown()
   {
      senderConnection.disconnect(null);
   }


   /**
    * Construct a message and publish it.
    */
   public void publish(String xmlKey, byte[] content, String qos)
   {
      if (log.isLoggable(Level.FINE)) log.fine("Publishing the message ...\nKEY:\n" + xmlKey + "\nCONTENT-LENGTH=" + content.length + "\nQOS:\n" + qos);

      try {
         MsgUnit msgUnit = new MsgUnit(glob, xmlKey, content, qos);
         StopWatch stop = new StopWatch();
         PublishReturnQos publish = senderConnection.publish(msgUnit);
         log.info("Success: Publishing done: " + publish.toXml() + "\n" + stop.nice());
         //log.info(ME, "Success: Publishing done, returned message oid=" + publish.getKeyOid() + stop.nice());
      } catch(XmlBlasterException e) {
         log.warning("XmlBlasterException: " + e.getMessage());
      }
   }


   /**
    * Command line usage.
    */
   private void usage()
   {
      System.out.println("----------------------------------------------------------");
      System.out.println("java org.xmlBlaster.client.feeder.PublishFile <options>");
      System.out.println("----------------------------------------------------------");
      System.out.println("Options:");
      System.out.println("   -?                  Print this message.");
      System.out.println("");
      System.out.println("   -loginName <LoginName> Your xmlBlaster login name.");
      System.out.println("   -passwd <Password>  Your xmlBlaster password.");
      System.out.println("");
      System.out.println("   -k  <XmlKeyFile>    The XmlKey for the content, or:");
      System.out.println("   -xmlKey <XmlKey>    The XML key on command line.");
      System.out.println("");
      System.out.println("   -c  <contentFile>   The content file you want to feed, or:");
      System.out.println("   -content <content>  The content on command line.");
      System.out.println("");
      System.out.println("   -q  <XmlQosFile>    The XmlQos for the message, or:");
      System.out.println("   -xmlQos <XmlQos>    The XML qos on command line.");
      System.out.println("");
      System.out.println("   These options only if you didn't specify -k or -xmlKey explicitly");
      System.out.println("   -m  <MIMEtype>      The MIME type of the message.");
      System.out.println("   -me <MIMEextendend> The extenden MIME type (for your own use).");
      //I_XmlBlasterAccess.usage();
      //log.usage();
      System.out.println("----------------------------------------------------------");
      System.out.println("Example:");
      System.out.println("java org.xmlBlaster.client.feeder.PublishFile -c Hello.xml");
      System.out.println("   The message will be named automatically 'Hello' and the MIME will be set to 'text/xml'");
      System.out.println("   and the qos (quality of service) is set to default");
      System.out.println("");
      System.out.println("java org.xmlBlaster.client.feeder.PublishFile -content \"Hello World\" -xmlKey \"<key oid='number12' contentMime='text/plain'></key>\"");
      System.out.println("");
      System.out.println("java org.xmlBlaster.client.feeder.PublishFile -xmlKey \"<key oid='__cmd:sysprop/?trace=true'/>\"");
      System.out.println("----------------------------------------------------------");
      System.out.println("");
   }


   /**
    * Invoke:  java org.xmlBlaster.client.feeder.PublishFile -c <content-file> -k <key-file> -q <qos-file> -m <mime-type>
    */
   public static void main(String args[])
   {
      try {
         new PublishFile(args);
      } catch (Throwable e) {
         e.printStackTrace();
         System.err.println(PublishFile.ME + ": " + e.toString());
         System.exit(1);
      }
   }
}

