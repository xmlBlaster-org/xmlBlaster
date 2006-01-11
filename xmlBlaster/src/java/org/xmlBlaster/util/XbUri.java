/*------------------------------------------------------------------------------
Name:      XbUri.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/
package org.xmlBlaster.util;
import java.net.URI;
import java.net.URISyntaxException;

import org.jutils.text.StringHelper;

/**
 * Convenience wrapper to parse an URI. 
 * 
 * It supports to directly access the user and password.
 * <br />
 * <pre>
 * smtp://joe:mypassword@server.xmlBlaster.org:3412
 *
 * http://x.org:6000/mypath/?
 *
 * http://server.xmlBlaster.org:3412/myPath#myFragment
 *
 * http://server.xmlBlaster.org:3412/myPath?key.oid=MyMessage
 *
 * http://server.xmlBlaster.org:3412/myPath/?key.oid=MyMessage
 *
 * http://server.xmlBlaster.org:3412/myPath?key.oid=MyMessage#myFragment
 * </pre>
 * 
 * <pre>
 * http://joe:mypassword@server.xmlBlaster.org:3412/myPath?key.oid=MyMessage#myFragment
Protocol: http
Host:     server.xmlBlaster.org
Port:     3412
File:     /myPath?key.oid=MyMessage
Path:     /myPath
Query:    key.oid=MyMessage
Ref:      myFragment
UserInfo: joe:mypassword
 * </pre>
 * <pre>
 * http:/myPath/?key.oid=MyMessage#myFragment
Protocol: http
Host:     
Port:     -1
File:     /myPath/?key.oid=MyMessage
Path:     /myPath/
Query:    key.oid=MyMessage
Ref:      myFragment
UserInfo: null
 * </pre>
 *
 * INVALID:
 * http://server.xmlBlaster.org:3412/myPath#myFragment?key.oid=MyMessage
 *
 * getRef() == Fragment
 * <p />
 * Note: Using a '@' character in the username or password should
 * be written as '%40', but we handle the '@' as well to ease the use.
 * @see SocketUrl.java
 */
public class XbUri
{
   private URI uri;

   private String user;
   private String password;
   
   private final String placeHolder = "$____$"; // "%40"
   
   public XbUri(String uriStr) throws URISyntaxException {
      parse(uriStr);
   }
   
   
   private void parse(String uriStr) throws URISyntaxException {
      //http://j@@oe:my@p@assword@server.xmlBlaster.org:3412/myPath?key.oid=MyMessage#myFragment
      //
      // The normal URI can't handle a '@' in the username or password:
      //    pop3://test@xy:test@xy@localhost:8110/INBOX
      // it should be escaped with '%40':
      //    pop3://test%40xy:test%40xy@localhost:8110/INBOX
      //
      // As we can't expect to people doing so, we handle it here by a temporary replacement
      int count=0;
      for (int i=0; i<uriStr.length(); i++) {
         if (uriStr.charAt(i) == '@') {
            count++;
         }
      }
      for (int i=1; i<count; i++) {
         uriStr = StringHelper.replaceFirst(uriStr, "@", placeHolder);
      }

      this.uri = new URI(uriStr);
      
      this.user = "";
      this.password = null;
      if (this.uri.getUserInfo() != null) {
         int i = this.uri.getUserInfo().indexOf(":");
         this.user = this.uri.getUserInfo();
         if (i != -1) {
            this.user = this.uri.getUserInfo().substring(0,i);
            this.password = this.uri.getUserInfo().substring(i+1);
            if (count > 1) {
               this.user = StringHelper.replaceAll(this.user, placeHolder, "@");
               this.password = StringHelper.replaceAll(this.password, placeHolder, "@");
            }
         }
      }
   }


   /**
    * @return Returns the host.
    */
   public String getHost() {
      return this.uri.getHost();
   }

   /**
    * @return Returns the password, IS NULL if no password was given
    */
   public String getPassword() {
      return this.password;
   }

   /**
    * @return Returns the path.
    */
   public String getPath() {
      return this.uri.getPath();
   }

   /**
    * @return Returns the port.
    */
   public int getPort() {
      return this.uri.getPort();
   }

   /**
    * @return Returns the query.
    */
   public String getQuery() {
      return (this.uri.getQuery() == null) ? "" : this.uri.getQuery();
   }

   /**
    * @return Returns the scheme.
    */
   public String getScheme() {
      return this.uri.getScheme();
   }

   /**
    * @return Returns the uri.
    */
   public URI getUri() {
      return this.uri;
   }

   /**
    * @return Returns the user.
    */
   public String getUser() {
      return this.user;
   }

   /**
    * @return Returns the userInfo.
    */
   public String getUserInfo() {
      //return this.uri.getUserInfo(); -> contains replacement !
      if (this.user == null && this.password == null)
         return null;
      String userInfo = (this.user != null) ? this.user : "";
      if (this.password != null) {userInfo += ":"+this.password;}
      return userInfo;
   }
   
   public String toString() {
      return this.uri.toString();
   }

   public String toLiteral() {
      return
              "Scheme:   " + getScheme() +
            "\nHost:     " + getHost() +
            "\nPort:     " + getPort() +
            "\nPath:     " + getPath() +
            "\nQuery:    " + getQuery() +
            "\nUserInfo: " + getUserInfo() +
            "\nuser:     " + this.user +
            "\npassword: " + this.password;
   }

   /**
    * Returns for example "pop3://demo@localhost:110/INBOX" which is extracted
    * from pop3Url="pop3://demo:secret@localhost:110/INBOX"
    * 
    * @return
    */
   public String getUrlWithoutPassword() {
      return getScheme() + "://" + getUserInfo() + "@"
            + getHost()
            + ((getPort() > 0) ? (":" + getPort()) : "");
      // TODO: Add the path
   }
   

   // java org.xmlBlaster.util.XbUri smtp://:mypassword@server.xmlBlaster.org:3412
   public static void main(String[] args) {
      try {
         String str = (args.length > 0) ? args[0] : "smtp://joe:mypassword@server.xmlBlaster.org:3412";
         XbUri test = new XbUri(str);
         System.out.println(test.toLiteral());
      } catch (URISyntaxException e) {
         e.printStackTrace();
      }
   }
}

