/*------------------------------------------------------------------------------
Name:      HttpIORServer.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Delivering the Authentication Service IOR over HTTP
Version:   $Id: HttpIORServer.java,v 1.12 2001/11/19 15:21:44 ruff Exp $
------------------------------------------------------------------------------*/
package org.xmlBlaster.authentication;

import org.xmlBlaster.util.Log;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.XmlBlasterProperty;
import java.util.*;
import java.net.*;
import java.io.*;


/**
 * Delivering the Authentication Service IOR over HTTP.
 * <p />
 * This little HTTP server is always running in the xmlBlaster on the
 * default port 7609.<br />
 * Clients may access through this port the AuthServer IOR if they
 * don't want to use a naming service
 * <p />
 * You may specify on command line -iorPort <port> and -iorHost <host>
 * to choose another port or to choose a server IP address on
 * multi homed hosts.
 *
 * @version $Revision: 1.12 $
 * @author $Author: ruff $
 */
public class HttpIORServer extends Thread
{
   private String ME = "HttpIORServer";
   private String ip_addr = null;
   private final int HTTP_PORT;
   private String ior = null;
   private ServerSocket listen = null;
   private boolean running = true;


   /**
    * Create a little web server.
    * <p />
    * @param ip_addr The string representation like "192.168.1.1", useful if multihomed computer
    * @param port    The port where we publish the IOR
    * @param iorStr  The IOR string of the ProxyService
    */
   public HttpIORServer(String ip_addr, int port, String ior)
   {
      this.ip_addr = ip_addr;
      this.ior = ior;
      this.HTTP_PORT = port;
      if (Log.CALL) Log.call(ME, "Creating new HttpIORServer");
      start();
   }


   /**
    */
   public void run()
   {
      try {
         int backlog = XmlBlasterProperty.get("iorBacklog", 50); // queue for max 50 incoming connection request
         listen = new ServerSocket(HTTP_PORT, backlog, InetAddress.getByName(ip_addr));
         while (running) {
            Socket accept = listen.accept();
            //Log.trace(ME, "New incoming request on port=" + HTTP_PORT + " ...");
            if (!running) {
               Log.info(ME, "Closing http server port=" + HTTP_PORT + ".");
               break;
            }
            HandleRequest hh = new HandleRequest(accept, ior);
         }
      }
      catch (java.net.UnknownHostException e) {
         Log.error(ME, "HTTP server problem, IP address '" + ip_addr + "' is invalid: " + e.toString());
      }
      catch (java.net.BindException e) {
         Log.error(ME, "HTTP server problem, port " + ip_addr + ":" + HTTP_PORT + " is not available: " + e.toString());
      }
      catch (java.net.SocketException e) {
         Log.info(ME, "Socket " + ip_addr + ":" + HTTP_PORT + " closed successfully: " + e.toString());
      }
      catch (IOException e) {
         Log.error(ME, "HTTP server problem on " + ip_addr + ":" + HTTP_PORT + ": " + e.toString());
      }

      if (listen != null) {
         try { listen.close(); } catch (java.io.IOException e) { Log.warn(ME, "listen.close()" + e.toString()); }
         listen = null;
      }
   }


   /**
    * Close the listener port
    */
   public void shutdown()// throws IOException
   {
      if (Log.CALL) Log.call(ME, "Entering shutdown");
      running = false;

      boolean closeHack = true;
      if (listen != null && closeHack) {
         // On some JDKs, listen.close() is not immediate (has a delay for about 1 sec.)
         // force closing by invoking server with this temporary client:
         try {
            java.net.Socket socket = new Socket(listen.getInetAddress(), HTTP_PORT);
            socket.close();
         } catch (java.io.IOException e) {
            Log.warn(ME, "shutdown problem: " + e.toString());
         }
      }

      try {
         if (listen != null) {
            listen.close();
            listen = null;
         }
      } catch (java.io.IOException e) {
         Log.warn(ME, "shutdown problem: " + e.toString());
      }
   }
}


/**
 * Handles a request from a client, delivering the AuthServer IOR
 */
