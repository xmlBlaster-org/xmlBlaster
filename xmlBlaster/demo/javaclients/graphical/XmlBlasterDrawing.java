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
import org.xmlBlaster.util.enum.Constants;
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

import java.util.ArrayList;
import java.util.Hashtable;
import java.util.HashSet;
import java.util.Iterator;
import java.io.IOException;
import CH.ifa.draw.framework.Drawing;
import CH.ifa.draw.framework.FigureAttributeConstant;
import CH.ifa.draw.framework.FigureEnumeration;


/**
 * @author <a href="mailto:laghi@swissinfo.org">Michele Laghi</a>
 */
public  class XmlBlasterDrawing extends StandardDrawing implements I_Timeout, I_Callback {

   private static final String ME = "XmlBlasterDrawing";
   private Global global;
   private LogChannel log;
   /* inhibit events to avoid loops when events are generated in update method */
   private boolean doPublishEvent; 
   /** key = timestamp, value = object reference (Figure) */
   private Hashtable timestampFigureTable; 
   /** key = object reference (Figure), value = timestamp */
   private Hashtable figureTimestampTable;
   private HashSet[] newChanged;
   private int currentIndex = 0;
   private int publishIndex = 1;
   private Timeout timeout;
   private long publishDelay = 5000L;
   private I_XmlBlasterAccess access;
   private SubscribeReturnQos subRetQos;
   private ConnectReturnQos connectReturnQos;
   private String drawingName = "GraphicChat";
   private SubscribeReturnQos subscribeReturnQos;
   
   private final static FigureAttributeConstant FIGURE_ID = new FigureAttributeConstant("FigureId");
   private final static FigureAttributeConstant TO_FRONT = new FigureAttributeConstant("FigureId");
   private boolean isBurstPublish; // if true publish are collected and sent on bulk

   public XmlBlasterDrawing() {
      super();
      init(Global.instance());
   }

   public XmlBlasterDrawing(Global global) {
      super();
      init(global);
   }


   public void init(Global global) {
      this.global = global.getClone(null);
      this.log = this.global.getLog("graphical");
                if (this.log.CALL) this.log.call(ME, "init");
      this.doPublishEvent = true;
      this.timestampFigureTable = new Hashtable();
      this.figureTimestampTable = new Hashtable();
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

                // this.drawingName = this.getTitle();
      initConnection();
   }


   public void initConnection() {
      try {
         if (this.log.CALL) this.log.call(ME, "initConnection");
         this.access = this.global.getXmlBlasterAccess();
         ConnectQos qos = new ConnectQos(this.global);
         this.connectReturnQos = this.access.connect(qos, this);  // Login to xmlBlaster, register for updates

         SubscribeKey sk = new SubscribeKey(this.global, "/xmlBlaster/key[drawingName='" + this.drawingName + "']", Constants.XPATH);
         SubscribeQos sq = new SubscribeQos(this.global);
         sq.setWantLocal(false);
         sq.setWantInitialUpdate(true);
         HistoryQos historyQos = new HistoryQos(this.global);
         historyQos.setNumEntries(1);
         sq.setHistoryQos(historyQos);
         
         this.publishMetaInfo(this.drawingName);
         this.subscribeReturnQos = this.access.subscribe(sk, sq);
      }
      catch (XmlBlasterException ex) {
         this.log.error(ME, "initConnection. Exception : " + ex.getMessage());
         ex.printStackTrace();
      }
   }

