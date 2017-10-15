/*------------------------------------------------------------------------------
Name:      HelperIPv6And4.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   SocketUrl knows how to parse the URL notation of our SOCKET protocol
------------------------------------------------------------------------------*/
package org.xmlBlaster.util;

/**
 * Helper for handling differences between IPv6 and IPv4.
 */
public class HelperIPv6And4 {

   public HelperIPv6And4() {
   }

   public final static boolean isIPv6(String url) {
      return url.contains("[") && url.contains("]");
   }

   public final static int getIPv6OrIPv4PortPosition(String url) {
      int pos = -1;
      if (isIPv6(url))
         pos = url.lastIndexOf("]:");
      else
         pos = url.lastIndexOf(":");
	      return pos;
   }

}

