/*------------------------------------------------------------------------------
Name:      I_EventDispatcher.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/

package org.xmlBlaster.util;

/**
 * I_EventDispatcher used/implemented by the EventPlugin. Allows an abstraction between 
 * engine code and util code (can be used even by client side plugins).
 * @author <a href="mailto:michele@laghi.eu">Michele Laghi</a>
 */
public interface I_EventDispatcher {

   void dispatchEvent(String summary, String description, String eventType);

}
