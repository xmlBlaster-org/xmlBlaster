/*------------------------------------------------------------------------------
Name:      I_Notify.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/
package org.xmlBlaster.util.dispatch.plugins.prio;

/**
 * The Interface allows to callback when a status message arrived. 
 * @author xmlBlaster@marcelruff.info
 */
public interface I_Notify
{
   public void statusChanged(String status);
}
