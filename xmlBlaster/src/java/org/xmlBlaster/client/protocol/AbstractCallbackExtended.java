/*------------------------------------------------------------------------------
Name:      AbstractCallbackExtended.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Easly extended class for protocol-unaware xmlBlaster clients.
Version:   $Id: AbstractCallbackExtended.java,v 1.12 2002/09/18 16:37:54 laghi Exp $
------------------------------------------------------------------------------*/
package org.xmlBlaster.client.protocol;

import org.jutils.log.LogChannel;

import org.xmlBlaster.client.UpdateKey;
import org.xmlBlaster.client.UpdateQos;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.Global;
import org.xmlBlaster.authentication.plugins.I_ClientPlugin;
import org.xmlBlaster.engine.helper.MessageUnit;


/**
 * This is a little abstract helper class which extends the I_CallbackExtended
 * interface to become suited for protocols like xml-rpc. Note that you need to
 * extend this class because one of the update methods is abstract.
 * <p>
 *
 * @version $Revision: 1.12 $
 * @author <a href="mailto:laghi@swissinfo.org">Michele Laghi</a>
 * @author <a href="mailto:ruff@swand.lake.de">Marcel Ruff</a>.
 */
public abstract class AbstractCallbackExtended implements I_CallbackExtended
{
   private String ME = "AbstractCallbackExtended";
   protected final Global glob;
   protected final LogChannel log;

   /**
    * The constructor does nothing.
    */
   public AbstractCallbackExtended(Global glob)
   {
      this.glob = glob;
      this.log = glob.getLog(null);
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
                      String updateQosLiteral) throws XmlBlasterException
   {
      I_ClientPlugin secPlgn = getSecurityPlugin();
      if (secPlgn != null) {
         updateKeyLiteral = secPlgn.importMessage(updateKeyLiteral);
         content = secPlgn.importMessage(content);
         updateQosLiteral = secPlgn.importMessage(updateQosLiteral);
      }
      try {
         UpdateKey updateKey = new UpdateKey(glob, updateKeyLiteral);
         //updateKey.init(updateKeyLiteral); // does the parsing
         UpdateQos updateQos = new UpdateQos(glob, updateQosLiteral); // does the parsing

         // Now we know all about the received message, dump it or do some checks
         /*
         if (log.DUMP) log.dump(ME+".UpdateKey", "\n" + updateKey.toXml());
         if (log.DUMP) log.dump(ME+".content", "\n" + new String(content));
         if (log.DUMP) log.dump(ME+".UpdateQos", "\n" + updateQos.toXml());
         */
         if (log.TRACE) log.trace(ME, "Received message [" + updateKey.getUniqueKey() + "] from publisher " + updateQos.getSender());

         return update(cbSessionId, updateKey, content, updateQos);
      }
      catch (XmlBlasterException e) {
         log.error(ME + ".update", "Parsing error: " + e.toString());
         throw new XmlBlasterException("Parsing Error", "check the key passed" + e.toString());
      }
   }

   /**
    * The oneway variant without a return value or exception
    * <p />
    * We match it to the blocking variant. Implement this in your code on demand.
    */
   public void updateOneway(String cbSessionId, String updateKeyLiteral, byte[] content, String updateQosLiteral)
   {
      try {
         update(cbSessionId, updateKeyLiteral, content, updateQosLiteral);
      }
      catch (Throwable e) {
         log.error(ME, "Caught exception, can't deliver it to xmlBlaster server as we are in oneway mode: " + e.toString());
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
         log.warn(ME, "Entering update() with null array.");
         return new String[0];
      }
      if (msgUnitArr.length == 0) {
         log.warn(ME, "Entering update() with 0 messages.");
         return new String[0];
      }
      if (log.CALL) log.call(ME, "Receiving update of " + msgUnitArr.length + " messages ...");

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
         log.error(ME, "Caught exception, can't deliver it to xmlBlaster server as we are in oneway mode: " + e.toString());
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
                               UpdateQos updateQos) throws XmlBlasterException;
}

