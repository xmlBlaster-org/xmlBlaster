/*------------------------------------------------------------------------------
Name:      MsgDataHandler.java
Project:   xmlBlaster.org
Comment:   Read/write messages to/from harddisk
------------------------------------------------------------------------------*/
package org.xmlBlaster.util.recorder.file;

import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.def.MethodName;
import org.xmlBlaster.util.MsgUnit;

import java.io.RandomAccessFile;
import java.io.IOException;

/**
 * Reads/writes publish()/subscribe() etc. data to/from disk. 
 * <p />
 * The two methods to the marshalling of message data.
 */
final class MsgDataHandler implements I_UserDataHandler
{
   private final Global glob;
   private final String ME = "MsgDataHandler";

   public MsgDataHandler(Global glob) {
      this.glob = glob;
   }

   public final void writeData(final RandomAccessFile ra, final Object userData) throws IOException, XmlBlasterException {
      
      RequestContainer cont = (RequestContainer)userData;

      ra.writeUTF(cont.method.getMethodName());
      
      if (cont.method.wantsMsgArrArg() || cont.method.wantsStrMsgArrArg()) { // e.g. MethodName.PUBLISH

         if (cont.method.wantsStrMsgArrArg()) { // UPDATE and UPDATE_ONEWAY
            ra.writeUTF(cont.cbSessionId);
         }
         ra.writeInt(cont.msgUnitArr.length);
         for (int i=0;i<cont.msgUnitArr.length;i++) {
            ra.writeUTF(cont.msgUnitArr[i].getKey());
            ra.writeInt(cont.msgUnitArr[i].getContent().length);
            ra.write(cont.msgUnitArr[i].getContent());
            ra.writeUTF(cont.msgUnitArr[i].getQos());
         }
      }
      else if (cont.method.wantsKeyQosArg()) { // e.g. MethodName.SUBSCRIBE)
         ra.writeUTF(cont.xmlKey);
         ra.writeUTF(cont.xmlQos);
      }
      else if (cont.method.wantsQosArg()) { // e.g. MethodName.CONNECT
         ra.writeUTF(cont.xmlQos);
      }
      else
         throw new XmlBlasterException(ME, "Internal problem: Unknown method '" + cont.method + "' can't handle it");
   }

   public final Object readData(final RandomAccessFile ra) throws IOException, XmlBlasterException {

      RequestContainer cont = new RequestContainer();
      String key;
      byte[] content;

      cont.method = MethodName.toMethodName(ra.readUTF());

      if (cont.method.wantsMsgArrArg() || cont.method.wantsStrMsgArrArg()) { // e.g. MethodName.PUBLISH

         if (cont.method.wantsStrMsgArrArg()) { // UPDATE and UPDATE_ONEWAY
            cont.cbSessionId = ra.readUTF();
         }
         cont.msgUnitArr = new MsgUnit[ra.readInt()];
         for (int i=0; i<cont.msgUnitArr.length; i++) {
            key = ra.readUTF();
            content = new byte[ra.readInt()];
            ra.read(content);
            cont.msgUnitArr[i] = new MsgUnit(glob, key, content, ra.readUTF());
         }
      }
      else if (cont.method.wantsKeyQosArg()) { // e.g. MethodName.SUBSCRIBE)
         cont.xmlKey = ra.readUTF();
         cont.xmlQos = ra.readUTF();
      }
      else if (cont.method.wantsQosArg()) { // e.g. MethodName.CONNECT
         cont.xmlQos = ra.readUTF();
      }
      else
         throw new XmlBlasterException(ME, "Internal problem: Unknown method '" + cont.method + "' can't handle it");

      return cont;
   }
}
