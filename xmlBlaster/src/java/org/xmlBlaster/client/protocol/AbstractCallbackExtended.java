/*------------------------------------------------------------------------------
Name:      AbstractCallbackExtended.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Easly extended class for protocol-unaware xmlBlaster clients.
Version:   $Id: AbstractCallbackExtended.java,v 1.3 2000/10/18 20:45:42 ruff Exp $
------------------------------------------------------------------------------*/
package org.xmlBlaster.client.protocol;
import org.xmlBlaster.client.UpdateKey;
import org.xmlBlaster.client.UpdateQoS;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.Log;
import org.xmlBlaster.engine.helper.MessageUnit;


/**
 * This is a little abstract helper class which extends the I_CallbackExtended
 * interface to become suited for protocols like xml-rpc. Note that you need to
 * extend this class because one of the update methods is abstract.
 * <p>
 *
 * @version $Revision: 1.3 $
 * @author "Michele Laghi" <michele.laghi@attglobal.net>
 * @author <a href="mailto:ruff@swand.lake.de">Marcel Ruff</a>.
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

         // Now we know all about the received message, dump it or do some checks
         if (Log.DUMP) Log.dump("UpdateKey", "\n" + updateKey.printOn().toString());
         if (Log.DUMP) Log.dump("content", "\n" + new String(content));
         if (Log.DUMP) Log.dump("UpdateQoS", "\n" + updateQoS.printOn().toString());
         if (Log.TRACE) Log.trace(ME, "Received message [" + updateKey.getUniqueKey() + "] from publisher " + updateQoS.getSender());

         update(loginName, updateKey, content, updateQoS);
      }
      catch (XmlBlasterException e) {
         Log.error(ME + ".update", "Parsing error: " + e.toString());
         throw new XmlBlasterException("Parsing Error", "check the key passed" + e.toString());
      }
   }


   /**
    * This is the callback method invoked natively
    * informing the client in an asynchronous mode about new messages.
    * <p />
    * It implements the interface I_CallbackRaw, used for example by the
    * InvocationRecorder
    * <p />
    * It nicely converts the raw MessageUnit with raw Strings and arrays
    * in corresponding objects and calls for every received message
    * the I_Callback.update(), which you need to implement in your code.
    *
    * @param msgUnitArr Contains MessageUnit structs (your message) in native form
    */
   public void update(String loginName, MessageUnit [] msgUnitArr) throws XmlBlasterException
   {
      if (msgUnitArr == null) {
         Log.warn(ME, "Entering update() with null array.");
         return;
      }
      if (msgUnitArr.length == 0) {
         Log.warn(ME, "Entering update() with 0 messages.");
         return;
      }
      if (Log.CALL) Log.call(ME, "Receiving update of " + msgUnitArr.length + " messages ...");

      for (int ii=0; ii<msgUnitArr.length; ii++) {
         MessageUnit msgUnit = msgUnitArr[ii];
         update(loginName, msgUnit.xmlKey, msgUnit.content, msgUnit.qos);
      }
   }


   /**
    * The class which extends AbstractCallbackExtended must implement this
    * method.
    * <p />
    * You receive one message, which is completely parsed and checked.
    *
    * @param loginName The name to whom the callback belongs
    * @param updateKey The arrived key (as an xml-string)
    * @param content   The arrived message content
    * @param updateQos  Quality of Service of the MessageUnit
    *                 (as an xml-string)
    * @see org.xmlBlaster.client.I_Callback
    */
   public abstract void update(String loginName, UpdateKey updateKey, byte[] content,
                               UpdateQoS updateQoS) throws XmlBlasterException;
}

