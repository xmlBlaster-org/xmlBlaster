/*------------------------------------------------------------------------------
Name:      PublishFile.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Code for a client to publish files to xmlBlaster
Version:   $Id: PublishFile.java,v 1.14 2000/09/15 17:16:14 ruff Exp $
------------------------------------------------------------------------------*/
package org.xmlBlaster.client.feeder;

import org.xmlBlaster.client.CorbaConnection;
import org.xmlBlaster.client.PublishKeyWrapper;
import org.xmlBlaster.client.PublishQosWrapper;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.XmlBlasterProperty;
import org.xmlBlaster.engine.helper.MessageUnit;
import org.xmlBlaster.protocol.corba.clientIdl.*;

import org.xmlBlaster.util.Log;
import org.jutils.JUtilsException;
import org.jutils.init.Args;
import org.jutils.io.FileUtil;
import org.jutils.time.StopWatch;

import java.io.File;


/**
 * Publish files to xmlBlaster.
 * <br />
 * Use this as a command line tool to publish files as messages to xmlBlaster.
 * Invoke examples:<br />
 * <pre>
 *    jaco org.xmlBlaster.client.feeder.PublishFile -c &lt;content-file> -k &lt;key-file> -q &lt;qos-file> -m &lt;mime-type>
 * </pre>
 */
public class PublishFile
{
   private static final String ME = "PublishFile";
   private CorbaConnection senderConnection;
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
   public PublishFile(String[] args) throws JUtilsException
   {
      try {
         XmlBlasterProperty.init(args);
      } catch(org.jutils.JUtilsException e) {
         Log.panic(ME, e.toString());
      }
      if (Args.getArg(args, "-?") == true || Args.getArg(args, "-h") == true) {
         usage();
         return;
      }

      loginName = Args.getArg(args, "-name", ME);
      passwd = Args.getArg(args, "-passwd", "secret");

      String contentMime = Args.getArg(args, "-m", (String)null);
      String contentMimeExtended = Args.getArg(args, "-me", "1.0"); // optional

      // if passing the stuff in files:
      String contentFile = Args.getArg(args, "-c", (String)null);
      String keyFile = Args.getArg(args, "-k", (String)null);
      String qosFile = Args.getArg(args, "-q", (String)null);

      // if passing the text directly on the command line:
      String xmlKeyGiven = Args.getArg(args, "-xmlKey", (String)null);
      String xmlQosGiven = Args.getArg(args, "-xmlQos", (String)null);
      String contentGiven = Args.getArg(args, "-content", (String)null);

      String body = null;
      String exte = null;
      if (contentFile != null) {
         File contentHandle = new File(contentFile);
         String name = contentHandle.getPath();
         body = FileUtil.getBody(name);      // filename without extension
         exte = FileUtil.getExtension(name);
      }

      // Determine content ...
      byte[] content = null;
      if (contentFile != null) {
         try { content = FileUtil.readFile(contentFile); }
         catch (JUtilsException e) { }
      }
      if (content == null && contentGiven != null) {
         content = contentGiven.getBytes();
      }
      if (content == null) {
         Log.panic(ME, "File content is missing, specify content as '-c <file>' or '-content <the content text>' (get help with -?)");
      }

      // Determine XmlKey ...
      String xmlKey = null;
      if (keyFile != null) {
         try { xmlKey = FileUtil.readAsciiFile(keyFile); }
         catch (JUtilsException e) { }
      }
      if (xmlKey == null) {
         xmlKey = xmlKeyGiven;
      }
      if (contentMime == null && exte != null) {
         contentMime = FileUtil.extensionToMime(exte, null);
      }
      if (xmlKey == null && body != null) { // The filename will be the key-oid ...
         if (contentMime == null)
            Log.panic(ME, "File MIME type is unknown, specify MIME type as '-m <MIME>', for example '-m \"image/gif\"' (get help with -?)");
         PublishKeyWrapper publishKey = new PublishKeyWrapper(body, contentMime, contentMimeExtended);
         xmlKey = publishKey.toXml();  // default <key oid=......></key>
      }
      if (xmlKey == null) {
         Log.panic(ME, "XmlKey is missing, specify key as '-k <file>' or '-xmlKey <the XML key>' (get help with -?)");
      }

      // Determine XmlQoS ...
      String xmlQos = null;
      if (qosFile != null) {
         try { xmlQos = FileUtil.readAsciiFile(qosFile); }
         catch (JUtilsException e) { }
      }
      if (xmlQos == null) {
         xmlQos = xmlQosGiven;
      }
      if (xmlQos == null) {
         PublishQosWrapper publishQos = new PublishQosWrapper();
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
         senderConnection = new CorbaConnection(); // Find orb
         senderConnection.login(loginName, passwd, null); // Login to xmlBlaster
      }
      catch (Exception e) {
          Log.error(ME, e.toString());
          e.printStackTrace();
      }
   }


