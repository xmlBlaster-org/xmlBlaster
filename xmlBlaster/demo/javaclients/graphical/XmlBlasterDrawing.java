/*------------------------------------------------------------------------------
Name:      GraphicChat.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/

package javaclients.graphical;

import CH.ifa.draw.framework.FigureChangeEvent;
import CH.ifa.draw.framework.Figure;
import CH.ifa.draw.standard.StandardDrawing;

import org.jutils.log.LogChannel;
import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.Timestamp;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.I_Timeout;
import org.xmlBlaster.util.Timeout;
import org.xmlBlaster.client.I_XmlBlasterAccess;
import org.xmlBlaster.client.qos.*;
import org.xmlBlaster.client.key.*;
import org.xmlBlaster.util.MsgUnit;

import org.xmlBlaster.client.I_Callback;

import java.util.Hashtable;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Enumeration;
import java.io.IOException;
import CH.ifa.draw.framework.*;


/**
 * @author <a href="mailto:laghi@swissinfo.org">Michele Laghi</a>
 */
public  class XmlBlasterDrawing extends StandardDrawing implements I_Timeout, I_Callback {

   private static final String ME = "XmlBlasterDrawing";
   private Global global;
   private LogChannel log;
   private boolean doRecord;
   /** key = timestamp, value = object reference (Figure) */
   private Hashtable timestampFigureTable; 
   /** key = object reference (Figure), value = timestamp */
   private Hashtable figureTimestampTable;
   private HashSet[] newAdded;
   private HashSet[] newRemoved;
   private HashSet[] newChanged;
   private int currentIndex = 0;
   private int publishIndex = 1;
   private Timeout timeout;
   private long publishDelay = 5000L;
   private I_XmlBlasterAccess access;
   private SubscribeReturnQos subRetQos;
   private ConnectReturnQos connectReturnQos;


   public XmlBlasterDrawing() {
      super();
      init(Global.instance());
   }

   public XmlBlasterDrawing(Global global) {
      super();
      init(global);
   }


   public void init(Global global) {
      this.global = global;
      this.log = this.global.getLog("graphical");
      this.doRecord = true;
      this.timestampFigureTable = new Hashtable();
      this.figureTimestampTable = new Hashtable();
      this.newAdded = new HashSet[2];
      this.newRemoved = new HashSet[2];
      this.newChanged = new HashSet[2];
      for (int i=0; i < 2; i++) {
         this.newAdded[i] = new HashSet();
         this.newRemoved[i] = new HashSet();
         this.newChanged[i] = new HashSet();
      }
      this.publishDelay = this.global.getProperty().get("publishDelay", 500L);
      this.timeout = new Timeout("PublishTimer");
      this.timeout.addTimeoutListener(this, this.publishDelay, this);
      initConnection();
   }


   public void initConnection() {
      try {
         this.access = this.global.getXmlBlasterAccess();
         ConnectQos qos = new ConnectQos(this.global);
         this.connectReturnQos = this.access.connect(qos, this);  // Login to xmlBlaster, register for updates
         SubscribeKey sk = new SubscribeKey(this.global, "GraphicChat");
         SubscribeQos sq = new SubscribeQos(this.global);
         sq.setWantLocal(false);
         sq.setWantInitialUpdate(false);
         this.access.subscribe(sk, sq);
      }
      catch (XmlBlasterException ex) {
         this.log.error(ME, "initConnection. Exception : " + ex.getMessage());
         ex.printStackTrace();
      }
   }

   synchronized public void shutdown() {
      try {
         this.access.unSubscribe(new UnSubscribeKey(this.global, "GraphicChat"), new UnSubscribeQos(this.global));
         this.access.disconnect(new DisconnectQos(this.global));
      }
      catch (XmlBlasterException ex) {
         this.log.error(ME, "shutdown. Exception : " + ex.getMessage());
      }
   }


   synchronized private void swapIndex() {
      if (this.currentIndex == 0) {
         this.currentIndex = 1; 
         this.publishIndex = 0;
      }
      else {
         this.currentIndex = 0;
         this.publishIndex = 1;
      }
   }

