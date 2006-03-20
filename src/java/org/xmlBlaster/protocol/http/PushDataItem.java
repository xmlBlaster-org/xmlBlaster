/*------------------------------------------------------------------------------
Name:      PushDataItem.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Handling callback over http
------------------------------------------------------------------------------*/
package org.xmlBlaster.protocol.http;

/**
 * Little helper container to hold data which is pushed to browser
 */
public class PushDataItem
{
   /** To mark pushed pings */
   public static final int PING=0;
   /** To mark pushed messages */
   public static final int MESSAGE=1;
   /** To mark pushed logging/errors */
   public static final int LOGGING=1;
   public PushDataItem(int type, String data)
   {
      this.type = type;
      this.data = data;
   }
   /**
   * messages or any other administrative pushs?
   */
   public int type;
   public String data;
}
