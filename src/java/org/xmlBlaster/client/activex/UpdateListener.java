/*------------------------------------------------------------------------------
Name:      XmlScriptAccess.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/
package org.xmlBlaster.client.activex;

/**
 * Interface to support callbacks from Java over the ActiveX bridge into C# or VisualBasic. 
 * @author <a href="mailto:xmlBlaster@marcelruff.info">Marcel Ruff</a>
 */
public interface UpdateListener extends java.util.EventListener {
   void update(UpdateEvent updateEvent);
}