class HandleRequest extends Thread
{
   private String ME = "HandleRequest";
   private final Socket sock;
   private final String ior;
   private final String CRLF = "\r\n";


   /**
    */
   public HandleRequest(Socket sock, String iorStr)
   {
      this.sock = sock;
      this.ior = iorStr;
      start();
   }

   /**
    * TODO: The HTTP/1.1 spec states that we should return the "Date:" header as well.
    * <p />
    * Test with "telnet <host> 7609"<br />
    *   GET /AuthenticationService.ior HTTP/1.0
    */
   public void run()
   {
      if (Log.CALL) Log.call(ME, "Handling client request, accessing AuthServer IOR ...");
      BufferedReader iStream = null;
      DataOutputStream oStream = null;
      try {
         iStream = new BufferedReader(new InputStreamReader(sock.getInputStream()));
         oStream = new DataOutputStream(sock.getOutputStream());

         String clientRequest = iStream.readLine();
         iStream.readLine(); // "\r\n"

         if (clientRequest == null) {
            errorResponse(oStream, "HTTP/1.1 400 Bad Request", null, true);
            Log.warn(ME, "Empty client request");
            return;
         }

         if (Log.TRACE) Log.trace(ME, "Handling client request '" + clientRequest + "' ...");

         StringTokenizer toks = new StringTokenizer(clientRequest);
         if (toks.countTokens() != 3) {
            errorResponse(oStream, "HTTP/1.1 400 Bad Request", null, true);
            Log.warn(ME, "Wrong syntax in client request: '" + clientRequest + "'");
            return;
         }

         String method = toks.nextToken();   // "GET"
         String resource = toks.nextToken(); // "/AuthenticationService.ior"
         String version = toks.nextToken();  // "HTTP/1.0"

         // RFC 2068 enforces minimum implementation GET and HEAD
         if (!method.equalsIgnoreCase("GET") && !method.equalsIgnoreCase("HEAD")) {
            errorResponse(oStream, "HTTP/1.1 501 Method Not Implemented", "Allow: GET", true);
            Log.warn(ME, "Invalid method in client request: '" + clientRequest + "'");
            return;
         }

         if (!resource.equalsIgnoreCase("/AuthenticationService.ior")) {
            errorResponse(oStream, "HTTP/1.1 404 Not Found", null, true);
            Log.warn(ME, "Ignoring unknown data from client request: '" + clientRequest + "'");
            return;
         }

         // java.net.HttpURLConnection.HTTP_OK:
         errorResponse(oStream, "HTTP/1.1 200 OK", null, false);
         String length = "Content-Length: " + ior.length();
         oStream.write((length+CRLF).getBytes());
         //oStream.write(("Transfer-Encoding: chunked"+CRLF).getBytes()); // java.io.IOException: Bogus chunk size
         oStream.write(("Content-Type: text/plain; charset=iso-8859-1"+CRLF).getBytes());
         if (!method.equalsIgnoreCase("HEAD")) {
            oStream.write(CRLF.getBytes());
            oStream.write(ior.getBytes());
         }
         oStream.flush();
      }
      catch (IOException e) {
         Log.error(ME, "Problems with sending IOR to client: " + e.toString());
         // throw new XmlBlasterException(ME, "Problems with sending IOR to client: " + e.toString());
      }
      finally {
         try { if (iStream != null) iStream.close(); } catch (IOException e) { }
         try { if (oStream != null) oStream.close(); } catch (IOException e) { }
         try { sock.close();  } catch (IOException e) { }
      }
   }


   private void errorResponse(DataOutputStream oStream, String code, String extra, boolean body) throws IOException
   {
      oStream.write((code+CRLF).getBytes());
      oStream.write(("Server: XmlBlaster HttpIORServer/1.0"+CRLF).getBytes());
      if (extra != null) oStream.write((extra+CRLF).getBytes());
      oStream.write(("Connection: close"+CRLF).getBytes());
      if (body) oStream.write((CRLF+"<html><head><title>"+code+"</title></head><body>"+code+"</body></html>").getBytes());
   }
}
