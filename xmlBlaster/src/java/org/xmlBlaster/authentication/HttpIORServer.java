/*------------------------------------------------------------------------------
Name:      HttpIORServer.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Delivering the Authentication Service IOR over HTTP
Version:   $Id: HttpIORServer.java,v 1.23 2002/09/11 14:48:37 ruff Exp $
------------------------------------------------------------------------------*/
package org.xmlBlaster.authentication;

import org.jutils.log.LogChannel;
import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.UriAuthority;
import org.xmlBlaster.util.Uri;
import java.util.*;
import java.net.*;
import java.io.*;


/**
 * Delivering the Authentication Service IOR over HTTP.
 * <p />
 * This tiny HTTP server is always running in the xmlBlaster server on the
 * default port 3412.<br />
 * Clients may access through this port the AuthServer IOR if they
 * don't want to use a naming service
 * <p />
 * You may specify on command line -port <port> and -hostname <host>
 * to choose another port or to choose a server IP address on
 * multi homed hosts.
 * <p />
 * Change code to be a generic HTTP server, not only for CORBA bootstrapping
 * @version $Revision: 1.23 $
 * @author $Author: ruff $
 */
public class HttpIORServer extends Thread
{
   private String ME = "HttpServer";
   private final Global glob;
   private final LogChannel log;
   private String ip_addr = null;
   private final int HTTP_PORT;
   private ServerSocket listen = null;
   private boolean running = true;

   private Hashtable knownRequests = new Hashtable();

   /**
    * Create a little web server.
    * <p />
    * @param ip_addr The string representation like "192.168.1.1", useful if multihomed computer
    * @param port    The port where we publish the IOR
    */
   public HttpIORServer(Global glob, String ip_addr, int port)
   {
      this.glob = glob;
      this.log = glob.getLog("protocol");
      this.ip_addr = ip_addr;
      this.HTTP_PORT = port;
      this.ME +=  this.glob.getLogPraefixDashed();
      if (this.HTTP_PORT <= 0) {
         if (log.CALL) log.call(ME, "Internal HttpServer not started, as port is " + this.HTTP_PORT);
         return;
      }
      if (log.CALL) log.call(ME, "Creating new HttpServer on IP=" + this.ip_addr + " port=" + this.HTTP_PORT);
      start();
   }

   /**
    * If you want to provide some information over http, register it here. 
    * @param urlPath The access path which the client uses to access your data
    * @param data The data you want to deliver to the client e.g. the CORBA IOR string
    */
   public void registerRequest(String urlPath, String data)
   {
      if (log.TRACE) log.trace(ME, "Registering urlPath: " + urlPath + "=" + data);
      knownRequests.put(urlPath.trim(), data);
   }

   /**
    * If you want to provide some information over http, register it here. 
    * @param urlPath The access path which the client uses to access your data
    * @param data The data you want to deliver to the client e.g. the CORBA IOR string
    */
   public void removeRequest(String urlPath)
   {
      knownRequests.remove(urlPath.trim());
   }

   /**
    */
   public void run()
   {
      try {
         int backlog = glob.getProperty().get("http.backlog", 50); // queue for max 50 incoming connection request
         listen = new ServerSocket(HTTP_PORT, backlog, InetAddress.getByName(ip_addr));
         while (running) {
            Socket accept = listen.accept();
            //log.trace(ME, "New incoming request on port=" + HTTP_PORT + " ...");
            if (!running) {
               log.info(ME, "Closing http server port=" + HTTP_PORT + ".");
               break;
            }
            HandleRequest hh = new HandleRequest(glob, log, accept, knownRequests);
         }
      }
      catch (java.net.UnknownHostException e) {
         log.error(ME, "HTTP server problem, IP address '" + ip_addr + "' is invalid: " + e.toString());
      }
      catch (java.net.BindException e) {
         log.error(ME, "HTTP server problem, port " + ip_addr + ":" + HTTP_PORT + " is not available: " + e.toString());
      }
      catch (java.net.SocketException e) {
         log.info(ME, "Socket " + ip_addr + ":" + HTTP_PORT + " closed successfully: " + e.toString());
      }
      catch (IOException e) {
         log.error(ME, "HTTP server problem on " + ip_addr + ":" + HTTP_PORT + ": " + e.toString());
      }

      if (listen != null) {
         try { listen.close(); } catch (java.io.IOException e) { log.warn(ME, "listen.close()" + e.toString()); }
         listen = null;
      }
   }


