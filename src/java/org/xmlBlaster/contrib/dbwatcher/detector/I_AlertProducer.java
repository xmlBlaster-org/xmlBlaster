/*------------------------------------------------------------------------------
Name:      I_AlertProducer.java
Project:   org.xmlBlasterProject:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/
package org.xmlBlaster.contrib.dbwatcher.detector;

import org.xmlBlaster.contrib.I_Info;

/**
 * Interface which hides a scheduler or a trigger. 
 * <p> 
 * The plugin needs to call {@link I_ChangeDetector#checkAgain(Map)} whenever
 * it thinks it's time to do so.
 * @author Marcel Ruff
 */
public interface I_AlertProducer {
   /**
    * Needs to be called after construction. 
    * @param info The configuration environment
    * @throws Exception Can be any plugin specific exception
    */
   void init(I_Info info, I_ChangeDetector changeDetector) throws Exception;
   
   /**
    * Starts the alert producer. 
    * @throws Exception of any type
    */
   void startProducing() throws Exception;

   /**
    * Sets the producer to standby. 
    * A call to #startProducing() starts it again 
    * @throws Exception of any type
    */
   void stopProducing() throws Exception;

   /**
    * Stop producing alerts and cleanup resources. 
    * @throws Exception of any type 
    */
   void shutdown() throws Exception;
}
