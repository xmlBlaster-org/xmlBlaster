/*------------------------------------------------------------------------------
Name:      UriAuthority.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Parse authentication URI
Version:   $Id: UriAuthority.java,v 1.3 2002/06/18 18:07:42 ruff Exp $
------------------------------------------------------------------------------*/
package org.xmlBlaster.util;

import java.net.URLEncoder;
import java.net.URLDecoder;

/**
 * Parse server-based authority: [<userInfo>@]<host>[:<port>]
 * <userInfo> := <user[:password]>
 *
 * This is the 'Authority' subset of JDK 1.4 java.net.URI
 * <p />
 * Example:
 * <pre>
 *    joe:secret@myServer.com:7608
 * </pre>
 * This addresses the user 'joe' with the Password 'secret'
 * The user is known or logged in at the xmlBlaster server running on 'myServer.com'
 * on port 7608
 * <p/>
 * A complete URI could be:
 * <pre>xmlBlaster.publish://joe:mypassword@server.xmlBlaster.org:3412/myPath#myFragment?key.oid=MyMessage</pre>
 * Where<br />
 * <ul>
 * <li>schema    = xmlBlaster.publish</li>
 * <li>authority = joe:mypassword@server.xmlBlaster.org:3412</li>
 * <li>path      = myPath</li>
 * <li>fragment  = myFragment</li>
 * <li>query     = key.oid=MyMessage</li>
 * <ul>
 */
public class UriAuthority
{
   private final String ME="UriAuthority";
   private final String uri; // Server-based authority: [<user:password>@]<host>[:<port>]
   /** The user encoded with URLEncoder */
   private String user;
   /** The password encoded with URLEncoder */
   private String password;
   /** The host name */
   private String host;      // null ==> registry-based
   /** The server port, -1 is undefined */
   private int port = -1;

   /**
    * @param uri The complete authority part of an URI. It can be URLEncoded
    */
   public UriAuthority(String uri) {
      this.uri = uri.trim();
      initialize();
   }

   /**
    * If the user uses for example his email address for login 'joe@univers.org'
    * the '@' will be HTML encoded to '%40'
    * @param user The user (not encoded!)
    * @param password The password (not encoded!), may be NULL
    * @param host The host part
    * @param port The port number
    */
   public UriAuthority(String user, String password, String host, int port) {
      if (user != null) this.user = URLEncoder.encode(user.trim());
      if (password != null) this.password = URLEncoder.encode(password.trim());
      this.host = host.trim();
      this.port = port;
      uri = toString();
   }

   private void initialize() {
      // joe:secret@myServer.com:7608
      user = null;
      password = null;
      host = null;
      port = -1;

      String socket = uri;
      int atIndex = uri.indexOf('@');
      if (atIndex > -1) {
         // parse the "joe:secret" part:
         String userInfo = uri.substring(0,atIndex);
         int colonIndex = userInfo.indexOf(':');
         if (colonIndex > -1) {
            user = userInfo.substring(0,colonIndex);
            password = userInfo.substring(colonIndex+1);
         }
         else {
            user = userInfo;
         }
         socket = uri.substring(atIndex+1);
      }

      // parse the "myServer.com:7608" part:
      int colonIndex = socket.indexOf(':');
      if (colonIndex > -1) {
         host = socket.substring(0,colonIndex);
         try {
            String portStr = socket.substring(colonIndex+1);
            port = new Integer(portStr).intValue();
         }
         catch(NumberFormatException e) {
            System.out.println("URI <" + uri + "> contains invalid port information, setting port to -1");
         }
      }
      else {
         host = socket;
      }
   }

   /** @return Not encoded. Null if not known */
   public String getUser() {
      try {
         return URLDecoder.decode(user);
      } catch(Throwable e) {
         Log.error(ME, "URLDecoder problem: " + e.toString());
         return user;
      }
   }

   /** @return Not encoded. Null if not known */
   public String getPassword() {
      try {
         return URLDecoder.decode(password);
      } catch(Throwable e) {
         Log.error(ME, "URLDecoder problem: " + e.toString());
         return user;
      }
   }

   /** @return Null if not known */
   public String getEncodedUser() {
      return user;
   }

   /** @return Null if not known */
   public String getEncodedPassword() {
      return password;
   }

   /** @return null if not known */
   public String getHost() {
      return host;
   }

   /** @return -1 if not known */
   public int getPort() {
      return port;
   }

   /**
    * The user and password are human readable (not encoded)
    */
   public String toString() {
      StringBuffer buf = new StringBuffer(128);
      try {
         if (user!=null) buf.append(URLDecoder.decode(user));
         if (password!=null) buf.append(":").append(URLDecoder.decode(password));
      } catch(Throwable e) {
         Log.error(ME, "URLDecoder problem: " + e.toString());
         return user;
      }
      if (host!=null) buf.append("@").append(host);
      if (port>-1) buf.append(":").append(port);
      return buf.toString();
   }

   /**
    * The user and password are encoded
    */
   public String toEncodedString() {
      StringBuffer buf = new StringBuffer(128);
      if (user!=null) buf.append(user);
      if (password!=null) buf.append(":").append(password);
      if (host!=null) buf.append("@").append(host);
      if (port>-1) buf.append(":").append(port);
      return buf.toString();
   }

   public String toXml() {
      String offset = "\n";
      StringBuffer sb = new StringBuffer(512);
      sb.append(offset).append("<uri id='").append(uri).append("'>");
      sb.append(offset).append("  <user>").append(user).append("</user>");
      sb.append(offset).append("  <password>").append(password).append("</password>");
      sb.append(offset).append("  <host>").append(host).append("</host>");
      sb.append(offset).append("  <port>").append(port).append("</port>");
      //sb.append("  <authority>").append(authority).append("</authority>");
      //sb.append("  <path>").append(path).append("</path>");
      //sb.append("  <query>").append(query).append("</query>");
      //sb.append("  <fragment>").append(fragment).append("</fragment>");
      sb.append(offset).append("</uri>");
      return sb.toString();
   }

   /** To test: javac -g UriAuthority.java
    *           java  org.xmlBlaster.util.UriAuthority
    */
   public static void main(String[] args) {
      test("  joe:secret@myServer.com:7608 ");
      test("joe ");
      test("joe:secret");
      test("myServer.com:7608");
      test(":@:");
   }
   static boolean test(String test)
   {
      UriAuthority uri= new UriAuthority(test);
      if (test.trim().equals(uri.toString())) {
         System.out.println("OK: "+test);
         System.out.println(uri.toXml());
         return true;
      }
      else
         System.out.println("ERROR: "+test+" differs from "+uri.toString());
      return false;
   }
   static boolean test(String user, String password)
   {
      UriAuthority uri= new UriAuthority(user, password, "localhost", 3412);
      String result = user + "@localhost:3412";
      if (password != null)
         result = user + ":" + password + "@localhost:3412";
      if (result.trim().equals(uri.toString())) {
         System.out.println("OK: "+result);
         System.out.println(uri.toXml());
         return true;
      }
      else
         System.out.println("ERROR: "+result+" differs from "+uri.toString());
      return false;
   }
}
