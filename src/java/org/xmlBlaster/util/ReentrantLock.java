/*------------------------------------------------------------------------------
Name:      ReentrantLock.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Create unique timestamp
------------------------------------------------------------------------------*/
package org.xmlBlaster.util;

/**
 * Extends a reentrantLock of Doug Lea and adds a lock() method similar
 * to the new java.util.concurrent.locks.ReentrantLock of JDK 1.5. 
 * @author <a href="mailto:xmlBlaster@marcelruff.info">Marcel Ruff</a>
 * @see org.xmlBlaster.test.classtest.ReentrantLockTest
 */
public final class ReentrantLock extends EDU.oswego.cs.dl.util.concurrent.ReentrantLock
{
   /**
    * Acquires the lock, same as acquire(), but ignoring a InterruptedException. 
    */
   public void lock() {
      while (true) {
         try {
            super.acquire();
            return;
         }
         catch (InterruptedException e) {
         }
      }
   }
}


