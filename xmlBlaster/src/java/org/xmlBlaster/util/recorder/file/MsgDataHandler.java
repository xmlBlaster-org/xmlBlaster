/*------------------------------------------------------------------------------
Name:      MsgDataHandler.java
Project:   xmlBlaster.org
Comment:   Read/write messages to/from harddisk
------------------------------------------------------------------------------*/
package org.xmlBlaster.util.recorder.file;

import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.engine.helper.Constants;
import org.xmlBlaster.engine.helper.MessageUnit;

import java.io.RandomAccessFile;
import java.io.IOException;

/**
 * Reads/writes publish()/subscribe() etc. data to/from disk. 
 * <p />
 * The two methods to the marshalling of message data.
 */
final class MsgDataHandler implements I_UserDataHandler
{
   private final String ME = "MsgDataHandler";

   public MsgDataHandler(Global glob) {
   }

   public final void writeData(final RandomAccessFile ra, final Object userData) throws IOException, XmlBlasterException {
      
      RequestContainer cont = (RequestContainer)userData;

      ra.writeUTF(cont.method);
      
      if (cont.method.equals(Constants.PUBLISH_ARR) || cont.method.equals(Constants.PUBLISH_ONEWAY) ||
               cont.method.equals(Constants.UPDATE) || cont.method.equals(Constants.UPDATE_ONEWAY)) {

         if (cont.method.equals(Constants.UPDATE) || cont.method.equals(Constants.UPDATE_ONEWAY)) {
            ra.writeUTF(cont.cbSessionId);
         }
         ra.writeInt(cont.msgUnitArr.length);
         for (int i=0;i<cont.msgUnitArr.length;i++) {
            ra.writeUTF(cont.msgUnitArr[i].getXmlKey());
            ra.writeInt(cont.msgUnitArr[i].getContent().length);
            ra.write(cont.msgUnitArr[i].getContent());
            ra.writeUTF(cont.msgUnitArr[i].getQos());
         }
      }
      else if (cont.method.equals(Constants.SUBSCRIBE) || cont.method.equals(Constants.UNSUBSCRIBE) ||
               cont.method.equals(Constants.ERASE) || cont.method.equals(Constants.GET)) {
         ra.writeUTF(cont.xmlKey);
         ra.writeUTF(cont.xmlQos);
      }
      else if (cont.method.equals(Constants.PUBLISH)) {
         ra.writeUTF(cont.msgUnit.getXmlKey());
         ra.writeInt(cont.msgUnit.getContent().length);
         ra.write(cont.msgUnit.getContent());
         ra.writeUTF(cont.msgUnit.getQos());
      }
      else if (cont.method.equals(Constants.CONNECT) || cont.method.equals(Constants.DISCONNECT) ||
               cont.method.equals(Constants.PING)) {
         ra.writeUTF(cont.xmlQos);
      }
      else
         throw new XmlBlasterException(ME, "Internal problem: Unknown method '" + cont.method + "' can't handle it");
   }

   public final Object readData(final RandomAccessFile ra) throws IOException, XmlBlasterException {

      RequestContainer cont = new RequestContainer();
      String key;
      byte[] content;

      cont.method = ra.readUTF();

      if (cont.method.equals(Constants.PUBLISH_ARR) || cont.method.equals(Constants.PUBLISH_ONEWAY) ||
               cont.method.equals(Constants.UPDATE) || cont.method.equals(Constants.UPDATE_ONEWAY)) {

         if (cont.method.equals(Constants.UPDATE) || cont.method.equals(Constants.UPDATE_ONEWAY)) {
            cont.cbSessionId = ra.readUTF();
         }
         cont.msgUnitArr = new MessageUnit[ra.readInt()];
         for (int i=0; i<cont.msgUnitArr.length; i++) {
            key = ra.readUTF();
            content = new byte[ra.readInt()];
            ra.read(content);
            cont.msgUnitArr[i] = new MessageUnit(key, content, ra.readUTF());
         }
      }
      else if (cont.method.equals(Constants.SUBSCRIBE) || cont.method.equals(Constants.UNSUBSCRIBE) ||
               cont.method.equals(Constants.ERASE) || cont.method.equals(Constants.GET)) {
         cont.xmlKey = ra.readUTF();
         cont.xmlQos = ra.readUTF();
      }
      else if (cont.method.equals(Constants.PUBLISH)) { 
         key = ra.readUTF();
         content = new byte[ra.readInt()];
         ra.read(content);
         cont.msgUnit = new MessageUnit(key, content, ra.readUTF());
      }
      else if (cont.method.equals(Constants.CONNECT) || cont.method.equals(Constants.DISCONNECT) || cont.method.equals(Constants.PING)) {
         cont.xmlQos = ra.readUTF();
      }
      else
         throw new XmlBlasterException(ME, "Internal problem: Unknown method '" + cont.method + "' can't handle it");

      return cont;
   }
}
