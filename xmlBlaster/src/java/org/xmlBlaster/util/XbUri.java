/*------------------------------------------------------------------------------
Name:      XbUri.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/
package org.xmlBlaster.util;
import java.net.URI;
import java.net.URISyntaxException;

/**
 * Convenience wrapper to parse an URI. 
 * 
 * It supports to directly access the user and password.
 * <br />
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
 *
 * http://joe:mypassword@server.xmlBlaster.org:3412/myPath?key.oid=MyMessage#myFragment
Protocol: http
Host:     server.xmlBlaster.org
Port:     3412
File:     /myPath?key.oid=MyMessage
Path:     /myPath
Query:    key.oid=MyMessage
Ref:      myFragment
UserInfo: joe:mypassword
 *
 * http:/myPath/?key.oid=MyMessage#myFragment
Protocol: http
Host:     
Port:     -1
File:     /myPath/?key.oid=MyMessage
Path:     /myPath/
Query:    key.oid=MyMessage
Ref:      myFragment
UserInfo: null
 *
 * INVALID:
 * http://server.xmlBlaster.org:3412/myPath#myFragment?key.oid=MyMessage
 *
 * getRef() == Fragment
 * @see SocketUrl.java
 */
public class XbUri
{
   private URI uri;

   private String user;
   private String password;
   
   public XbUri(String uriStr) throws URISyntaxException {
      this.uri = new URI(uriStr);

      this.user = "";
      this.password = "";
      if (this.uri.getUserInfo() != null) {
         int i = this.uri.getUserInfo().indexOf(":");
         this.user = this.uri.getUserInfo();
         this.password = "";
         if (i != -1) {
            this.user = this.uri.getUserInfo().substring(0,i);
            this.password = this.uri.getUserInfo().substring(i+1);
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
    * @return Returns the password.
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
      return this.uri.getUserInfo();
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

