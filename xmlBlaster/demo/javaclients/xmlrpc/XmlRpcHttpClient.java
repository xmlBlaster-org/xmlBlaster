/*------------------------------------------------------------------------------
Name:      XmlRpcHttpClient.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Code to post a xml-rpc message thru the HTTP protocol
Version:   $Id: XmlRpcHttpClient.java,v 1.20 2003/03/24 16:12:55 ruff Exp $
Author:    Michele Laghi (laghi@swissinfo.org)
------------------------------------------------------------------------------*/

package javaclients.xmlrpc;

import java.io.*;
import java.net.URL;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.ProtocolException;
import org.jutils.log.LogChannel;
import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.protocol.xmlrpc.*;
import org.xmlBlaster.client.qos.ConnectQos;
import org.xmlBlaster.client.I_XmlBlasterAccess;

import org.xmlBlaster.client.key.PublishKey;
import org.xmlBlaster.client.key.SubscribeKey;
import org.xmlBlaster.util.MsgUnit;

/**
 * Demo showing how to implement a client which connects to xmlBlaster via
 * xml-rpc. 
 * Calls are made through the I_XmlBlasterAccess client helper class.
 *
 * When using this class as a client, the xmlrpc
 * protocol is completely transparent, even the Callback server is created for you.
 * <p />
 * Invoke example (this client forces XML-RPC as protocol hard coded):
 * <pre>
 *    java -cp lib/xmlBlaster.jar javaclients.xmlrpc.XmlRpcHttpClient
 * </pre>
 *
 * <p />
 * NOTE:  Any java client using I_XmlBlasterAccess helper class will switch
 *        to XML-RPC if the command line parameter -client.protocol is specified as follows:
 * <br />
 * <pre>
 *    java -cp lib/xmlBlaster.jar javaclients.ClientSub -client.protocol XML-RPC
 * </pre>
 *
 * @author "Michele Laghi" <laghi@swissinfo.org>
 * @see org.xmlBlaster.client.protocol.xmlrpc.XmlRpcConnection
 */
public class XmlRpcHttpClient
{
   private static final String ME = "XmlRpcHttpClient";
   private LogChannel log = null;


   /**
    * Constructor.
    */
   public XmlRpcHttpClient ()
   {
   }


   /**
    * Here come the methods to invoke if you want to use the xml-rpc
    * protocol transparently ...
    * <br />
    */
   private void testWithHelperClasses(Global glob)
   {
      this.log = glob.getLog("client");
      try {
         // force XML-RPC protocol:
         glob.getProperty().set("client.protocol", "XML-RPC");
         
         I_XmlBlasterAccess client = glob.getXmlBlasterAccess();
         
         log.info(ME, "Going to invoke xmlBlaster using XmlRpc-I_XmlBlasterAccess");
         String sessionId = "Session1";
         ConnectQos loginQos = new ConnectQos(glob); // creates "<qos></qos>" string

         client.login("LunaMia", "silence", loginQos, null);
         log.info(ME, "Login successful");

         String contentString = "This is a simple Test Message for the xml-rpc Protocol";
         byte[] content = contentString.getBytes();

         PublishKey xmlKey = new PublishKey(glob, "", "text/plain", null);

         MsgUnit msgUnit = new MsgUnit(xmlKey.toXml(), content, "<qos><forceUpdate /></qos>");
         client.publish(msgUnit);
         log.info(ME, "Published a message");

         client.disconnect(null);
      }
      catch (XmlBlasterException ex) {
         log.error(ME, "exception: " + ex.toString());
      }
      catch (Throwable ex1) {
         System.err.println("exception:"  + ex1);
      }
   }


   /**
    * Only for testing purposes.
    * <pre>
    * java javaclients.xmlrpc.XmlRpcHttpClient < demo.xml -trace true
    * </pre>
    */
   public static void main (String args[])
   {
      final String ME = "XmlRpcHttpClient";

      Global glob = new Global();
      if (glob.init(args) != 0) {
         usage();
         System.exit(1);
      }

      XmlRpcHttpClient client = new XmlRpcHttpClient();

      client.testWithHelperClasses(glob);
   }


   /**
    * Command line usage.
    */
   private static void usage()
   {
      System.out.println("----------------------------------------------------------");
      System.out.println("java javaclients.xmlrpc.XmlRpcHttpClient < demo.xml <options>");
      System.out.println("----------------------------------------------------------");
      System.out.println(glob.usage());
      System.out.println(Global.instance().usage());
      System.out.println("----------------------------------------------------------");
      System.out.println("");
   }
}