   /**
    * Close the listener port
    */
   public void shutdown()// throws IOException
   {
      if (log.CALL) log.call(ME, "Entering shutdown");
      running = false;

      boolean closeHack = true;
      if (listen != null && closeHack) {
         // On some JDKs, listen.close() is not immediate (has a delay for about 1 sec.)
         // force closing by invoking server with this temporary client:
         try {
            java.net.Socket socket = new Socket(listen.getInetAddress(), HTTP_PORT);
            socket.close();
         } catch (java.io.IOException e) {
            log.warn(ME, "shutdown problem: " + e.toString());
         }
      }

      try {
         if (listen != null) {
            listen.close();
            listen = null;
         }
      } catch (java.io.IOException e) {
         log.warn(ME, "shutdown problem: " + e.toString());
      }
   }
}


/**
 * Handles a request from a client, delivering the AuthServer IOR
 */
class HandleRequest extends Thread
{
   private final String ME;
   private final Global glob;
   private final LogChannel log;
   private final Socket sock;
   private final Hashtable knownRequests;
   private final String CRLF = "\r\n";
   private final String VERSION = "1.0";


   /**
    */
   public HandleRequest(Global glob, LogChannel log, Socket sock, Hashtable knownRequests)
   {
      this.glob = glob;
      this.ME = "HandleRequest" + this.glob.getLogPraefixDashed();
      this.log = log;
      this.sock = sock;
      this.knownRequests = knownRequests;
      start();
   }

