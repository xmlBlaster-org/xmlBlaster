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

import java.util.Hashtable;
import java.util.Enumeration;
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
   private Hashtable added, removed, changed;
   private LogChannel log;

   public MessageContent(LogChannel log, Hashtable added, Hashtable changed, Hashtable removed) {
      this.log     = log;
      this.added   = added;
      this.changed = changed;
      this.removed = removed;
   }

   public MessageContent() {
      this(null, null, null, null);
      this.log = Global.instance().getLog("graphical");
   }


   private void writeSeries(Hashtable table, StorableOutput out) {
      if (this.log.CALL) this.log.call(ME, "writeSeries");
      Enumeration enum = table.keys();
      while (enum.hasMoreElements()) {
         String uniqueId = (String)enum.nextElement();
         Figure fig = (Figure)table.get(uniqueId);
         out.writeString(uniqueId);
         out.writeStorable(fig);
      }
   }

   private Hashtable readSeries(int numOfEntries, StorableInput in) throws IOException {
      if (this.log.CALL) this.log.call(ME, "readSeries");
      Hashtable ret = new Hashtable();
      for (int i=0; i < numOfEntries; i++) {
         String uniqueId = in.readString();
         Storable obj = in.readStorable();
         if (obj instanceof XmlBlasterDrawing) {
            log.warn(ME, "Storable is instance of Drawing, we ignore it: " + obj.getClass().getName());
            continue;
         }
         ret.put(uniqueId, obj);
      }
      return ret;
   }

   public void write(StorableOutput out) {
      if (this.log.CALL) this.log.call(ME, "write");
      // size of added, changed, removed
      out.writeInt(this.added.size());
      out.writeInt(this.changed.size());
      out.writeInt(this.removed.size());
      this.log.info(ME, "read: added size: " + added.size() + ", changed size: " + changed.size() + ", removed size: " + removed.size());
      writeSeries(this.added, out);
      writeSeries(this.changed, out);
      writeSeries(this.removed, out);
   }

   public void read(StorableInput in) throws IOException {
      if (this.log.CALL) this.log.call(ME, "read");
      int addedSize = in.readInt();
      int changedSize = in.readInt();
      int removedSize = in.readInt();
      this.log.info(ME, "read: added size: " + addedSize + ", changed size: " + changedSize + ", removed size: " + removedSize);
      this.added = readSeries(addedSize, in);
      this.changed = readSeries(changedSize, in);
      this.removed = readSeries(removedSize, in);
   }

   public Hashtable getAdded() {
      return this.added;
   }

   public Hashtable getChanged() {
      return this.changed;
   }

   public Hashtable getRemoved() {
      return this.removed;
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




