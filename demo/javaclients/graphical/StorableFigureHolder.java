/*------------------------------------------------------------------------------
Name:      StorableFigureHolder.java
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

import java.util.logging.Logger;
import java.util.logging.Level;

/**
 * This is a placeholder for the Figure when it is sent over xmlBlaster
 * @author <a href="mailto:michele@laghi.eu">Michele Laghi</a>
 */
public class StorableFigureHolder implements Storable {

   private final static String ME = "MessageContent";
   private Figure fig;
   private String figId;
   private String toFront;
   private static Logger log = Logger.getLogger(StorableFigureHolder.class.getName());

   /**
    * We pass the used own defined attributes here since for LineFigure these are not 
    * serialized internally
    * @param log
    * @param fig
    */
   public StorableFigureHolder(Figure fig, String figId, String toFront) {
      StorableFigureHolder.log = log;
      this.fig = fig;
      this.figId = figId;
      this.toFront = toFront;
   }

   public StorableFigureHolder() {
      this(null, null, null);

   }

   public void write(StorableOutput out) {
      if (this.figId != null) out.writeString(this.figId);
      else  out.writeString("");
      if (this.toFront != null) out.writeString(this.toFront);
      else  out.writeString("");
      out.writeStorable(this.fig);
   }

   public void read(StorableInput in) throws IOException {
      this.figId = in.readString();
      this.toFront = in.readString();
      this.fig = (Figure)in.readStorable();
      if (this.figId.length() < 1) this.figId = null;
      if (this.toFront.length() < 1) this.toFront = null;
   }

   public String getFigureId() {
      return this.figId;
   }

   public String getToFront() {
      return this.toFront;
   }

   public void setFigureId(String figId) {
      this.figId = figId;
   }

   public void setToFront(String toFront) {
      this.toFront = toFront;
   }

   public Figure getFigure() {
      if (log.isLoggable(Level.FINE)) {
         log.fine("getFigure: figureId='" + this.figId + 
                                      "' toFront='" + this.toFront +
                                      "' toFront='" + this.fig.displayBox().x +
                                      "' toFront='" + this.fig.displayBox().y +
                                      "' toFront='" + this.fig.displayBox().width +
                                      "' toFront='" + this.fig.displayBox().height + "'");
      }
      return this.fig;
   }

   public byte[] toBytes() throws IOException {
      ByteArrayOutputStream byteStream = new ByteArrayOutputStream();
      StorableOutput out = new StorableOutput(byteStream);
      out.writeStorable(this);
      out.close();
      return byteStream.toByteArray();
   }

   public static StorableFigureHolder fromBytes(byte[] bytes) throws IOException {
      ByteArrayInputStream byteStream = new ByteArrayInputStream(bytes);
      StorableInput in = new StorableInput(byteStream);
      return (StorableFigureHolder)in.readStorable();
   }


   /*
    private void recursiveErase(Figure fig) throws XmlBlasterException {
      erase(fig);
      FigureEnumeration iter = fig.figures();
      while (iter.hasNextFigure()) {
         Figure child = iter.nextFigure();
         log.severe("recursiveErase " + child);
         recursiveErase(child);
      }
   }
    */

}
