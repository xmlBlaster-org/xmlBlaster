/*------------------------------------------------------------------------------
Name:      XmlRpcHttpClientRaw.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Code to post a xml-rpc message thru the HTTP protocol
Version:   $Id: XmlRpcHttpClientRaw.java,v 1.1 2001/02/14 12:15:41 ruff Exp $
Author:    "Michele Laghi" <michele.laghi@attglobal.net>
------------------------------------------------------------------------------*/

package javaclients.xmlrpc;

import java.io.*;
import java.net.URL;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.ProtocolException;
import org.xmlBlaster.util.Log;
import org.xmlBlaster.util.XmlBlasterProperty;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.protocol.xmlrpc.*;
import org.xmlBlaster.client.protocol.XmlBlasterConnection;

import org.xmlBlaster.client.PublishKeyWrapper;
import org.xmlBlaster.client.SubscribeKeyWrapper;
import org.xmlBlaster.engine.helper.MessageUnit;

/**
 * Raw demo showing how to implement a client which connects to xmlBlaster via
 * xml-rpc.
 * <br />
 * This is a demo showing what happens 'low level', used with 'demo.xml',
 * sending the method calls directly in xml style.
 * <br />
 * You must be careful when sending xml strings.
 * The "<" character inside these strings must be
 * converted to "&lt;", otherwise the xmlrpc parser will erroneously try to
 * parse xmlBlaster specific stuff, resulting in a parsing error (see demo.xml
 * on this directory).
 * <p/>
 * This demo first reads "raw" procedure calls from the standard input.
 *
 * @author "Michele Laghi" <michele.laghi@attglobal.net>
 * @see org.xmlBlaster.client.protocol.xmlrpc.XmlRpcConnection
 */
public class XmlRpcHttpClientRaw
{
   private static final String ME = "XmlRpcHttpClientRaw";


   /**
    * Constructor.
    */
   public XmlRpcHttpClientRaw ()
   {
   }


   /**
    * executes a 'raw' xml-rpc method call through the HTTP protocol.
    * @param inputString the xml-rpc string to execute (xml literal)
    * @return a string containing the result of the remote procedure call.
    */
   public String execute(String urlStr, String inputString) throws XmlBlasterException
   {
      StringBuffer ret = null;
      try {
         // connect to the server
         URL url = new URL(urlStr);
         HttpURLConnection urlConnection = (HttpURLConnection)url.openConnection();

         // send a POST
         urlConnection.setRequestMethod("POST");
         urlConnection.setDoOutput(true);
         OutputStream outStream = urlConnection.getOutputStream();
         byte[] conversionHelper = inputString.getBytes();
         outStream.write(conversionHelper);
         outStream.flush();

         // read the answer of the server and store it in a string
         ret = new StringBuffer();
         InputStream inStream = urlConnection.getInputStream();
         int bytes = 0, deltaBytes = 0;
         int maxBytes = urlConnection.getContentLength();

         //         while ( (bytes = inStream.available()) > 0) {
         while (bytes < maxBytes) {
            deltaBytes = inStream.available();
            byte buffer[] = new byte[deltaBytes];
            inStream.read(buffer);
            bytes += deltaBytes;
            ret.append(new String(buffer));
         }
      }

      catch (MalformedURLException ex1) {
         Log.error(ME, ex1.toString());
         throw new XmlBlasterException(ME, ex1.toString());
      }
      catch (IOException ex2) {
         Log.error(ME, ex2.toString());
         throw new XmlBlasterException(ME, ex2.toString());
      }

      return ret.toString();
   }


   /**
    * Invokes the methods as described in demo.xml
    */
   private void testRaw(String[] args)
   {
      try {
         String host = XmlBlasterProperty.get("xmlrpc.host", "localhost");
         int port = XmlBlasterProperty.get("xmlrpc.port", 8080);
         int cb_port = XmlBlasterProperty.get("xmlrpc.cb_port", 8081);
         String urlStr = "http://" + host + ":" + port;

         Log.info(ME, "Connected to xmlBlaster using XML-RPC");

         // reads from the standard input stream. Ignores the lines until the
         // first comment line containing the word COMMAND

         BufferedReader reader = new BufferedReader( new InputStreamReader(System.in) );
         String line = "";

         String cmd = "";
         boolean hasStarted = false;
         Log.info(ME, "Processing data from stdin ...");
         while ( (line = reader.readLine()) != null) {
            if (line.indexOf("COMMAND") == -1) {
               if (hasStarted) cmd += line + "\n";
            }

            else {
               if (cmd.length() > 0) {
                  System.out.println("THE COMMAND IS: " + cmd + "\nEND OF COMMAND");
                  System.out.println(execute(urlStr, cmd));
               }
               cmd = "";
               hasStarted = true;
            }

         }
      }
      catch (XmlBlasterException ex) {
         Log.error(ME, "exception: " + ex);
      }
      catch (IOException ex1) {
         Log.error(ME, "exception:"  + ex1);
      }
   }


   /**
    * Only for testing purposes.
    * <pre>
    * java javaclients.xmlrpc.XmlRpcHttpClientRaw < demo.xml -trace true
    * </pre>
    */
   public static void main (String args[])
   {
      final String ME = "XmlRpcHttpClientRaw";
      boolean showUsage = false;

      try {
         if (XmlBlasterProperty.init(args)) {
            usage();
            Log.exit(ME, "");
         }
      } catch(org.jutils.JUtilsException e) {
         usage();
         Log.panic(ME, e.toString());
      }

      XmlRpcHttpClientRaw client = new XmlRpcHttpClientRaw();

      client.testRaw(args);
   }


   /**
    * Command line usage.
    */
   private static void usage()
   {
      Log.plain(ME, "----------------------------------------------------------");
      Log.plain(ME, "java javaclients.xmlrpc.XmlRpcHttpClientRaw < demo.xml <options>");
      Log.plain(ME, "----------------------------------------------------------");
      Log.plain(ME, "   -h                  Show the complete usage.");
      Log.plain(ME, "   -xmlrpc.host        The XML-RPC web server host [localhost].");
      Log.plain(ME, "   -xmlrpc.port        The XML-RPC web server port [8080].");
      //Log.plain(ME, "   -xmlrpc.cb_host     My XML-RPC callback web server host (e.g. for multi homed hosts) [localhost].");
      //Log.plain(ME, "   -xmlrpc.cb_port     My XML-RPC callback web server port [8081].");
      Log.usage();
      Log.plain(ME, "----------------------------------------------------------");
      Log.plain(ME, "");
   }
}