   synchronized public void shutdown() {
      try {
         if (this.subscribeReturnQos != null) {
            this.access.unSubscribe(new UnSubscribeKey(this.global, this.subscribeReturnQos.getSubscriptionId()), new UnSubscribeQos(this.global));
         }
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

   private void traceToFront(String method, Figure fig) {
      Boolean toFront = (Boolean)fig.getAttribute(TO_FRONT);
      if (toFront != null) {
         this.log.trace(ME, "figureChanged: TO_FRONT is '" + toFront.booleanValue() + "'");
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
         if (this.log.TRACE) traceToFront("figureChanged", e.getFigure()); 
         if (this.isBurstPublish) this.newChanged[this.currentIndex].add(e.getFigure()); 
         else publishAll(e.getFigure());
      }
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

   protected void addToTable(String figureId, Figure figure) {
      synchronized (this.figureTimestampTable) {
         this.figureTimestampTable.put(figure, figureId);
         this.timestampFigureTable.put(figureId, figure);
      }
   }

   protected void remove(String figureId, Figure figure) {
      if (this.log.CALL) this.log.call(ME, "remove");
      synchronized (this.figureTimestampTable) {
         if (figure != null) {
            this.figureTimestampTable.remove(figure);
         }
         if (figureId != null) {
            this.timestampFigureTable.remove(figureId);
         }
      }
   }

   private String getFigureId(Figure figure) {
      // MEMO: getAttribute does not work when using 'group'
      String figureId = (String)figure.getAttribute(FIGURE_ID);
      // String figureId = (String)this.figureTimestampTable.get(figure);
      if (figureId == null) {
         String timestamp = "" + (new Timestamp()).getTimestampLong();
         figureId = this.drawingName + "-" + timestamp;
         figure.setAttribute(FIGURE_ID, figureId);
         addToTable(figureId, figure);
      }
      return figureId;
   }

   synchronized public Figure add(Figure figure) {
      if (this.log.CALL) this.log.call(ME, "add");
      if (figure instanceof Drawing) return figure;
      if (this.doPublishEvent) {
         String figureId = getFigureId(figure);
         if (this.isBurstPublish) this.newChanged[this.currentIndex].add(figure);
         else publishAll(figure);
         if (this.log.TRACE) this.log.trace(ME, "add: adding '" + figureId + "'");
      }
      return super.add(figure);
   }

/*       
   public void addAll(FigureEnumeration fe) {
      if (this.log.CALL) this.log.call(ME, "addAll");
      super.addAll(fe);
   }
*/
   synchronized public void bringToFront(Figure figure) {
      if (this.log.CALL) this.log.call(ME, "bringToFront");
      if (this.doPublishEvent) figure.setAttribute(TO_FRONT, new Boolean(true));
      super.bringToFront(figure);
   }

/*
   public void init(java.awt.Rectangle viewRectangle) {
      if (this.log.CALL) this.log.call(ME, "init");
      super.init(viewRectangle);
   }
*/


   private void recursiveErase(Figure fig) throws XmlBlasterException {
      erase(fig);
      FigureEnumeration iter = fig.figures();
      while (iter.hasNextFigure()) {
         Figure child = iter.nextFigure();
         recursiveErase(child);
      }
   }

   private void erase(Figure figure) throws XmlBlasterException {
      if (this.log.CALL) this.log.call(ME, "erase");
      if (figure == null) return;
      String figureId = (String)figure.getAttribute(FIGURE_ID);
      if (figureId == null) return;
      if (this.log.TRACE) this.log.trace(ME, "erase '" + figureId + "'");
      EraseKey ek = new EraseKey(this.global, figureId);
      EraseQos eq = new EraseQos(this.global);
      EraseReturnQos[] eraseArr = this.access.erase(ek, eq);
      //    the removing is handled by the timeout
      //   this.newRemoved[this.currentIndex].add(figure);
   }

   synchronized public Figure orphan(Figure figure) {
      if (this.log.CALL) this.log.call(ME, "orphan");
      if (figure instanceof Drawing) return figure;
      if (this.doPublishEvent) {
         try {
            recursiveErase(figure);
         }
         catch (XmlBlasterException ex) {
            this.log.error(ME, "orphan '" + (String)figure.getAttribute(FIGURE_ID) + "' exception : " + ex.getMessage());
            ex.printStackTrace();
         }
      }
      return super.orphan(figure); // clean from my drawing area
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

   public synchronized void sendToBack(Figure figure) {
      if (this.log.CALL) this.log.call(ME, "sendToBack");
      if (this.doPublishEvent) figure.setAttribute(TO_FRONT, new Boolean(false));
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
            Figure fig = (Figure)iter.next();
            if (fig instanceof Drawing) continue;
            publishAll(fig);
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

   private void publish(Figure figure) {
      if (this.log.CALL) this.log.call(ME, "publish");
      // publish the message here ...
      if (figure == null || figure instanceof Drawing) return;
      try {
         if (this.log.TRACE) traceToFront("publish", figure);
         String figureId = getFigureId(figure);
         if (this.log.TRACE) this.log.trace(ME, "publish '" + figureId + "'");
         MessageContent content = new MessageContent(this.log, figureId, figure);
         PublishKey pk = new PublishKey(this.global, figureId, "application/draw", "1.0");
         pk.setClientTags("<drawingName>"+this.drawingName+"</drawingName>");
         PublishQos pq = new PublishQos(this.global);

         MsgUnit msgUnit = new MsgUnit(pk, content.toBytes(), pq);
         this.access.publish(msgUnit);
         if (figure.getAttribute(TO_FRONT) != null) figure.setAttribute(TO_FRONT, null);
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

   private void recursiveFigureUpdate(Figure fig) {
           currentFigureUpdate(fig);
           FigureEnumeration iter = fig.figures();
           while (iter.hasNextFigure()) {
                   Figure child = iter.nextFigure();
                   recursiveFigureUpdate(child);
           }
   }

   private ArrayList recursivePublish(Figure fig, ArrayList entries) {
           if (entries == null) entries = new ArrayList();
           // publish(fig);
           if (fig instanceof Drawing) return null;
           entries.add(fig);
           FigureEnumeration iter = fig.figures();
           while (iter.hasNextFigure()) {
                   Figure child = iter.nextFigure();
                   entries = recursivePublish(child, entries);
           }
           iter = fig.getDependendFigures();
           while (iter.hasNextFigure()) {
                   Figure child = iter.nextFigure();
                   entries = recursivePublish(child, entries);
           }
           return entries;
   }

   private void publishAll(Figure fig) {
           ArrayList entries = recursivePublish(fig, null);
           if (entries != null) {
                   for (int i=0; i < entries.size(); i++) {
                           publish((Figure)entries.get(i));        
                   }
           }
   }

   private void currentFigureUpdate(Figure fig) {
      if (this.log.CALL) this.log.call(ME, "currentFigureUpdate");
      boolean sendToBack = false;
      boolean sendToFront = false;
      Boolean toFront = (Boolean)fig.getAttribute(TO_FRONT);
      if (toFront != null) {
         boolean val = toFront.booleanValue();
         this.log.info(ME, "update: the attribute 'TO_FRONT' is set to '" + val + "'"); 
         if (val == true) sendToFront = true;
         else sendToBack = true;                         
      }
      String figureId = (String)fig.getAttribute(FIGURE_ID);
      if (this.log.TRACE) this.log.trace(ME, "currentFigureUpdate '" + figureId + "'");
      if (figureId == null) return;
      // String figureId = msgContent.getFigureId();
      this.log.info(ME, "update figure: '" + figureId  + "' changed or added");
      Figure oldFigure = (Figure)this.timestampFigureTable.get(figureId);
      addToTable(figureId, fig);
      if (oldFigure == null) {
         super.add(fig);
      }
      else {
         super.replace(oldFigure, fig);
         if (sendToFront) bringToFront(fig);
         else if (sendToBack) sendToBack(fig);
         fig.setAttribute(TO_FRONT, null);
         FigureChangeEvent ev = new FigureChangeEvent(oldFigure);
         figureRequestUpdate(ev);
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
            Figure fig = (Figure)this.timestampFigureTable.get(figureId);
            remove(figureId, fig);
            
            if (fig != null) {
               super.orphan(fig);
               FigureChangeEvent ev = new FigureChangeEvent(fig);
               figureRequestUpdate(ev);
            }
            return "OK";
         }

         MessageContent msgContent = MessageContent.fromBytes(content);
         Figure fig = msgContent.getFigure();
         recursiveFigureUpdate(fig);
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
   
}

