/*------------------------------------------------------------------------------
Name:      AbstractCallbackExtended.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Easly extended class for protocol-unaware xmlBlaster clients.
Version:   $Id: AbstractCallbackExtended.java,v 1.8 2002/03/18 00:29:28 ruff Exp $
------------------------------------------------------------------------------*/
package org.xmlBlaster.client.protocol;
import org.xmlBlaster.client.UpdateKey;
import org.xmlBlaster.client.UpdateQoS;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.Log;
import org.xmlBlaster.authentication.plugins.I_ClientPlugin;
import org.xmlBlaster.engine.helper.MessageUnit;


/**
 * This is a little abstract helper class which extends the I_CallbackExtended
 * interface to become suited for protocols like xml-rpc. Note that you need to
 * extend this class because one of the update methods is abstract.
 * <p>
 *
 * @version $Revision: 1.8 $
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

   abstract I_ClientPlugin getSecurityPlugin();

   /**
    * It parses the string literals passed in the argument list and calls
    * subsequently the update method with the signature defined in I_Callback.
    * <p>
    * This method is invoked by certain protocols only. Others might directly
    * invoke the update method with the other signature.
    *
    * @param cbSessionId The session ID specified by the client which registered the callback
    * @param updateKeyLiteral The arrived key (as an xml-string)
    * @param content   The arrived message content
    * @param updateQosLiteral  Quality of Service of the MessageUnit
    *                      (as an xml-string)
    * @see I_CallbackExtended
    */
   public String update(String cbSessionId, String updateKeyLiteral, byte[] content,
                      String updateQoSLiteral) throws XmlBlasterException
   {
      I_ClientPlugin secPlgn = getSecurityPlugin();
      if (secPlgn != null) {
         updateKeyLiteral = secPlgn.importMessage(updateKeyLiteral);
         content = secPlgn.importMessage(content);
         updateQoSLiteral = secPlgn.importMessage(updateQoSLiteral);
      }
      try {
         UpdateKey updateKey = new UpdateKey();
         updateKey.init(updateKeyLiteral); // does the parsing
         UpdateQoS updateQoS = new UpdateQoS(updateQoSLiteral); // does the parsing

         // Now we know all about the received message, dump it or do some checks
         if (Log.DUMP) Log.dump("UpdateKey", "\n" + updateKey.toXml());
         if (Log.DUMP) Log.dump("content", "\n" + new String(content));
         if (Log.DUMP) Log.dump("UpdateQoS", "\n" + updateQoS.toXml());
         if (Log.TRACE) Log.trace(ME, "Received message [" + updateKey.getUniqueKey() + "] from publisher " + updateQoS.getSender());

         return update(cbSessionId, updateKey, content, updateQoS);
      }
      catch (XmlBlasterException e) {
         Log.error(ME + ".update", "Parsing error: " + e.toString());
         throw new XmlBlasterException("Parsing Error", "check the key passed" + e.toString());
      }
   }

   /**
    * The oneway variant without a return value or exception
    * <p />
    * We match it to the blocking variant. Implement this in your code on demand.
    */
   public void updateOneway(String cbSessionId, String updateKeyLiteral, byte[] content, String updateQoSLiteral)
   {
      try {
         update(cbSessionId, updateKeyLiteral, content, updateQoSLiteral);
      }
      catch (Throwable e) {
         Log.error(ME, "Caught exception, can't deliver it to xmlBlaster server as we are in oneway mode: " + e.toString());
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
   public String[] update(String cbSessionId, MessageUnit [] msgUnitArr) throws XmlBlasterException
   {
      if (msgUnitArr == null) {
         Log.warn(ME, "Entering update() with null array.");
         return new String[0];
      }
      if (msgUnitArr.length == 0) {
         Log.warn(ME, "Entering update() with 0 messages.");
         return new String[0];
      }
      if (Log.CALL) Log.call(ME, "Receiving update of " + msgUnitArr.length + " messages ...");

      String[] retArr = new String[msgUnitArr.length];
      for (int ii=0; ii<msgUnitArr.length; ii++) {
         MessageUnit msgUnit = msgUnitArr[ii];
         retArr[ii] = update(cbSessionId, msgUnit.xmlKey, msgUnit.content, msgUnit.qos);
      }
      return retArr;
   }

   /**
    * The oneway variant without a return value or exception
    */
   public void updateOneway(String cbSessionId, org.xmlBlaster.engine.helper.MessageUnit[] msgUnitArr)
   {
      try {
         update(cbSessionId, msgUnitArr);
      }
      catch (Throwable e) {
         Log.error(ME, "Caught exception, can't deliver it to xmlBlaster server as we are in oneway mode: " + e.toString());
      }
   }

   /**
    * The class which extends AbstractCallbackExtended must implement this
    * method.
    * <p />
    * You receive one message, which is completely parsed and checked.
    *
    * @param cbSessionId The session ID specified by the client which registered the callback
    * @param updateKey   The arrived key (as an xml-string)
    * @param content     The arrived message content
    * @param updateQos   Quality of Service of the MessageUnit as an xml-string
    * @see org.xmlBlaster.client.I_Callback
    */
   public abstract String update(String cbSessionId, UpdateKey updateKey, byte[] content,
                               UpdateQoS updateQoS) throws XmlBlasterException;
}