   synchronized public void figureChanged(FigureChangeEvent e) {
      super.figureChanged(e);
      if (this.doRecord) this.newChanged[this.currentIndex].add(e.getFigure());
      if (this.log.CALL) this.log.call(ME, "figureChanged " + e.getFigure());
   }

/*
   public void figureInvalidated(FigureChangeEvent e) {
      super.figureInvalidated(e);
//      if (this.log.CALL) this.log.call(ME, "figureInvalidated " + e.getFigure());
   }

   public void figureRemoved(FigureChangeEvent e) {
      super.figureRemoved(e);
      if (this.log.CALL) this.log.call(ME, "figureRemoved " + e.getFigure());
   }

   public void figureRequestRemove(FigureChangeEvent e) {
      super.figureRequestRemove(e);
      if (this.log.CALL) this.log.call(ME, "figureRequestRemove " + e.getFigure());
   }

   public void figureRequestUpdate(FigureChangeEvent e) {
      super.figureRequestUpdate(e);
      if (this.log.CALL) this.log.call(ME, "figureRequestUpdate");
   }
*/

   protected void add(String uniqueId, Figure figure) {
      synchronized (this.figureTimestampTable) {
         this.figureTimestampTable.put(figure, uniqueId);
         this.timestampFigureTable.put(uniqueId, figure);
      }
   }

   protected void remove(String uniqueId, Figure figure) {
      synchronized (this.figureTimestampTable) {
         if (figure != null) {
            this.figureTimestampTable.remove(figure);
         }
         if (uniqueId != null) {
            this.timestampFigureTable.remove(uniqueId);
         }
      }
   }



   synchronized public Figure add(Figure figure) {
      if (this.log.CALL) this.log.call(ME, "add");
      if (this.doRecord) {
         String uniqueId = this.global.getStrippedId() + "-" + (new Timestamp()).getTimestampLong();
         add(uniqueId, figure);
         this.newAdded[this.currentIndex].add(figure);
         if (this.log.TRACE) this.log.trace(ME, "add: adding '" + uniqueId + "'");
      }
      return super.add(figure);
   }

/*       
   public void addAll(FigureEnumeration fe) {
      if (this.log.CALL) this.log.call(ME, "addAll");
      super.addAll(fe);
   }
*/
   public void bringToFront(Figure figure) {
      if (this.log.CALL) this.log.call(ME, "bringToFront");
      super.bringToFront(figure);
   }

/*
   public void init(java.awt.Rectangle viewRectangle) {
      if (this.log.CALL) this.log.call(ME, "init");
      super.init(viewRectangle);
   }
*/

   synchronized public Figure orphan(Figure figure) {
      if (this.log.CALL) this.log.call(ME, "orphan");
      if (this.doRecord) {
         // the removing is handled by the timeout
         this.newRemoved[this.currentIndex].add(figure);

      }
      return super.orphan(figure);
   }

/*
   public void orphanAll(FigureEnumeration fe) {
      if (this.log.CALL) this.log.call(ME, "orphanAll");
      super.orphanAll(fe);
   }

   public void release() {
      if (this.log.CALL) this.log.call(ME, "release");
      super.release();
   }

   public Figure remove(Figure figure) {
      if (this.log.CALL) this.log.call(ME, "remove");
      return super.remove(figure);
   }

   public void removeAll(FigureEnumeration fe) {
      if (this.log.CALL) this.log.call(ME, "removeAll");
      super.removeAll(fe);
   }

   public Figure replace(Figure figure, Figure replacement) {
      if (this.log.CALL) this.log.call(ME, "replace");
      return super.replace(figure, replacement);
   }
*/

   public void sendToBack(Figure figure) {
      if (this.log.CALL) this.log.call(ME, "sendToBack");
      super.sendToBack(figure);
   }

   public void sendToLayer(Figure figure, int layerNr) {
      if (this.log.CALL) this.log.call(ME, "sendToLayer");
      super.sendToLayer(figure, layerNr);
   }

   public void setTitle(java.lang.String name) {
      if (this.log.CALL) this.log.call(ME, "");
      super.setTitle(name);
   }


