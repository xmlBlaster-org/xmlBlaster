/*------------------------------------------------------------------------------
Name:      I_ExecuteListener.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/
package org.xmlBlaster.util;


/**
 * Interface you need to implement to receive stdout/stderr data from a spawned process. 
 *
 * @author xmlBlaster@marcelruff.info
 */
public interface I_ExecuteListener
{
   public void stdout(String data);
   public void stderr(String data);
}
