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
import org.xmlBlaster.util.def.Constants;
import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.Timestamp;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.I_Timeout;
import org.xmlBlaster.util.Timeout;
import org.xmlBlaster.client.I_XmlBlasterAccess;
import org.xmlBlaster.client.qos.*;
import org.xmlBlaster.client.key.*;
import org.xmlBlaster.util.qos.HistoryQos;
import org.xmlBlaster.util.MsgUnit;

import org.xmlBlaster.client.I_Callback;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.io.IOException;
import CH.ifa.draw.framework.Drawing;


/**
 * @author <a href="mailto:laghi@swissinfo.org">Michele Laghi</a>
 */
public  class XmlBlasterDrawing extends StandardDrawing implements I_Timeout, I_Callback {

   private static final String ME = "XmlBlasterDrawing";
   private Global global;
   private LogChannel log;
   /** inhibit events to avoid loops when events are generated in update method */
   private boolean doPublishEvent; 
   /** key = timestamp, value = object reference (Figure) */
   private HashMap timestampFigureTable;
   /** key = object reference (Figure), value = timestamp */
   private HashMap figureTimestampTable;
   private HashSet[] newChanged;
   private int currentIndex = 0;
   private int publishIndex = 1;
   private Timeout timeout;
   private long publishDelay = 5000L;
   private I_XmlBlasterAccess access;
   private SubscribeReturnQos subRetQos;
   private String drawingName = "GraphicChat";
   private SubscribeReturnQos subscribeReturnQos;
   private boolean isBurstPublish; // if true publish are collected and sent on bulk

   public XmlBlasterDrawing() {
      super();
      init(Global.instance());
      if (this.log.CALL) this.log.call(ME, "default contructor");
   }

   public XmlBlasterDrawing(Global global) {
      super();
      init(global);
      if (this.log.CALL) this.log.call(ME, "global contructor");
   }

   public void init(Global global) {
      this.global = global.getClone(null);
      this.log = this.global.getLog("graphical");
      if (this.log.CALL) this.log.call(ME, "init");
      this.doPublishEvent = true;
      this.timestampFigureTable = new HashMap();
      this.figureTimestampTable = new HashMap();
      this.newChanged = new HashSet[2];
      for (int i=0; i < 2; i++) {
         this.newChanged[i] = new HashSet();
      }
      this.publishDelay = this.global.getProperty().get("publishDelay", 200L);
      if (this.publishDelay > 0L) this.isBurstPublish = true;
      else isBurstPublish = false;
      if (this.isBurstPublish) {
         this.timeout = new Timeout("PublishTimer");
         this.timeout.addTimeoutListener(this, this.publishDelay, this);
      }  
      if (this.getTitle() != null) this.drawingName = this.getTitle();
      initConnection();
   }

   public void initConnection() {
      try {
         if (this.log.CALL) this.log.call(ME, "initConnection");
         this.access = this.global.getXmlBlasterAccess();
         ConnectQos qos = new ConnectQos(this.global);
         this.access.connect(qos, this);  // Login to xmlBlaster, register for updates

         SubscribeKey sk = new SubscribeKey(this.global, "/xmlBlaster/key[drawingName='" + this.drawingName + "']", Constants.XPATH);
         SubscribeQos sq = new SubscribeQos(this.global);
         sq.setWantLocal(false);
         sq.setWantInitialUpdate(true);
         HistoryQos historyQos = new HistoryQos(this.global);
         historyQos.setNumEntries(1);
         sq.setHistoryQos(historyQos);
         
         // this.publishMetaInfo(this.drawingName);
         this.subscribeReturnQos = this.access.subscribe(sk, sq);
      }
      catch (XmlBlasterException ex) {
         this.log.error(ME, "initConnection. Exception : " + ex.getMessage());
         ex.printStackTrace();
      }
   }

