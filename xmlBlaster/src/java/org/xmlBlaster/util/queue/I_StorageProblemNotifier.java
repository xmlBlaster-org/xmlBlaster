/*------------------------------------------------------------------------------
Name:      I_StorageProblemNotifier.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/
package org.xmlBlaster.util.queue;

public interface I_StorageProblemNotifier
{
   /**
    * registers a new listener to be notified. If the registration was not done (for example if the 
    * implementation only allows one listener and there is already one), then a 'false' is returned,
    * otherwise 'true' is returned.
    */
   public boolean registerStorageProblemListener(I_StorageProblemListener listener);

   /**
    * unregisters a listener. If there is no such listener 'false' is returned, otherwise 'true' is
    * returned.
    */
   public boolean unRegisterStorageProblemListener(I_StorageProblemListener listener);
}
