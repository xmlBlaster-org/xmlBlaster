/*------------------------------------------------------------------------------
Name:      RcvTimestamp.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Create unique timestamp for incoming message
Version:   $Id: RcvTimestamp.java,v 1.2 2002/03/13 16:41:34 ruff Exp $
Author:    ruff@swand.lake.de
------------------------------------------------------------------------------*/
package org.xmlBlaster.util;


/**
 * Timestamp for received messages, time elapsed since 1970, the nanos are simulated
 * as a unique counter. 
 * <pre>
 *  &lt;rcvTimestamp nanos='1013346248150000001'>
 *     2002-02-10 14:04:08.150000001
 *  &lt;/rcvTimestamp>
 * </pre>
 * or
 * <pre>
 *  &lt;rcvTimestamp nanos='1013346248150000001'/>
 * </pre>
 * @see org.xmlBlaster.util.Timestamp;
 * @author <a href="mailto:ruff@swand.lake.de">Marcel Ruff</a>
 */
public class RcvTimestamp extends Timestamp
{
   /**
    * Constructs a current timestamp which is guaranteed to be unique in time for this JVM
    * @exception RuntimeException on overflow (never happens :-=)
    */
   public RcvTimestamp() {
      tagName = "rcvTimestamp";
   }

   /**
    * Create a Timestamp with given nanoseconds since 1970
    */
   public RcvTimestamp(long nanos) {
      super(nanos);
      tagName = "rcvTimestamp";
   }
}


