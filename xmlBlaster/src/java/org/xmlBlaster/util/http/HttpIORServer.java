/*------------------------------------------------------------------------------
Name:      HttpIORServer.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Delivering the Authentication Service IOR over HTTP
Version:   $Id$
------------------------------------------------------------------------------*/
package org.xmlBlaster.util.http;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.Map;
import java.util.StringTokenizer;
import java.util.TreeMap;

import org.jutils.log.LogChannel;
import org.xmlBlaster.util.Global;


/**
 * Delivering the Authentication Service IOR over HTTP.
 * <p />
 * This tiny HTTP server is always running in the xmlBlaster server on the
 * default bootstrapPort 3412.<br />
 * Clients may access through this bootstrap port the AuthServer IOR if they
 * don't want to use a naming service
 * <p />
 * You may specify on command line -bootstrapPort <port> and -bootstrapHostname <host>
 * to choose another bootstrap port or to choose a server IP address on
 * multi homed hosts.
 * <p />
 * Change code to be a generic HTTP server, not only for CORBA bootstrapping
 * @version $Revision: 1.31 $
 * @author $Author$
 */
public class HttpIORServer extends Thread implements I_HttpRequest
{
   private String ME = "HttpServer";
   private final Global glob;
   private final LogChannel log;
   private String ip_addr = null;
   private final int HTTP_PORT;
   private ServerSocket listen = null;
   private boolean running = true;

   private String icoMimeType = "image/ico";
   private String icoRequestFile = "favicon.ico";
   private String icoRequestUrlPath = "/"+icoRequestFile;

   private String fishMimeType = "image/gif";
   private String fishRequestFile = "rainbowfish200.gif";
   private String fishRequestUrlPath = "/"+fishRequestFile;

   private Hashtable knownRequests = new Hashtable();

   /**
    * Create a little web server.
    * <p />
    * @param ip_addr The string representation like "192.168.1.1", useful if multihomed computer
    * @param bootstrapPort    The bootstrap port where we publish the IOR
    */
   public HttpIORServer(Global glob, String ip_addr, int port)
   {
      super("XmlBlaster.HttpIORServer");
      this.glob = glob;
      this.log = glob.getLog("protocol");
      this.ip_addr = ip_addr;
      this.HTTP_PORT = port;
      this.ME +=  this.glob.getLogPrefixDashed();
      if (this.HTTP_PORT <= 0) {
         if (log.CALL) log.call(ME, "Internal HttpServer not started, as -bootstrapPort is " + this.HTTP_PORT);
         return;
      }

      registerRequest(icoRequestUrlPath, this);
      registerRequest(fishRequestUrlPath, this);

      if (log.CALL) log.call(ME, "Creating new HttpServer on IP=" + this.ip_addr + " bootstrap port=" + this.HTTP_PORT);
      setDaemon(true);
      start();
   }

   /**
    * If you want to provide some information over http, register it here. 
    * @param urlPath The access path which the client uses to access your data, for example "/AuthenticationService.ior"
    * @param data The data you want to deliver to the client e.g. the CORBA IOR string
    */
   public void registerRequest(String urlPath, String data)
   {
      if (log.TRACE) log.trace(ME, "Registering urlPath: " + urlPath + "=" + data);
      knownRequests.put(urlPath.trim(), data);
   }

   /**
    * If you want to provide some information over http, register it here. 
    * @param urlPath The access path which the client uses to access your data,
    *        for example "/monitor/index.html" or "/favicon.ico"
    * @param data The data you want to deliver to the client e.g. the CORBA IOR string
    */
   public void registerRequest(String urlPath, I_HttpRequest cb)
   {
      if (log.TRACE) log.trace(ME, "Registering urlPath: " + urlPath);
      knownRequests.put(urlPath.trim(), cb);
   }
   

   /**
    * Unregister your http listener. 
    * @param urlPath The access path which the client uses to access your data
    *        for example "/monitor/index.html" or "/favicon.ico"
    */
   public void removeRequest(String urlPath)
   {
      knownRequests.remove(urlPath.trim());
   }

