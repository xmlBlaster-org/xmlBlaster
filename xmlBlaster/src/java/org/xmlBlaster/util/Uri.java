/*------------------------------------------------------------------------------
Name:      Uri.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Parse URI
------------------------------------------------------------------------------*/
package org.xmlBlaster.util;

import org.jutils.log.LogChannel;
import org.xmlBlaster.util.Global;

import java.net.*;
import java.io.*;

/**
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
 */
public class Uri
{
   private final String ME = "Uri";
   private final Global glob;
   private final LogChannel log;
   private URL url = null;
   private String uriStr = null;
   
   public Uri(Global glob, String uriStr) throws XmlBlasterException {
      this.glob = glob;
      this.log = glob.getLog("core");
      this.uriStr = uriStr;
      try {
         url = new URL(uriStr);
      } catch(MalformedURLException e) {
         log.error(ME, "'" + uriStr + "' is not valid: " + e.toString());
         throw new XmlBlasterException(ME, "'" + uriStr + "' is not valid: " + e.toString());
      }
   }

   public String getUriString() {
      return this.uriStr;
   }

   /*
   public void printURL() {
      if (uri == null) {
         System.out.println("No URL");
         return;
      }
      String protocol = uri.getProtocol();
      String host = uri.getHost();
      int port = uri.getPort();
      String file = uri.getFile();
      System.out.println("Protocol: " + protocol +
                       "\nHost:     " + host +
                       "\nPort:     " + port +
                       "\nFile:     " + file +
                       "\nPath:     " + uri.getPath() +
                       "\nQuery:    " + uri.getQuery() +
                       "\nRef:      " + uri.getRef() +
                       "\nUserInfo: " + uri.getUserInfo());

   }
   */

   public String toXml() {
      String offset = "\n";
      StringBuffer sb = new StringBuffer(256);
      sb.append(offset).append("<uri id='").append(uriStr).append("'>");
      sb.append(offset).append("  <userInfo>").append(url.getUserInfo()).append("</userInfo>");
      //sb.append(offset).append("  <password>").append(password).append("</password>");
      sb.append(offset).append("  <host>").append(url.getHost()).append("</host>");
      sb.append(offset).append("  <port>").append(url.getPort()).append("</port>");
      sb.append(offset).append("  <authority>").append(url.getAuthority()).append("</authority>");
      sb.append(offset).append("  <path>").append(url.getPath()).append("</path>");
      sb.append(offset).append("  <query>").append(url.getQuery()).append("</query>");
      sb.append(offset).append("  <fragment>").append(url.getRef()).append("</fragment>");
      sb.append(offset).append("</uri>");
      return sb.toString();
   }

   public static void main(String[] args) {
   }
}