   /**
    * Logout from xmlBlaster
    */
   protected void tearDown()
   {
      senderConnection.logout();
   }


   /**
    * Construct a message and publish it.
    */
   public void publish(String xmlKey, byte[] content, String qos)
   {
      if (Log.TRACE) Log.trace(ME, "Publishing the message ...\nKEY:\n" + xmlKey + "\nCONTENT-LENGTH=" + content.length + "\nQOS:\n" + qos);

      MessageUnit msgUnit = new MessageUnit(xmlKey, content, qos);
      try {
         StopWatch stop = new StopWatch();
         String publishOid = senderConnection.publish(msgUnit);
         Log.info(ME, "Success: Publishing done, returned message oid=" + publishOid + stop.nice());
      } catch(XmlBlasterException e) {
         Log.warn(ME, "XmlBlasterException: " + e.reason);
      }
   }


   /**
    * Command line usage.
    */
   private void usage()
   {
      Log.plain(ME, "----------------------------------------------------------");
      Log.plain(ME, "jaco org.xmlBlaster.client.feeder.PublishFile <options>");
      Log.plain(ME, "----------------------------------------------------------");
      Log.plain(ME, "Options:");
      Log.plain(ME, "   -?                  Print this message.");
      Log.plain(ME, "");
      Log.plain(ME, "   -name <LoginName>   Your xmlBlaster login name.");
      Log.plain(ME, "   -passwd <Password>  Your xmlBlaster password.");
      Log.plain(ME, "");
      Log.plain(ME, "   -k  <XmlKeyFile>    The XmlKey for the content, or:");
      Log.plain(ME, "   -xmlKey <XmlKey>    The XML key on command line.");
      Log.plain(ME, "");
      Log.plain(ME, "   -c  <contentFile>   The content file you want to feed, or:");
      Log.plain(ME, "   -content <content>  The content on command line.");
      Log.plain(ME, "");
      Log.plain(ME, "   -q  <XmlQosFile>    The XmlQos for the message, or:");
      Log.plain(ME, "   -xmlQos <XmlQos>    The XML qos on command line.");
      Log.plain(ME, "");
      Log.plain(ME, "   These options only if you didn't specify -k or -xmlKey explicitly");
      Log.plain(ME, "   -m  <MIMEtype>      The MIME type of the message.");
      Log.plain(ME, "   -me <MIMEextendend> The extenden MIME type (for your own use).");
      //CorbaConnection.usage();
      //Log.usage();
      Log.plain(ME, "----------------------------------------------------------");
      Log.plain(ME, "Example:");
      Log.plain(ME, "jaco org.xmlBlaster.client.feeder.PublishFile -c Hello.xml");
      Log.plain(ME, "   The message will be named automatically 'Hello' and the MIME will be set to 'text/xml'");
      Log.plain(ME, "   and the qos (quality of service) is set to default");
      Log.plain(ME, "");
      Log.plain(ME, "jaco org.xmlBlaster.client.feeder.PublishFile -content \"Hello World\" -xmlKey \"<key oid='number12' contentMime='text/plain'></key>\"");
      Log.plain(ME, "----------------------------------------------------------");
      Log.plain(ME, "");
   }


   /**
    * Invoke:  jaco org.xmlBlaster.client.feeder.PublishFile -c <content-file> -k <key-file> -q <qos-file> -m <mime-type>
    */
   public static void main(String args[])
   {
      try {
         PublishFile publishFile = new PublishFile(args);
      } catch (Throwable e) {
         e.printStackTrace();
         Log.panic(PublishFile.ME, e.toString());
      }
      Log.exit(PublishFile.ME, "Good bye");
   }
}

