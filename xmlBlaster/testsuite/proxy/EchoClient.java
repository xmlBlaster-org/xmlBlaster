import java.io.*;
import java.net.*;

public class EchoClient extends HttpReader {

   public EchoClient(String proxyHost, int port, String destinationUrl) throws IOException {
      super("EchoClient");

      Socket echoSocket = null;
      PrintWriter out = null;
      BufferedInputStream in = null;

      try {
         echoSocket = new Socket(proxyHost, port);
         System.out.println("\n*** Connected to proxy=" + proxyHost + " on proxyPort=" + port + " accessing " + destinationUrl);
         out = new PrintWriter(echoSocket.getOutputStream(), true);
         in = new BufferedInputStream(echoSocket.getInputStream());
         int count = 20;
         for (int ii=0; ii<count; ii++) {
            if ((count % 10) == 9) {
               try { Thread.currentThread().sleep(5000); } catch(Exception e) { }
            }
            System.out.println("\nSending POST #" + ii);
            String resp = "UAL #" + ii%10;
            String header = getPostHeader(destinationUrl, resp.length());
            out.print(header);
            out.print(resp);
            out.flush();

            System.out.println("\n*** Waiting for data [" + new java.util.Date().toString() + "]");
            byte[] data = read(in);
            System.out.println("*** Received: '" + new String(data) + "'");
         }
         /*
         while (true) {
            System.out.println("\n*** Waiting for callback data");
            byte[] data = read(in);
            System.out.println("*** Received Callback: '" + new String(data) + "'");
         } */
      } catch (UnknownHostException e) {
         System.err.println("Don't know about proxyHost: " + proxyHost);
      } catch (IOException e) {
         System.err.println("Couldn't get I/O for the connection to: " + proxyHost);
      }
      finally {
         if (out != null) out.close();
         if (in != null) in.close();
         if (echoSocket != null) echoSocket.close();
         System.out.println("Connection removed!\n");
      }
   }


   public static void main(String[] args) throws IOException {
      System.out.println("Usage: java EchoClient <proxyHost> <port> <destinationUrl>\nExample:   java EchoClient 192.168.1.2 3128 http://194.121.221.46:8080\n");
      
      String destinationUrl = "http://194.121.221.46:8080";   //"http://192.168.1.2:8080";
      String proxyHost = "192.168.1.2";
      int port = 3128;
      try {
         if (args.length > 0)
            proxyHost = args[0];
         if (args.length > 1)
            port = Integer.parseInt(args[1]);
         if (args.length > 2)
            destinationUrl = args[2];

         new EchoClient(proxyHost, port, destinationUrl);
      }
      catch (Throwable e) {
         System.out.println("STARTUP ERROR: " + e.toString());
         e.printStackTrace();
      }
   }
}
