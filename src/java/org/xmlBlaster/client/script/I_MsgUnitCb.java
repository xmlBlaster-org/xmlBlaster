/*------------------------------------------------------------------------------
Name:      I_MsgUnitCb.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/
package org.xmlBlaster.client.script;

import org.xmlBlaster.util.MsgUnit;

/**
 * Interface to intercept a MsgUnit just before it is published. 
 * @author <a href="mailto:xmlblast@marcelruff.info">Marcel Ruff</a>
 */
public interface I_MsgUnitCb {

   /**
    * @return false: don't publish it
    */
   public boolean intercept(MsgUnit msgUnit);
}