   /**
    * Invoked by the timer
    */
   public void timeout(Object userData) {
      if (this.log.CALL) this.log.call(ME, "timeout");
      swapIndex();
      int addedSize = this.newAdded[this.publishIndex].size();
      int changedSize = this.newChanged[this.publishIndex].size();
      int removedSize = this.newRemoved[this.publishIndex].size();

      if (addedSize < 1 && changedSize < 1 && removedSize < 1) {
         this.timeout.addTimeoutListener(this, this.publishDelay, this);
         return;
      }


      Hashtable tmpAdded = new Hashtable();
      Hashtable tmpChanged = new Hashtable();
      Hashtable tmpRemoved = new Hashtable();

      if (addedSize > 0) {
         Iterator iter = this.newAdded[this.publishIndex].iterator();
         while (iter.hasNext()) {
            Figure fig = (Figure)iter.next();
            if (fig instanceof Drawing) continue;
            String uniqueId = (String)this.figureTimestampTable.get(fig);
            this.log.info(ME, "timeout: newAdded: " + fig + " " + uniqueId);
            tmpAdded.put(uniqueId, fig);
         }
         this.newAdded[this.publishIndex].clear();
      }

      if (changedSize > 0) {
         Iterator iter = this.newChanged[this.publishIndex].iterator();
         while (iter.hasNext()) {
            Figure fig = (Figure)iter.next();
				if (fig instanceof Drawing) continue;
            String uniqueId = (String)this.figureTimestampTable.get(fig);
            this.log.info(ME, "timeout: newChanged: " + fig + " " + uniqueId);
            tmpChanged.put(uniqueId, fig);
         }
         this.newChanged[this.publishIndex].clear();
      }
      if (removedSize > 0) {
         Iterator iter = this.newRemoved[this.publishIndex].iterator();
         while (iter.hasNext()) {
            Figure fig = (Figure)iter.next();
				if (fig instanceof Drawing) continue;
            String uniqueId = (String)this.figureTimestampTable.get(fig);
            this.log.info(ME, "timeout: newRemoved: " + fig + " " + uniqueId);
            if (uniqueId != null ) {
               if (this.log.TRACE) this.log.trace(ME, "orphan: removing the uniqueId '" + uniqueId + "'");
               remove(uniqueId, fig);
               tmpRemoved.put(uniqueId, fig);
            }
         }
         this.newRemoved[this.publishIndex].clear();
      }

      // publish the message here ...
      try {
         MessageContent content = new MessageContent(this.log, tmpAdded, tmpChanged, tmpRemoved);
         PublishKey pk = new PublishKey(this.global, "GraphicChat", "text/xml", "1.0");
         PublishQos pq = new PublishQos(this.global);
         MsgUnit msgUnit = new MsgUnit(pk, content.toBytes(), pq);
         this.access.publish(msgUnit);
      }
      catch (XmlBlasterException ex) {
         this.log.error(ME, "exception occured when publishing: " + ex.getMessage());
      }
      catch (IOException ex) {
         this.log.error(ME, "IOException occured when publishing: " + ex.getMessage());
      }
      this.timeout.addTimeoutListener(this, this.publishDelay, this);
   }


   public String update(String cbSessionId, UpdateKey updateKey, byte[] content, UpdateQos updateQos) {
      this.log.info(ME, "update for '" + cbSessionId + "', length of msg is '" + content.length + "'");
      try {
         MessageContent msgContent = MessageContent.fromBytes(content);

         Hashtable tmpAdded = msgContent.getAdded();
         Hashtable tmpChanged = msgContent.getChanged();
         Hashtable tmpRemoved = msgContent.getRemoved();

         this.log.info(ME, "update added " + tmpAdded.size() + " changed " + tmpChanged.size() + " removed " + tmpRemoved.size() + " figures");

         synchronized (this) {
            lock();
            this.doRecord = false;
           
            Enumeration enum = tmpAdded.keys();
            while (enum.hasMoreElements()) {
               String uniqueId = (String)enum.nextElement();
               Figure fig = (Figure)tmpAdded.get(uniqueId);
               add(uniqueId, fig);
               super.add(fig);
            }
           
            enum = tmpChanged.keys();
            while (enum.hasMoreElements()) {
               String uniqueId = (String)enum.nextElement();
               Figure fig = (Figure)tmpChanged.get(uniqueId);
               Figure oldFigure = (Figure)this.timestampFigureTable.get(uniqueId);
               add(uniqueId, fig);
               super.replace(oldFigure, fig);
               if (oldFigure != null) {
                  FigureChangeEvent ev = new FigureChangeEvent(oldFigure);
                  figureRequestUpdate(ev);
               }
               FigureChangeEvent ev1 = new FigureChangeEvent(fig);
               figureRequestUpdate(ev1);
            }
           
            enum = tmpRemoved.keys();
            while (enum.hasMoreElements()) {
               String uniqueId = (String)enum.nextElement();
               Figure fig = (Figure)this.timestampFigureTable.get(uniqueId);
               remove(uniqueId, fig);
               if (fig != null) {
                  super.orphan(fig);
                  FigureChangeEvent ev = new FigureChangeEvent(fig);
                  figureRequestUpdate(ev);
               }
            }
           
            this.doRecord = true;
            unlock();
         }
      }
      catch (IOException ex) {
         this.log.error(ME, "update: an IOException occured when reconstructing the content: " + ex.getMessage());
      }

      return "OK";
   }


}