   synchronized public void shutdown() {
      if (this.log.CALL) this.log.call(ME, "shutdown");
      try {
         if (this.subscribeReturnQos != null) {
            this.access.unSubscribe(new UnSubscribeKey(this.global, this.subscribeReturnQos.getSubscriptionId()), new UnSubscribeQos(this.global));
         }
         this.access.disconnect(new DisconnectQos(this.global));
         this.log.info(ME, "successfully shutdown drawing (unsubscribed and disconnected");
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
      if (this.log.CALL) this.log.call(ME, "figureChanged event='" + e.toString() + "'");
      if (e.getFigure() instanceof Drawing) {
         if (this.log.TRACE) this.log.trace(ME, "figureChanged for a Drawing instance " + e.getFigure());
         return;
      }
      super.figureChanged(e);
      if (this.doPublishEvent) {
         StorableFigureHolder figureHolder = getFigureHolder(e.getFigure());
         if (this.isBurstPublish) this.newChanged[this.currentIndex].add(figureHolder); 
         else publish(figureHolder);
      }
      if (this.log.CALL) this.log.call(ME, "figureChanged " + e.getFigure());
   }

   protected void addToTable(StorableFigureHolder figureHolder) {
      String figureId = figureHolder.getFigureId();
      Figure figure = figureHolder.getFigure();
      synchronized (this.timestampFigureTable) {
         this.figureTimestampTable.put(figure, figureHolder);
         this.timestampFigureTable.put(figureId, figureHolder);
      }
   }   

   protected void removeFromTable(StorableFigureHolder figureHolder) {
      if (this.log.CALL) this.log.call(ME, "remove");
      String figureId = figureHolder.getFigureId();
      Figure figure = figureHolder.getFigure();
      synchronized (this.timestampFigureTable) {
         if (figure != null) {
            this.figureTimestampTable.remove(figure);
         }
         if (figureId != null) {
            this.timestampFigureTable.remove(figureId);
         }
      }
   }

   private StorableFigureHolder getFigureHolder(Figure figure) {
      // MEMO: getAttribute does not work when using 'group or lines'
      StorableFigureHolder figureHolder = (StorableFigureHolder)this.figureTimestampTable.get(figure);
      if (figureHolder == null) {
         String timestamp = "" + (new Timestamp()).getTimestampLong();
         String figureId = this.drawingName + "-" + timestamp;
         figureHolder = new StorableFigureHolder(this.log, figure, figureId, null);
         addToTable(figureHolder);
      }
      return figureHolder;
   }

   synchronized public Figure add(Figure figure) {
      if (this.log.CALL) this.log.call(ME, "add");
      if (figure instanceof Drawing) return figure;
      if (this.doPublishEvent) {
         StorableFigureHolder figureHolder = getFigureHolder(figure);
         if (this.isBurstPublish) this.newChanged[this.currentIndex].add(figureHolder);
         else publish(figureHolder);
         if (this.log.TRACE) this.log.trace(ME, "add: adding '" + figureHolder.getFigureId() + "'");
      }
      return super.add(figure);
   }

   synchronized public void bringToFront(Figure figure) {
      if (this.log.CALL) this.log.call(ME, "bringToFront");
      if (this.doPublishEvent) {
         ((StorableFigureHolder)this.figureTimestampTable.get(figure)).setToFront("true");
      } 
      super.bringToFront(figure);
   }

   synchronized public Figure orphan(Figure figure) {
      if (this.log.CALL) this.log.call(ME, "orphan");
      if (figure instanceof Drawing) return figure;
      if (this.doPublishEvent) {
         try {
            erase(figure);
         }
         catch (XmlBlasterException ex) {
            String figureId = ((StorableFigureHolder)this.figureTimestampTable.get(figure)).getFigureId();
            this.log.error(ME, "orphan '" + figureId + "' exception : " + ex.getMessage());
            ex.printStackTrace();
         }
      }
      return super.orphan(figure); // clean from my drawing area
   }

   public synchronized void sendToBack(Figure figure) {
      if (this.log.CALL) this.log.call(ME, "sendToBack");
      if (this.doPublishEvent) {
         ((StorableFigureHolder)this.figureTimestampTable.get(figure)).setToFront("false");
      } 
      super.sendToBack(figure);
   }

   public synchronized void sendToLayer(Figure figure, int layerNr) {
      if (this.log.CALL) this.log.call(ME, "sendToLayer");
      super.sendToLayer(figure, layerNr);
   }

   public synchronized void setTitle(java.lang.String name) {
      if (this.log.CALL) this.log.call(ME, "");
      super.setTitle(name);
   }


   /**
    * Invoked by the timer
    */
   public synchronized void timeout(Object userData) {
      try {
         swapIndex();
         if (this.newChanged[this.publishIndex].size() == 0) return;
         Iterator iter = this.newChanged[this.publishIndex].iterator();
         while (iter.hasNext()) {
            StorableFigureHolder figureHolder = (StorableFigureHolder)iter.next();
            if (figureHolder.getFigure() instanceof Drawing) continue;
            publish(figureHolder);
         }
         this.newChanged[this.publishIndex].clear();
      }
      catch (Throwable ex) {
         this.log.warn(ME ,"timeout: exception occured in timeout: " + ex.getMessage());
         ex.printStackTrace();
      }
      finally {
         this.timeout.addTimeoutListener(this, this.publishDelay, this);
      }
   }

   // publish -----------------------------------------------------------

   private void publish(StorableFigureHolder figureHolder) {
      if (this.log.CALL) this.log.call(ME, "publish");
      // publish the message here ...
      if (figureHolder == null || figureHolder.getFigure() instanceof Drawing) return;
      try {
         String figureId = figureHolder.getFigureId();
         if (this.log.TRACE) this.log.trace(ME, "publish '" + figureId + "'");
         PublishKey pk = new PublishKey(this.global, figureId, "application/draw", "1.0");
         pk.setClientTags("<drawingName>"+this.drawingName+"</drawingName>");
         PublishQos pq = new PublishQos(this.global);

         MsgUnit msgUnit = new MsgUnit(pk, figureHolder.toBytes(), pq);
         this.access.publish(msgUnit);
      }
      catch (IOException ex) {
         this.log.error(ME, "exception occured when publishing: " + ex.toString());
      }
      catch (XmlBlasterException ex) {
         this.log.error(ME, "exception occured when publishing: " + ex.getMessage());
      }
   }

   private void publishMetaInfo(String data) {
      if (this.log.CALL) this.log.call(ME, "publishMetaInfo");
      // publish the message here ...
      try {
         PublishKey pk = new PublishKey(this.global, null, "text/plain", "1.0");
         pk.setClientTags("<drawingMetadata>"+this.drawingName+"</drawingMetadata>");
         PublishQos pq = new PublishQos(this.global);

         MsgUnit msgUnit = new MsgUnit(pk, data.getBytes(), pq);
         this.access.publish(msgUnit);
      }
      catch (XmlBlasterException ex) {
              this.log.error(ME, "exception occured when publishing: " + ex.getMessage());
      }
   }

   // erase ---------------------------------------------------------------

   private void erase(Figure figure) throws XmlBlasterException {
      if (this.log.CALL) this.log.call(ME, "erase");
      if (figure == null) return;
      StorableFigureHolder figureHolder = (StorableFigureHolder)this.figureTimestampTable.get(figure);
      if (figureHolder == null) return;
      String figureId = figureHolder.getFigureId();
      if (this.log.TRACE) this.log.trace(ME, "erase '" + figureId + "'");
      EraseKey ek = new EraseKey(this.global, figureId);
      EraseQos eq = new EraseQos(this.global);
      EraseReturnQos[] eraseArr = this.access.erase(ek, eq);
      removeFromTable(figureHolder);
   }

   // update ---------------------------------------------------------------

   private void currentFigureUpdate(StorableFigureHolder figureHolder) {
      if (this.log.CALL) this.log.call(ME, "currentFigureUpdate");
      boolean sendToBack = false;
      boolean sendToFront = false;
      String toFront = figureHolder.getToFront(); 
      if (toFront != null) {
         boolean val = "true".equalsIgnoreCase(toFront);
         this.log.info(ME, "update: the attribute 'TO_FRONT' is set to '" + val + "'"); 
         if (val == true) sendToFront = true;
         else sendToBack = true;                         
         // setFigureAttribute(fig, TO_FRONT, null);
      }
      String figureId = figureHolder.getFigureId();
      if (this.log.TRACE) this.log.trace(ME, "currentFigureUpdate figureId='" + figureId + "'");
      if (figureId == null) return;
      // String figureId = msgContent.getFigureId();
      this.log.info(ME, "update figure: '" + figureId  + "' changed or added");
      StorableFigureHolder oldFigureHolder = (StorableFigureHolder)this.timestampFigureTable.get(figureId);

      Figure fig = figureHolder.getFigure();
      if (oldFigureHolder == null) {
         addToTable(figureHolder);
         super.add(fig);
      }
      else {
         Figure oldFigure = oldFigureHolder.getFigure();
         super.replace(oldFigure, fig);
         removeFromTable(oldFigureHolder);
         addToTable(figureHolder);
         FigureChangeEvent ev = new FigureChangeEvent(oldFigure);
         figureRequestUpdate(ev);
         if (sendToFront) {
            if (this.log.TRACE) this.log.trace(ME, "update: send to front");
            bringToFront(fig);
         } 
         else if (sendToBack) {
            if (this.log.TRACE) this.log.trace(ME, "update: send to back");
            sendToBack(fig);
         } 
      }
      FigureChangeEvent ev1 = new FigureChangeEvent(fig);
      figureRequestUpdate(ev1);
   }

   public synchronized String update(String cbSessionId, UpdateKey updateKey, byte[] content, UpdateQos updateQos) {
      this.log.info(ME, "update for '" + cbSessionId + "', '" + updateKey.getOid() + "' length of msg is '" + content.length + "'");
      try {
         lock();
         this.doPublishEvent = false;
              
         if (updateQos.isErased()) {
            log.info(ME, "Message '" + updateKey.getOid() + "' is erased");
            String figureId = updateKey.getOid();
            StorableFigureHolder figHolder = (StorableFigureHolder)this.timestampFigureTable.get(figureId);
            if (figHolder != null) {
               removeFromTable(figHolder);
               Figure fig = figHolder.getFigure();
               Figure figToRelease = super.orphan(fig);
               FigureChangeEvent ev = new FigureChangeEvent(fig);
               figureRequestUpdate(ev);
               figToRelease.release();
            }
            return "OK";
         }

         StorableFigureHolder figureHolder = StorableFigureHolder.fromBytes(content);
         currentFigureUpdate(figureHolder);
      }
      catch (IOException ex) {
         this.log.error(ME, "update: an IOException occured when reconstructing the content: " + ex.getMessage());
      }
      catch (Throwable ex) {
         this.log.error(ME, "update: a Throwable occured when reconstructing the content (acknowledge anyway): " + ex.getMessage());
         ex.printStackTrace();
      }
      finally {
         this.doPublishEvent = true;
         unlock();
      }
      return "OK";
   }

   public void release() {
      if (this.log.CALL) this.log.call(ME, "release");
      shutdown();
      super.release();
   }



   /*
      public void figureInvalidated(FigureChangeEvent e) {
         super.figureInvalidated(e);
         if (this.log.CALL) this.log.call(ME, "figureInvalidated " + e.getFigure());
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

      public void addAll(FigureEnumeration fe) {
         if (this.log.CALL) this.log.call(ME, "addAll");
         super.addAll(fe);
      }

      public void init(java.awt.Rectangle viewRectangle) {
         if (this.log.CALL) this.log.call(ME, "init");
         super.init(viewRectangle);
      }

      public void orphanAll(FigureEnumeration fe) {
         if (this.log.CALL) this.log.call(ME, "orphanAll");
         super.orphanAll(fe);
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
}

