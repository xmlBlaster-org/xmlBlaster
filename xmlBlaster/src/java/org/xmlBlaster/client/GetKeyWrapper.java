/*------------------------------------------------------------------------------
Name:      GetKeyWrapper.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Handling one xmlKey
Version:   $Id: GetKeyWrapper.java,v 1.3 2002/04/19 11:07:41 ruff Exp $
------------------------------------------------------------------------------*/
package org.xmlBlaster.client;

import org.xmlBlaster.util.XmlBlasterException;


/**
 * This class encapsulates the Message meta data and unique identifier (key) of a get() message.
 * <p />
 * Currently it is implemented identical to SubscribeKeyWrapper.java, see there for javadoc info.
 */
public class GetKeyWrapper extends SubscribeKeyWrapper
{
   /**
    * @see org.xmlBlaster.client.SubscribeKeyWrapper
    */
   public GetKeyWrapper(String oid)
   {
      super(oid);
   }
   /**
    * @param queryString e.g. "//key"
    * @param queryType e.g. "XPATH"
    */
   public GetKeyWrapper(String queryString, String queryType) throws XmlBlasterException
   {
      super(queryString, queryType);
   }
}
