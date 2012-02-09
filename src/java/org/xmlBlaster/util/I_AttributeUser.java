/*------------------------------------------------------------------------------
Name:      I_AttributeUser.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/
package org.xmlBlaster.util;

/**
 * @author michele@laghi.eu
 */
public interface I_AttributeUser {

   /**
    * @param key
    * @param value ${xy} are replaced since xmlBlaster v2.2
    */
   void addAttribute(String key, String value);
   
   /**
    * 
    * @param key
    * @param value
    * @param replacePlaceHolder if true ${xy} are replaced
    */
   void addAttribute(String key, String value, boolean replacePlaceHolder);
   
   /**
    * When the attribute is written to a string in the toXml methods it is wrapped inside a CDATA in case
    * you pass 'true' here.
    */
   void wrapAttributeInCDATA(String attributeKey);

   /**
    * When the attribute is written to a string in the toXml methods it is wrapped inside a CDATA. This can
    * be undone if you pass 'true' here.
    */
   void unwrapAttributeFromCDATA(String attributeKey);
}
   
