/*------------------------------------------------------------------------------
Name:      XmlRpcHttpClient.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Code to post a xml-rpc message thru the HTTP protocol
Version:   $Id: XmlRpcHttpClient.java,v 1.13 2002/05/11 19:39:14 ruff Exp $
Author:    "Michele Laghi" <michele.laghi@attglobal.net>
------------------------------------------------------------------------------*/

package javaclients.xmlrpc;

import java.io.*;
import java.net.URL;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.ProtocolException;
import org.xmlBlaster.util.Log;
import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.protocol.xmlrpc.*;
import org.xmlBlaster.util.ConnectQos;
import org.xmlBlaster.client.protocol.XmlBlasterConnection;

import org.xmlBlaster.client.PublishKeyWrapper;
import org.xmlBlaster.client.SubscribeKeyWrapper;
import org.xmlBlaster.engine.helper.MessageUnit;

/**
 * Demo showing how to implement a client which connects to xmlBlaster via
 * xml-rpc. 
 * Calls are made through the XmlBlasterConnection client helper class.
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
 * NOTE:  Any java client using XmlBlasterConnection helper class will switch
 *        to XML-RPC if the command line parameter -client.protocol is specified as follows:
 * <br />
 * <pre>
 *    java -cp lib/xmlBlaster.jar javaclients.ClientSub -client.protocol XML-RPC
 * </pre>
 *
 * @author "Michele Laghi" <michele.laghi@attglobal.net>
 * @see org.xmlBlaster.client.protocol.xmlrpc.XmlRpcConnection
 */
public class XmlRpcHttpClient
{
   private static final String ME = "XmlRpcHttpClient";


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
      try {
         // force XML-RPC protocol:
         glob.getProperty().set("client.protocol", "XML-RPC");
         
         XmlBlasterConnection client = new XmlBlasterConnection(glob);
         
         Log.info(ME, "Going to invoke xmlBlaster using XmlRpc-XmlBlasterConnection");
         String sessionId = "Session1";
         ConnectQos loginQos = new ConnectQos(glob); // creates "<qos></qos>" string

         client.login("LunaMia", "silence", loginQos, null);
         Log.info(ME, "Login successful");

         String contentString = "This is a simple Test Message for the xml-rpc Protocol";
         byte[] content = contentString.getBytes();

         PublishKeyWrapper xmlKey = new PublishKeyWrapper("", "text/plain", null);

         MessageUnit msgUnit = new MessageUnit(xmlKey.toXml(), content, "<qos><forceUpdate /></qos>");
         String publishOid = client.publish(msgUnit);
         Log.info(ME, "Published a message");

         client.logout();
      }
      catch (XmlBlasterException ex) {
         Log.error(ME, "exception: " + ex.toString());
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
         Log.exit(ME, "");
      }

      XmlRpcHttpClient client = new XmlRpcHttpClient();

      client.testWithHelperClasses(glob);

      Log.exit(ME, "EXIT NOW....");
   }


   /**
    * Command line usage.
    */
   private static void usage()
   {
      Log.plain(ME, "----------------------------------------------------------");
      Log.plain(ME, "java javaclients.xmlrpc.XmlRpcHttpClient < demo.xml <options>");
      Log.plain(ME, "----------------------------------------------------------");
      XmlBlasterConnection.usage();
      Log.usage();
      Log.plain(ME, "----------------------------------------------------------");
      Log.plain(ME, "");
   }
}


