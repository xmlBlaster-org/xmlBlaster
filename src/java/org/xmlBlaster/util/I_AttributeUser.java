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

   void addAttribute(String key, String value);
   
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
   