   /**
    * Unregister your http listener. 
    * @param cb Remove all registered pathes of this registrar. 
    *        for example "/monitor/index.html" or "/favicon.ico"
    */
   public void removeRequest(I_HttpRequest cb)
   {
      Iterator it = knownRequests.keySet().iterator();
      ArrayList list = new ArrayList();
      while (it.hasNext()) {
         Object obj = it.next();
         if (obj instanceof I_HttpRequest) {
            I_HttpRequest tmp = (I_HttpRequest)obj;
            if (cb == tmp) {
               list.add(tmp);
            }
         }
      }
      for (int i=0; i<list.size(); i++) {
         knownRequests.remove(list.get(i));
      }
   }

   /**
    */
   public void run()
   {
      try {
         int backlog = glob.getProperty().get("http.backlog", 50); // queue for max 50 incoming connection request
         this.listen = new ServerSocket(HTTP_PORT, backlog, InetAddress.getByName(ip_addr));
         while (running) {
            Socket accept = this.listen.accept();
            log.trace(ME, "New incoming request on bootstrapPort=" + HTTP_PORT + " ...");
            if (!running) {
               log.info(ME, "Closing http server bootstrapPort=" + HTTP_PORT + ".");
               break;
            }
            new HandleRequest(glob, log, accept, knownRequests);
         }
      }
      catch (java.net.UnknownHostException e) {
         log.error(ME, "HTTP server problem, IP address '" + ip_addr + "' is invalid: " + e.toString());
      }
      catch (java.net.BindException e) {
         log.error(ME, "HTTP server problem, bootstrapPort " + ip_addr + ":" + HTTP_PORT + " is not available: " + e.toString());
      }
      catch (java.net.SocketException e) {
         log.info(ME, "Socket " + ip_addr + ":" + HTTP_PORT + " closed successfully: " + e.toString());
      }
      catch (IOException e) {
         log.error(ME, "HTTP server problem on " + ip_addr + ":" + HTTP_PORT + ": " + e.toString());
      }

      if (this.listen != null) {
         try { this.listen.close(); } catch (java.io.IOException e) { log.warn(ME, "this.listen.close()" + e.toString()); }
         this.listen = null;
      }
   }


   /**
    * Close the listener port
    */
   public void shutdown()// throws IOException
   {
      if (log.CALL) log.call(ME, "Entering shutdown");
      running = false;
      removeRequest(icoRequestUrlPath);
      boolean closeHack = true;
      if (this.listen != null && closeHack) {
         // On some JDKs, listen.close() is not immediate (has a delay for about 1 sec.)
         // force closing by invoking server with this temporary client:
         try {
            java.net.Socket socket = new Socket(this.listen.getInetAddress(), HTTP_PORT);
            socket.close();
         } catch (java.io.IOException e) {
            log.warn(ME, "shutdown problem: " + e.toString());
         }
      }

      try {
         if (this.listen != null) {
            this.listen.close();
            this.listen = null;
         }
      } catch (java.io.IOException e) {
         log.warn(ME, "shutdown problem: " + e.toString());
      }
   }

   /**
    * A HTTP request needs to be processed
    * @param urlPath The url path like "/monitor" which triggered this call
    * @param properties The key values from the browser
    * @return The HTML page to return
    */
   public HttpResponse service(String urlPath, Map properties) {
      if (urlPath.indexOf(icoRequestFile) != -1) {
         // set the application icon "favicon.ico"
         byte[] img = Global.getFromClasspath(icoRequestFile, this);
         if (log.TRACE) log.trace(ME, "Serving urlPath '" + urlPath + "'");
         return new HttpResponse(img, icoMimeType);
      }
      else if (urlPath.indexOf(fishRequestFile) != -1) {
         byte[] img = Global.getFromClasspath(fishRequestFile, this);
         if (log.TRACE) log.trace(ME, "Serving urlPath '" + urlPath + "'");
         return new HttpResponse(img, fishMimeType);
      }
      throw new IllegalArgumentException("Can't handle unknown " + urlPath);
   }

