/*------------------------------------------------------------------------------
Name:      I_StorageProblemListener.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/
package org.xmlBlaster.util.queue;

public interface I_StorageProblemListener
{
   public static final int UNDEF = -1;
   public static final int UNAVAILABLE = 0;
   public static final int AVAILABLE = 1;

   /**
    * Invoked by the I_StorageProblemNotifier when the storage becomes unavailable (for example on a DB
    * when the jdbc connection is broken).
    * @param oldStatus the status before the storage became unavailable.
    */
   public void storageUnavailable(int oldStatus);

   /**
    * Invoked by the I_StorageProblemNotifier when the storage becomes available again (for example on a DB
    * when the jdbc connection is broken). Note that this method is invoked ONLY after the connection has
    * become unavailable, it is NOT invoked the at startup, i.e. the first time the connection becomes
    * available.
    */
   public void storageAvailable(int oldStatus);

}
