import java.io.*;
import java.net.*;

/**
 * tail -n 100 -f /var/squid/logs/access.log
 * telnet develop 3128 
 * GET http://194.121.221.46:8080 HTTP/1.0
 * GET http://192.168.1.2:8080 HTTP/1.0
 *
 'GET / HTTP/1.0' len=14
 'Via: 1.0 develop.ruff.de:3128 (Squid/2.4.STABLE2)' len=49
 'X-Forwarded-For: 192.168.1.2' len=28
 'Host: 192.168.1.2:8080' len=22
 'Cache-Control: max-age=259200' len=29
 'Connection: keep-alive' len=22

POST http://192.168.1.2:8080 HTTP/1.0
From: xx@yy.com
User-Agent: HTTPxmlBlaster/1.0
Content-Type: application/x-www-form-urlencoded
Content-Length: 32

home=Cosby&favorite+flavor=flies


POST http://192.168.1.2:8080 HTTP/1.1
From: xx@yy.com
User-Agent: HTTPxmlBlaster/1.0
Content-Type: application/x-www-form-urlencoded
Content-Length: 32

home=Cosby&favorite+flavor=flies


  * Response:

HTTP/1.0 200 OK
Date: Fri, 31 Dec 1999 23:59:59 GMT
Content-Type: text/html
Content-Length: 1354

<html>
<body>
<h1>Happy New Millennium!</h1>
(more file contents)
.
.
.
</body>
</html>
 */
public class EchoServer extends HttpReader {
   ServerSocket listen = null;
   Socket client = null;
   PrintWriter out = null;
   BufferedInputStream in = null;
   ByteArray array = new ByteArray();
   int contentLength = 0;
   int indexContent = 0;

   public EchoServer(int port) throws Exception {
      super("EchoServer");

      try {
         listen = new ServerSocket(port);
         while (true) {
            try {
               System.out.println("Waiting on port " + port + " for somebody who wants to talk to me [" + new java.util.Date().toString() + "]");
               Socket client = listen.accept();
               client.setTcpNoDelay(true);
               client.setSoLinger(true, 2000);
               //client.setSoTimeout(1000*60*);
               client.setKeepAlive(true);  // JDK 1.3
               System.out.println("\nClient accepted!");
               out = new PrintWriter(client.getOutputStream(), true);
               in = new BufferedInputStream(client.getInputStream());

               byte curr;
               int val;
               int index = 0;
               boolean isHttpHeader = false;

               for (int ii=0; ii<1000000; ii++) {
                  System.out.println("\n*** Waiting for data " + ii + " [" + new java.util.Date().toString() + "]");
                  byte[] data = read(in);
                  System.out.println("*** Received [" + new java.util.Date().toString() + "]" + ": '" + new String(data) + "'");

                  String resp = new String(data);
                  String header = getReplyHeader(resp.length());
                  System.out.println("\n*** Sending reply\n" + header + resp);
                  out.print(header);
                  out.print(resp);
                  out.flush();

                  for (int jj=0; jj<COUNT_CB; jj++) {
                     //try { Thread.currentThread().sleep(500); } catch(Exception e) { }
                     String asynchCallback = "        32<callback>" + jj%10 + "</callback>";
                     header = getReplyHeader(asynchCallback.length());
                     //header = "";
                     System.out.println("\n*** Sending callback " + (ii+1)*jj + "\n" + header + new String(asynchCallback));
                     out.print(header);
                     out.print(asynchCallback);
                     out.flush();
                  }
                  //try { Thread.currentThread().sleep(500); } catch(Exception e) { }
               }
            }
            catch (IOException e) {
               System.out.println("Problem with Client: " + e.toString());
            }
            finally {
               if (out != null) out.close();
               if (in != null) in.close();
               if (client != null) client.close();
               System.out.println("################ Client removed!\n");
            }
         }
      }
      catch (NumberFormatException e) {
         System.err.println("The message is corrupted, can't parse content length: " + e.toString());
      }
      catch (Throwable e) {
         System.out.println("ERROR: " + e.toString());
         e.printStackTrace();
      }
      finally {
         if (listen!=null) listen.close();
         System.out.println("Listen Socket closed!\n");
      }
   }


   public static void main(String[] args) throws IOException {
      int port = 8080;
      try {
         if (args.length > 0)
            port = Integer.parseInt(args[0]);

         new EchoServer(port);
      }
      catch (Throwable e) {
         System.out.println("STARTUP ERROR: " + e.toString());
         e.printStackTrace();
      }
   }
}

