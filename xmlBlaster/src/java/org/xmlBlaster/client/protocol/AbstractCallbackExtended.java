/*------------------------------------------------------------------------------
Name:      AbstractCallbackExtended.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Easly extended class for protocol-unaware xmlBlaster clients.
Version:   $Id: AbstractCallbackExtended.java,v 1.1 2000/08/30 00:21:58 laghi Exp $
------------------------------------------------------------------------------*/
package org.xmlBlaster.client.protocol;
import org.xmlBlaster.client.UpdateKey;
import org.xmlBlaster.client.UpdateQoS;
import org.xmlBlaster.util.XmlBlasterException;
import org.jutils.log.LogManager;
/**
 * This is a little abstract helper class which extends the I_CallbackExtended
 * interface to become suited for protocols like xml-rpc. Note that you need to
 * extend this class because one of the update methods is abstract.
 * <p>
 *
 * @version $Revision: 1.1 $
 * @author "Michele Laghi" <michele.laghi@attglobal.net>
 */
public abstract class AbstractCallbackExtended implements I_CallbackExtended
{
   private String ME = "AbstractCallbackExtended";
   /**
    * The constructor does nothing.
    */
   public AbstractCallbackExtended ()
   {
   }


   /**
    * It parses the string literals passed in the argument list and calls
    * subsequently the update method with the signature defined in I_Callback.
    * <p>
    * This method is invoked by certain protocols only. Others might directly
    * invoke the update method with the other signature.
    *
    * @param loginName The name to whom the callback belongs
    * @param updateKeyLiteral The arrived key (as an xml-string)
    * @param content   The arrived message content
    * @param updateQosLiteral  Quality of Service of the MessageUnit 
    *                      (as an xml-string)
    * @see I_CallbackExtended
    */
   public void update(String loginName, String updateKeyLiteral, byte[] content, 
                      String updateQoSLiteral) throws XmlBlasterException 
   {
      try {
         UpdateKey updateKey = new UpdateKey();
         updateKey.init(updateKeyLiteral); // does the parsing
         UpdateQoS updateQoS = new UpdateQoS(updateQoSLiteral); // does the parsing
         update(loginName, updateKey, content, updateQoS);
      }
      catch (XmlBlasterException e) {
         LogManager.error("xmlBlaster", ME + ".update", "Parsing error: " + e.toString());
         throw new XmlBlasterException("Parsing Error", "check the key passed" + e.toString());
      }
   }

   
   /**
    * The class which extends AbstractCallbackExtended must implement this 
    * method.
    *
    * @param loginName The name to whom the callback belongs
    * @param updateKey The arrived key (as an xml-string)
    * @param content   The arrived message content
    * @param updateQos  Quality of Service of the MessageUnit 
    *                 (as an xml-string)
    * @see org.xmlBlaster.client.I_Callback
    */
   public abstract void update(String loginName, UpdateKey updateKey, byte[] content, 
                               UpdateQoS updateQoS);
}

