/*------------------------------------------------------------------------------
Name:      I_Log.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/
package org.xmlBlaster.client.protocol.http.common;

/**
 * You can redirect the logging output of the xmlBlaster applet client library. 
 * @author <a href="mailto:xmlBlaster@marcelruff.info">Marcel Ruff</a>.
 */
public interface I_Log
{
   /**
    * Receive the logging. 
    * @param location The class and/or method name
    * @param level One of "ERROR", "WARN", "INFO", "DEBUG"
    * @param text The text to log
    */
   public void log(String location, String level, String text);
}

