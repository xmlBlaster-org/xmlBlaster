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



Content-Type: multipart/x-mixed-replace;boundary=End
.....
--End
Content-Type: text/html

 */
public class HttpReader {

   ByteArray array = new ByteArray();
   File to_file;
   FileOutputStream file;
   public final int CR = 13;
   public final int LF = 10;
   public final byte[] CRLF = {13, 10};
   //public final String CRLFstr = new String(CRLF);
   public final String CRLFstr = "\r\n";
   public final int COUNT_CB = 0; // for testing only

   protected HttpReader(String filename) throws FileNotFoundException {
      to_file = new File(filename+".log");
      file = new FileOutputStream(to_file);
   }


   protected String getReplyHeader(int contentLength) {
      StringBuffer buf = new StringBuffer(512);
      buf.append("HTTP/1.1 200 OK").append(CRLFstr);
      //buf.append("Date: Fri, 31 Dec 1999 23:59:59 GMT").append(CRLFstr);
      //buf.append("Expires: Tue, 31 Dec 1997 23:59:59 GMT").append(CRLFstr);
      //buf.append("").append(CRLFstr);
      buf.append("Server: HTTP xmlBlaster server/1.0").append(CRLFstr);
      buf.append("Cache-Control: no-cache, no-store, must-revalidate").append(CRLFstr);
      buf.append("Connection: Keep-alive").append(CRLFstr);
      buf.append("Keep-Alive: 300000").append(CRLFstr);
      buf.append("Expires: 0").append(CRLFstr);
      //buf.append("Content-Type: multipart/x-mixed-replace;boundary=End").append(CRLFstr);
      buf.append("Content-Type: application/octet-stream").append(CRLFstr);
      buf.append("Content-Length: ").append(contentLength).append(CRLFstr);
      //buf.append("Content-Length: 300000").append(CRLFstr);
      //buf.append("Content-Type: text/plain").append(CRLFstr);
      buf.append(CRLFstr);
      return buf.toString();
   }

   protected String getPostHeader(String url, int contentLength) {
      StringBuffer buf = new StringBuffer(512);
      buf.append("POST ").append(url).append(" HTTP/1.1").append(CRLFstr);
      buf.append("From: xx@yy.com").append(CRLFstr);
      buf.append("User-Agent: HTTP xmlBlaster/1.0").append(CRLFstr);
      buf.append("Cache-Control: no-cache, no-store, must-revalidate").append(CRLFstr);
      buf.append("Expires: 0").append(CRLFstr);
      buf.append("Connection: Keep-alive").append(CRLFstr);
      buf.append("Keep-Alive: 30000000").append(CRLFstr);
      //buf.append("Content-Type: application/octet-stream").append(CRLFstr);
      buf.append("Content-Type: text/plain").append(CRLFstr);
      buf.append("Content-Length: ").append(contentLength).append(CRLFstr);
      buf.append(CRLFstr);
      return buf.toString();
   }

   protected byte[] read(InputStream in) throws IOException {

      int contentLength = 0;
      int indexContent = 0;

      byte curr;
      int val;
      int index = 0;
      boolean isHttpHeader = false;

         while (true) {
            val = in.read();
            index ++;
            indexContent++;
            /*
            if (val == -1) {
               System.out.println("Can't read bytes from socket, trying again");
               continue;
            }
            */
            /*
            if (val == 0)
               break;
            */
            if (val == -1)
               throw new IOException("Can't read bytes from socket, socket closed");
            if (index == 0 && val == 'G' || val == 'P') {
               isHttpHeader = true;
               System.out.println("Receiving HTTP request");
            }

            array.write(val);
            
            //System.out.println("'" + val + "'");

            if (val == CR) {
               continue;
            }
            if (val == LF) {
               String line = new String(array.toByteArray()).trim();
               if (line.length() == 0) {
                  byte[] lenb = new byte[10];
                  int read = 0;
                  while (read < 10)
                     read += in.read(lenb, read, 10-read);
                  long len = Long.parseLong(new String(lenb).trim());
                  //System.out.println("*** Expecting raw data len=" + len);
                  byte[] data = new byte[(int)len];
                  System.arraycopy(lenb, 0, data, 0, 10);
                  //read=10 is already
                  while (read < len) {
                     read += in.read(data, read, (int)len-read);
                  }
                  //System.out.println("*** Data=\n'" + new String(data) + "'");
                  return data;
               }

            /*
            if (val == LF) {
               //System.out.println("Ignoring linefeed");
               continue;
            }

            if (val == CR) {
               String line = new String(array.toByteArray()).trim();
               if (line.length() == 0) {
                  //System.out.println("*** Starting data section");
                  indexContent = 0;
                  byte[] data = new byte[contentLength];
                  if (contentLength == 0) {
                     //System.out.println("*** NO CONTENT");
                     return data;
                  }
                  val = in.read();
                  int offset = 0;
                  if (val != LF) {
                     System.out.println("*** MISSING LF");
                     offset++;
                     data[0] = (byte)val;
                  }
                  int read = 0;
                  while (read != contentLength) {
                     read += in.read(data, offset, contentLength);
                  }
                  String dataStr = new String(data);
                  //System.out.println("*** Data=\n'" + new String(data) + "'");
                  return data;
               }
               */
               System.out.println(line);
               file.write(line.getBytes());
               // We ignore "HTTP/1.1 100 Continue"
               if (line.startsWith("Content-Length:")) {
                  String tmp = line.substring(15).trim();
                  contentLength = Integer.parseInt(tmp);
                  //System.out.println("*** Content length = " + contentLength);
               }
               array.reset();
            }
         }
      //   if (file != null) file.close();
      //return null;
   }
}