   /**
    * TODO: The HTTP/1.1 spec states that we should return the "Date:" header as well.
    * <p />
    * Test with "telnet <host> 3412"<br />
    *   GET /AuthenticationService.ior HTTP/1.0
    */
   public void run()
   {
      if (log.CALL) log.call(ME, "Handling client request, accessing AuthServer IOR ...");
      BufferedReader iStream = null;
      DataOutputStream oStream = null;
      String clientRequest = "";
      boolean first = true;
      try {
         iStream = new BufferedReader(new InputStreamReader(sock.getInputStream()));
         oStream = new DataOutputStream(sock.getOutputStream());

         clientRequest = iStream.readLine();
         iStream.readLine(); // "\r\n"

         if (log.CALL) log.call(ME, "Request from client " + getSocketInfo());

         if (clientRequest == null) {
            String info = "Empty request ignored " + getSocketInfo();
            errorResponse(oStream, "HTTP/1.1 400 Bad Request", null, true, info);
            log.warn(ME, info);
            return;
         }

         first = false;
         if (log.TRACE) log.trace(ME, "Handling client request '" + clientRequest + "' ...");

         StringTokenizer toks = new StringTokenizer(clientRequest);
         if (toks.countTokens() != 3) {
            String info = "Wrong syntax in client request: '" + clientRequest + "', closing " + getSocketInfo() + " connection.";
            errorResponse(oStream, "HTTP/1.1 400 Bad Request", null, true, info);
            log.warn(ME, info);
            return;
         }

         String method = toks.nextToken();   // "GET"
         String resource = toks.nextToken(); // "/AuthenticationService.ior"
         String version = toks.nextToken();  // "HTTP/1.0"

         if (false) { // TEST ONLY:
            Uri uri = null;
            try {
               // TODO: use UriAuthority to parse the request and forward it to CommandManager
               //UriAuthority uriAuthority = new UriAuthority(resource);

               // To test a telnet with
               // GET http://joe:mypasswd@develop:3412/admin/?key=XX HTTP/1.0

               // !! From browser we only get "/admin/?key=XX" -> 'joe:mypasswd' is not delivered!!

               uri = new Uri(glob, resource);
               if (log.CALL) log.call(ME, "Request is" + uri.toXml());
            }
            catch (XmlBlasterException e) {
               String info = getSocketInfo() + ": " + e.toString();
               log.call(ME, info);
               errorResponse(oStream, "HTTP/1.1 400 Bad Request", null, true, info);
               return;
            }
            finally {
               if (log.CALL) {
                  while (true) {
                     String req = iStream.readLine();
                     if (req == null)
                        break;
                     if (log.CALL) log.call(ME, req);
                  }
               }
            }
         }

         // RFC 2068 enforces minimum implementation GET and HEAD
         if (!method.equalsIgnoreCase("GET") && !method.equalsIgnoreCase("HEAD")) {
            String info = "Invalid method in client " + getSocketInfo() + " request: '" + clientRequest + "'";
            errorResponse(oStream, "HTTP/1.1 501 Method Not Implemented", "Allow: GET", true, info);
            log.warn(ME, info);
            return;
         }

         String responseStr = (String)knownRequests.get(resource.trim());

         if (responseStr == null) {
            String info = "Ignoring unknown data '" + resource.trim() + "' from client " + getSocketInfo() + " request: '" + clientRequest + "'";
            errorResponse(oStream, "HTTP/1.1 404 Not Found", null, true, info);
            log.warn(ME, info);
            return;
         }

         // java.net.HttpURLConnection.HTTP_OK:
         errorResponse(oStream, "HTTP/1.1 200 OK", null, false, null);
         String length = "Content-Length: " + responseStr.length();
         oStream.write((length+CRLF).getBytes());
         //oStream.write(("Transfer-Encoding: chunked"+CRLF).getBytes()); // java.io.IOException: Bogus chunk size
         oStream.write(("Content-Type: text/plain; charset=iso-8859-1"+CRLF).getBytes());
         if (!method.equalsIgnoreCase("HEAD")) {
            oStream.write(CRLF.getBytes());
            oStream.write(responseStr.getBytes());
         }
         oStream.flush();
      }
      catch (IOException e) {
         if (clientRequest == null && first) {
            if (log.TRACE) log.trace(ME, "Ignoring connect/disconnect attempt, probably a xmlBlaster client detecting its IP to use");
         } else {
            log.error(ME, "Problems with sending response for '" + clientRequest + "' to client " + getSocketInfo() + ": " + e.toString());
         }
         // throw new XmlBlasterException(ME, "Problems with sending IOR to client: " + e.toString());
      }
      finally {
         try { if (iStream != null) iStream.close(); } catch (IOException e) { }
         try { if (oStream != null) oStream.close(); } catch (IOException e) { }
         try { sock.close();  } catch (IOException e) { }
      }
   }


   private void errorResponse(DataOutputStream oStream, String code, String extra, boolean body, String info) throws IOException
   {
      oStream.write((code+CRLF).getBytes());
      oStream.write(("Server: XmlBlaster HttpServer/"+VERSION+CRLF).getBytes());
      if (extra != null) oStream.write((extra+CRLF).getBytes());
      oStream.write(("Connection: close"+CRLF).getBytes());
      if (body) {
        oStream.write((CRLF+"<html><head><title>"+code+"</title></head><body>" + 
                      "<h2>XmlBlaster HTTP server " + VERSION + "</h2>" +
                      "<p>" + code + "</p>" +
                      "<p>" + info + "</p>" +
                      "<p><a href='" + glob.getProperty().get("http.info.url", "http://www.xmlBlaster.org") + "'>Info</a></p>" +
                      "</body></html>").getBytes());
      }
   }

   private String getSocketInfo() {
      StringBuffer sb = new StringBuffer(196);
      if (sock == null)
         return "";
      sb.append(sock.getInetAddress().getHostAddress());
      sb.append(":").append(sock.getPort());
      sb.append(" -> ");
      sb.append(sock.getLocalAddress().getHostAddress());
      sb.append(":").append(sock.getLocalPort());
      return sb.toString();
   }
}
