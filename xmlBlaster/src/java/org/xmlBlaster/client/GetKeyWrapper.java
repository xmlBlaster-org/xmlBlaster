/*------------------------------------------------------------------------------
Name:      GetKeyWrapper.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/
package org.xmlBlaster.client;

import org.xmlBlaster.util.XmlBlasterException;


/**
 * @deprecated Please use org.xmlBlaster.client.key.GetKey
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