   /**
    * Access the server settings for logging. 
    * @return The socket <ip>:<port>, for example "server.xmlBlaster.org:3412"
    */
   public String getSocketInfo() {
      StringBuffer sb = new StringBuffer(196);

      if (this.listen == null) {
         if (running) {
            // Wait on thread to startup
            for (int i=0; i<10; i++) {
               try { Thread.sleep(20L); } catch( InterruptedException e) {}
               if (this.listen != null) break;
            }
         }
         if (this.listen == null) {
            return "";
         }
      }
      
      sb.append(listen.getInetAddress().getHostAddress());
      sb.append(":").append(this.HTTP_PORT);
      return sb.toString();
   }
} // class HttpIORServer


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
      this.ME = "HandleRequest" + this.glob.getLogPrefixDashed();
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
         String headerLine; // "\r\n"   carriage return and line feed terminate the http header section
         while (true /*!sock.isClosed() JDK 1.4 only*/) {
            headerLine = iStream.readLine();
            if (log.TRACE) log.trace(ME, "Receiving header '" + headerLine + "'");
            if (headerLine == null || headerLine.trim().length() < 1) {
               break;
            }
         }

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
//         String version = 
    	 toks.nextToken();  // "HTTP/1.0"

/*
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
*/

         // RFC 2068 enforces minimum implementation GET and HEAD
         if (!method.equalsIgnoreCase("GET") && !method.equalsIgnoreCase("HEAD")) {
            String info = "Invalid method in client " + getSocketInfo() + " request: '" + clientRequest + "'";
            errorResponse(oStream, "HTTP/1.1 501 Method Not Implemented", "Allow: GET", true, info);
            log.warn(ME, info);
            return;
         }

         // lookup if request is registered
         resource = resource.trim();

         
         Object obj = knownRequests.get(resource);
         if(log.TRACE) log.trace(ME, "1. Resource: " + resource + " => " + obj);
         if (obj == null) {
            Iterator it = knownRequests.keySet().iterator();
            while (it.hasNext()) {
               String key = (String)it.next();
               if (resource.startsWith(key)) {
                  obj = knownRequests.get(key);
                  break;
               }
            }
            if (obj == null) {
               String info = "Ignoring unknown data '" + resource + "' from client " + getSocketInfo() + " request: '" + clientRequest + "'";
               errorResponse(oStream, "HTTP/1.1 404 Not Found", null, true, info);
               log.warn(ME, info);
               return;
            }
         }
         if(log.TRACE) log.trace(ME, "2. Resource: " + resource + " => " + obj);

         HttpResponse httpResponse;
         if (obj instanceof String) {
            httpResponse = new HttpResponse((String)obj, "text/plain"); // CORBA IOR
         }
         else {
            I_HttpRequest httpRequest = (I_HttpRequest)obj;             // Registered plugins
            httpResponse = httpRequest.service(resource, new TreeMap());
         }

         // java.net.HttpURLConnection.HTTP_OK:
         errorResponse(oStream, "HTTP/1.1 200 OK", null, false, null);
         String length = "Content-Length: " + httpResponse.getContent().length;
         oStream.write((length+CRLF).getBytes());
         //oStream.write(("Transfer-Encoding: chunked"+CRLF).getBytes()); // java.io.IOException: Bogus chunk size
         oStream.write(("Content-Type: "+httpResponse.getMimeType()+"; charset=utf-8"+CRLF).getBytes());
         if (!method.equalsIgnoreCase("HEAD")) {
            oStream.write(CRLF.getBytes());
            oStream.write(httpResponse.getContent());
         }
         oStream.flush();
      }
      catch (Throwable e) {
         if (clientRequest == null && first) {
            if (log.TRACE) log.trace(ME, "Ignoring connect/disconnect attempt, probably a xmlBlaster client detecting its IP to use");
         } else {
            log.warn(ME, "Problems with sending response for '" + clientRequest + "' to client " + getSocketInfo() + ": " + e.toString());
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
} // class HandleRequest
