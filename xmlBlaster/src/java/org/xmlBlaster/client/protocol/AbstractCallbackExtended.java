/*------------------------------------------------------------------------------
Name:      AbstractCallbackExtended.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/
package org.xmlBlaster.client.protocol;

import org.jutils.log.LogChannel;

import org.xmlBlaster.client.key.UpdateKey;
import org.xmlBlaster.client.qos.UpdateQos;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.enum.ErrorCode;
import org.xmlBlaster.util.Global;
import org.xmlBlaster.authentication.plugins.I_ClientPlugin;
import org.xmlBlaster.util.MsgUnitRaw;


/**
 * This is a little abstract helper class which extends the I_CallbackExtended
 * interface to become suited for protocols like xml-rpc. Note that you need to
 * extend this class because one of the update methods is abstract.
 * <p>
 *
 * @version $Revision: 1.15 $
 * @author <a href="mailto:laghi@swissinfo.org">Michele Laghi</a>
 * @author <a href="mailto:xmlBlaster@marcelruff.info">Marcel Ruff</a>.
 */
public abstract class AbstractCallbackExtended implements I_CallbackExtended
{
   private String ME = "AbstractCallbackExtended";
   protected final Global glob;
   protected final LogChannel log;

   /**
    * @param glob If null we use Global.instance()
    */
   public AbstractCallbackExtended(Global glob) {
      this.glob = (glob==null) ? Global.instance() : glob;
      this.log = glob.getLog("client");
   }

   public abstract I_ClientPlugin getSecurityPlugin();

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
    * @param updateQosLiteral  Quality of Service of the MsgUnitRaw
    *                      (as an xml-string)
    * @see I_CallbackExtended
    */
   public String update(String cbSessionId, String updateKeyLiteral, byte[] content,
                      String updateQosLiteral) throws XmlBlasterException
   {
      // import (decrypt) message
      I_ClientPlugin secPlgn = getSecurityPlugin();
      if (secPlgn != null) {
         updateKeyLiteral = secPlgn.importMessage(updateKeyLiteral);
         content = secPlgn.importMessage(content);
         updateQosLiteral = secPlgn.importMessage(updateQosLiteral);
      }

      // parse XML key and QoS
      UpdateKey updateKey = null;
      UpdateQos updateQos = null;
      try {
         updateKey = new UpdateKey(glob, updateKeyLiteral);
         //updateKey.init(updateKeyLiteral); // does the parsing
         updateQos = new UpdateQos(glob, updateQosLiteral); // does the parsing
      }
      catch (XmlBlasterException e) {
         log.error(ME + ".update", "Parsing error: " + e.toString());
         throw new XmlBlasterException(glob, ErrorCode.USER_UPDATE_ILLEGALARGUMENT, ME+".update", "Parsing error", e);
      }

      // invoke client code
      try {
         // Now we know all about the received message, dump it or do some checks
         /*
         if (log.DUMP) log.dump(ME+".UpdateKey", "\n" + updateKey.toXml());
         if (log.DUMP) log.dump(ME+".content", "\n" + new String(content));
         if (log.DUMP) log.dump(ME+".UpdateQos", "\n" + updateQos.toXml());
         */
         if (log.TRACE) log.trace(ME, "Received message [" + updateKey.getOid() + "] from publisher " + updateQos.getSender());

         String ret = update(cbSessionId, updateKey, content, updateQos);

         // export (encrypt) return value
         if (secPlgn != null) {
            ret = secPlgn.exportMessage(ret);
         }

         return ret;
      }
      catch (XmlBlasterException e) {
         throw e;
      }
      catch (Throwable e) {
         log.warn(ME + ".update", "Error in client user code of update(): " + e.toString());
         e.printStackTrace();
         throw new XmlBlasterException(glob, ErrorCode.USER_UPDATE_INTERNALERROR, ME+".update", "Error in client code", e);
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
    * It nicely converts the raw MsgUnitRaw with raw Strings and arrays
    * in corresponding objects and calls for every received message
    * the I_Callback.update(), which you need to implement in your code.
    *
    * @param msgUnitArr Contains MsgUnitRaw structs (your message) in native form
    */
   public String[] update(String cbSessionId, MsgUnitRaw [] msgUnitArr) throws XmlBlasterException
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
         MsgUnitRaw msgUnit = msgUnitArr[ii];
         retArr[ii] = update(cbSessionId, msgUnit.getKey(), msgUnit.getContent(), msgUnit.getQos());
      }
      return retArr;
   }

   /**
    * The oneway variant without a return value or exception
    */
   public void updateOneway(String cbSessionId, org.xmlBlaster.util.MsgUnitRaw[] msgUnitArr)
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
    * @param updateQos   Quality of Service of the MsgUnitRaw as an xml-string
    * @see org.xmlBlaster.client.I_Callback
    */
   public abstract String update(String cbSessionId, UpdateKey updateKey, byte[] content,
                               UpdateQos updateQos) throws XmlBlasterException;
}

