/*------------------------------------------------------------------------------
Name:      MessageContent.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/

package javaclients.graphical;

import CH.ifa.draw.framework.Figure;
import CH.ifa.draw.util.Storable;
import CH.ifa.draw.util.StorableInput;
import CH.ifa.draw.util.StorableOutput;

import java.io.IOException;
import java.io.ByteArrayOutputStream;
import java.io.ByteArrayInputStream;

import org.jutils.log.LogChannel;
import org.xmlBlaster.util.Global;

/**
 * @author <a href="mailto:laghi@swissinfo.org">Michele Laghi</a>
 */
public class MessageContent implements Storable {

   private final static String ME = "MessageContent";
   private Figure fig;
   private String figId;
   private LogChannel log;

   public MessageContent(LogChannel log, String figId, Figure fig) {
      this.log = log;
      this.fig = fig;
      this.figId = figId;
   }

   public MessageContent() {
      this(null, null, null);
      this.log = Global.instance().getLog("graphical");
   }

   public void write(StorableOutput out) {
      if (this.log.CALL) this.log.call(ME, "write");
      out.writeString(this.figId);
      out.writeStorable(this.fig);
   }

   public void read(StorableInput in) throws IOException {
      if (this.log.CALL) this.log.call(ME, "read");
      this.figId = in.readString();
      this.fig = (Figure)in.readStorable();
   }

   public String getFigureId() {
      return this.figId;
   }

   public Figure getFigure() {
      return this.fig;
   }

   public byte[] toBytes() throws IOException {
      if (this.log.CALL) this.log.call(ME, "toBytes");
      ByteArrayOutputStream byteStream = new ByteArrayOutputStream();
      StorableOutput out = new StorableOutput(byteStream);
      out.writeStorable(this);
      out.close();
      if (this.log.TRACE) 
         this.log.trace(ME, "toBytes: the content length : " + byteStream.toByteArray().length);
      return byteStream.toByteArray();
   }

   public static MessageContent fromBytes(byte[] bytes) throws IOException {
      System.out.println("fromBytes invoked");
      ByteArrayInputStream byteStream = new ByteArrayInputStream(bytes);
      StorableInput in = new StorableInput(byteStream);
      return (MessageContent)in.readStorable();
   }

}
