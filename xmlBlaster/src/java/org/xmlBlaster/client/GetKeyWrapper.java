/*------------------------------------------------------------------------------
Name:      GetKeyWrapper.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Handling one xmlKey
Version:   $Id: GetKeyWrapper.java,v 1.1 2000/07/11 08:50:17 ruff Exp $
------------------------------------------------------------------------------*/
package org.xmlBlaster.client;


/**
 * This class encapsulates the Message meta data and unique identifier (key) of a get() message. 
 * <p />
 * Currently it is implemented identical to PublishKeyWrapper.java, see there for javadoc info.
 */
public class GetKeyWrapper extends PublishKeyWrapper
{
   /**
    * @see org.xmlBlaster.client.PublishKeyWrapper
    */
   public GetKeyWrapper(String oid, String contentMime, String contentMimeExtended)
   {
      super(oid, contentMime, contentMimeExtended);
   }
}
