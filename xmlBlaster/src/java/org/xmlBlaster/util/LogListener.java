/*------------------------------------------------------------------------------
Name:      LogListener.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Listens on log output
------------------------------------------------------------------------------*/
package org.xmlBlaster.util;


/**
 * Listens on logging events.
 * <p>
 * The events are fired by Log.java.
 *
 * @version $Id: LogListener.java,v 1.1 1999/12/22 09:37:09 ruff Exp $
 * @author Marcel Ruff
 */
public interface LogListener extends java.util.EventListener
{
   /**
    * Invoked for each new logging string
    */
   public void log(String str);
}
