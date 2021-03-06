/*------------------------------------------------------------------------------
Name:      MomEventHandler.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/

package org.xmlBlaster.contrib;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.StringTokenizer;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import org.xmlBlaster.client.I_Callback;
import org.xmlBlaster.client.I_XmlBlasterAccess;
import org.xmlBlaster.client.key.PublishKey;
import org.xmlBlaster.client.key.UpdateKey;
import org.xmlBlaster.client.qos.ConnectQos;
import org.xmlBlaster.client.qos.PublishQos;
import org.xmlBlaster.client.qos.UpdateQos;
import org.xmlBlaster.contrib.dbwatcher.DbWatcherConstants;
import org.xmlBlaster.contrib.dbwriter.SqlInfoParser;
import org.xmlBlaster.contrib.dbwriter.info.SqlInfo;
import org.xmlBlaster.jms.XBSession;
import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.MsgUnit;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.def.Constants;
import org.xmlBlaster.util.def.ErrorCode;
import org.xmlBlaster.util.qos.ClientProperty;
import org.xmlBlaster.util.qos.address.CallbackAddress;

public class MomEventEngine implements I_Callback, I_ChangePublisher {

   private static Logger log = Logger.getLogger(MomEventEngine.class.getName());
   protected Global glob;
   protected I_XmlBlasterAccess con;
   protected String loginName;
   protected String password;
   protected List subscribeKeyList;
   protected List subscribeQosList;
   protected ConnectQos connectQos;
   protected I_Update eventHandler;
   protected boolean shutdownMom;
   private int compressSize;
   
   public MomEventEngine() {
      this.subscribeKeyList = new ArrayList();
      this.subscribeQosList = new ArrayList();
   }
   
   
   /**
    * @see org.xmlBlaster.contrib.I_ContribPlugin#getUsedPropertyKeys()
    */
   public Set getUsedPropertyKeys() {
      Set set = new HashSet();
      set.add("mom.loginName");
      set.add("mom.password");
      set.add("mom.subscriptions");
      // TODO add also the kind mom.subscribeKeys[*] and qos
      set.add("mom.subscribeKey");
      set.add("mom.subscribeQos");
      set.add("mom.connectQos");
      set.add("mom.maxSessions");
      set.add("dbWriter.shutdownMom");
      return set;
   }


   public void init(I_Info info) throws Exception {
      if (this.con != null) return;
      
      Global globOrig = (Global)info.getObject("org.xmlBlaster.engine.Global");
      if (globOrig == null) {
         
         if (info instanceof GlobalInfo)
            this.glob = ((GlobalInfo)info).global;
         else {
            Iterator iter = info.getKeys().iterator();
            ArrayList argsList = new ArrayList();
            while (iter.hasNext()) {
               String key = (String)iter.next();
               String value = info.get(key, null);
               if (value != null) {
                  argsList.add("-" + key);
                  argsList.add(value);
               }
            }
            this.glob = new Global((String[])argsList.toArray(new String[argsList.size()]));
         }
      }
      else {
         this.glob = globOrig.getClone(globOrig.getNativeConnectArgs());
         this.glob.addObjectEntry(Constants.OBJECT_ENTRY_ServerScope, globOrig.getObjectEntry(Constants.OBJECT_ENTRY_ServerScope)); // "ServerNodeScope"
      }

      this.compressSize = info.getInt(DbWatcherConstants.MOM_COMPRESS_SIZE, 0);
      this.shutdownMom = info.getBoolean("dbWriter.shutdownMom", false); // avoid to disconnect (otherwise it looses persistent subscriptions)
      this.loginName = info.get("mom.loginName", "dbWriter/1");
      this.password  = info.get("mom.password", "secret");

      /* comma separated list of names for the subscriptions */
      String subscriptionNames = info.get("mom.subscriptions", (String)null);
      if (subscriptionNames != null) {
         StringTokenizer tokenizer = new StringTokenizer(subscriptionNames.trim(), ",");
         while (tokenizer.hasMoreTokens()) {
            String name = tokenizer.nextToken();
            if (name != null) {
               name = name.trim();
               if (name.length() > 0) {
                  String tmp = "mom.subscribeKey[" + name + "]";
                  String key = info.get(tmp, null);
                  if (key == null)
                     throw new Exception(".init: the attribute '" + tmp + "' has not been found but '" + name +"' was listed in 'mom.subscriptions' solve the inconsistency");
                  tmp = "mom.subscribeQos[" + name + "]";
                  String qos = info.get(tmp, "<qos/>");
                  log.info(".init: adding subscription '" + name + "' to the list: key='" + key + "' and qos='" + qos + "'");
                  this.subscribeKeyList.add(key);
                  this.subscribeQosList.add(qos);
               }
            }
         }
      }
      // Either subscriptionNames are null or not we use the mom.subscriptionKey and mom.subscriptionQos
      String tmp = "mom.subscribeKey";
      String key = info.get(tmp, null);
      if (key != null) {
         tmp = "mom.subscribeQos";
         String qos = info.get(tmp, "<qos/>");
         log.info(".init: adding unnamed subscription to the list: key='" + key + "' and qos='" + qos + "'");
         this.subscribeKeyList.add(key);
         this.subscribeQosList.add(qos);
      }


      tmp  = info.get("mom.connectQos", (String)null);
      if (tmp != null) {
         this.connectQos = new ConnectQos(this.glob, this.glob.getConnectQosFactory().readObject(tmp));
      }
      else {
         this.connectQos = new ConnectQos(this.glob, this.loginName, this.password);
         int maxSessions = info.getInt("mom.maxSessions", 100);
         this.connectQos.setMaxSessions(maxSessions);
         this.connectQos.getAddress().setRetries(-1);
         this.connectQos.setSessionTimeout(0L);
         if (info.getBoolean("mom.updateBulkAck", false))
            this.connectQos.addClientProperty(Constants.UPDATE_BULK_ACK, "true");
         CallbackAddress cbAddr = new CallbackAddress(this.glob);
         if (!info.getBoolean("mom.initialDispatcherActive", false)) {
            cbAddr.setDispatcherActive(false);
         }
         cbAddr.setRetries(-1);
         String dispatcherPlugin = info.get("mom.dispatcherPlugin", null);
         if (dispatcherPlugin != null)
            cbAddr.setDispatchPlugin(dispatcherPlugin);
         this.connectQos.addCallbackAddress(cbAddr);
      }
      log.info("Connecting with qos '" + this.connectQos.toXml() + "'");
      this.con = this.glob.getXmlBlasterAccess();
      this.con.connect(this.connectQos, this);

      // TODO cleanup in an own method and avoid unsubscribe and disconnect on shutdown ...
      if (this.subscribeKeyList.size() < 1)
         log.info("init: no subscription has been registered.");
      else
         log.info("init: " + this.subscribeKeyList.size() + " subscriptions have been registered.");
      for (int i=0; i < this.subscribeKeyList.size(); i++) {
         log.fine("init: subscribing '" + i + "' with key '" + this.subscribeKeyList.get(i) + "' and qos '" + this.subscribeQosList.get(i) + "'");
         this.con.subscribe((String)this.subscribeKeyList.get(i), (String)this.subscribeQosList.get(i), this);
      }
      
      // Make myself available
      info.putObject("org.xmlBlaster.contrib.dbwriter.mom.MomEventEngine", this);
      info.putObject("org.xmlBlaster.contrib.dbwatcher.mom.I_EventEngine", this);
   }

   public String update(String cbSessionId, UpdateKey updateKey, byte[] content, UpdateQos updateQos) throws XmlBlasterException {
      String timestamp = null;
      try {
         if (log.isLoggable(Level.FINEST)) {
            
         }
         ByteArrayInputStream bais = new ByteArrayInputStream(content);
         InputStream is = decompress(bais, updateQos.getClientProperties());
         timestamp = "" + updateQos.getRcvTimestamp().getTimestamp();
         updateQos.getData().addClientProperty(ContribConstants.TIMESTAMP_ATTR, timestamp);
         
         if (this.eventHandler != null)
            this.eventHandler.update(updateKey.getOid(), is, updateQos.getClientProperties());
         else 
            throw new Exception("update: No event handler has been registered, you must register one");
         return Constants.RET_OK;
      }
      catch (Exception ex) {
         if (ex instanceof XmlBlasterException) {
            String msg = ex.getMessage();
            if (msg.indexOf("SAXParseException") > -1) {
               // then try it again without uncompressing
               InputStream is = new ByteArrayInputStream(content);
               try {
                  if (eventHandler != null) {
                     eventHandler.update(updateKey.getOid(), is, updateQos.getClientProperties());
                     return Constants.RET_OK;
                  }
               }
               catch (Exception e) {
                  log.warning("Exception occured also when running uncompressed");
               }
            }
         }
         ex.printStackTrace();
         throw new XmlBlasterException(this.glob, ErrorCode.USER_UPDATE_HOLDBACK, "MomEventEngine.update", "user exception", ex);
      }
      catch (Throwable ex) {
         ex.printStackTrace();
         throw new XmlBlasterException(this.glob, ErrorCode.USER_UPDATE_HOLDBACK, "MomEventEngine.update", "user throwable", ex);
      }
   }


   /**
    * @param changeKey The topic of the message as a string.
    * @param message the content of the message to publish.
    * @param attrMap an attribute map which can be null. A single attribute
    * is currently used: qos, containing the qos literal.
    * @return the PublishQos as a string.
    */
   public String publish(String oid, byte[] message, Map attrMap) throws Exception {
      message = compress(message, attrMap, this.compressSize, null);
      String qos = null;
      if (attrMap != null)
         qos = (String)attrMap.get("qos");
      if (qos == null) {
         if (attrMap != null) {
            PublishQos pubQos = new PublishQos(this.glob);
            ClientPropertiesInfo tmpInfo = new ClientPropertiesInfo(pubQos.getData().getClientProperties(), null);
            InfoHelper.fillInfoWithEntriesFromMap(tmpInfo, attrMap);
            qos = pubQos.toXml();
         }
         else {
            qos = "<qos/>";
         }
      }
      PublishKey pubKey = new PublishKey(this.glob, oid);
      MsgUnit msg = new MsgUnit(this.glob, pubKey.toXml(), message, qos);
      return this.con.publish(msg).toXml();
   }

   public boolean registerAlertListener(I_Update update, Map attrs) throws Exception {
      if (this.eventHandler != null)
         return false;
      this.eventHandler = update;
      return true;
   }

   public void shutdown() {
      log.fine("Closing xmlBlaster connection");
      if (this.con != null && this.shutdownMom) {
         this.con.disconnect(null);
         this.con = null;
         this.glob = null;
      }
   }
   
   
   /**
    * @see org.xmlBlaster.contrib.I_ChangePublisher#getJmsSession()
    */
   public XBSession getJmsSession() {
      return new XBSession(this.glob, XBSession.AUTO_ACKNOWLEDGE, false);
   }

   /**
    * Compresses the message if needed. 
    * @param buffer The buffer to compress
    * @param props The properties to update with the compressed flag (uncompressed size)
    * @param compressSizeLimit The limit for compression. If less than one no compression
    * is done. If the size of the buffer is less than this limit it is not compressed either.
    * @return the compressed buffer or the input buffer if no compression was needed.
    * @deprecated
    */
   public static byte[] compressOLD(byte[] buffer, Map props, int compressSizeLimit, String zipType) {
      if (compressSizeLimit <  1L)
         return buffer;
      if (buffer.length < compressSizeLimit)
         return buffer;
      int uncompressedLength = buffer.length;

      // check if not already compressed
      if (props != null && props.containsKey(DbWatcherConstants._COMPRESSION_TYPE)) {
         log.fine("The message is already compressed, will not compress it");
         return buffer;
      }
      ByteArrayOutputStream baos = new ByteArrayOutputStream();
      byte[] ret = null;
      try {
          GZIPOutputStream zippedStream = new GZIPOutputStream(baos);
          ObjectOutputStream objectOutputStream = new ObjectOutputStream(zippedStream);
          objectOutputStream.writeObject(buffer);
          objectOutputStream.flush();
          zippedStream.finish();
          ret = baos.toByteArray();
          objectOutputStream.close();
          if (ret.length >= uncompressedLength) {
             log.fine("The compressed size is bigger than the original. Will not compress since it does not make sense");
             return buffer;
          }
          if (props != null) {
             props.put(DbWatcherConstants._UNCOMPRESSED_SIZE, "" + uncompressedLength);
             props.put(DbWatcherConstants._COMPRESSION_TYPE, DbWatcherConstants.COMPRESSION_TYPE_GZIP);
          }
          return ret;
      }
      catch(IOException ex) {
         log.severe("An exception occured when compressing: '" + ex.getMessage() + "' will not compress");
         return buffer;
      }
   }

   /**
    * Compresses the message if needed. 
    * @param buffer The buffer to compress
    * @param props The properties to update with the compressed flag (uncompressed size)
    * @param compressSizeLimit The limit for compression. If less than one no compression
    * is done. If the size of the buffer is less than this limit it is not compressed either.
    * @return the compressed buffer or the input buffer if no compression was needed.
    */
   public static byte[] compress(byte[] buffer, Map props, int compressSizeLimit, String zipType) {
      if (compressSizeLimit <  1L)
         return buffer;
      if (buffer.length < compressSizeLimit)
         return buffer;
      int uncompressedLength = buffer.length;

      // check if not already compressed
      if (props != null && props.containsKey(DbWatcherConstants._COMPRESSION_TYPE)) {
         log.fine("The message is already compressed, will not compress it");
         return buffer;
      }
      ByteArrayOutputStream baos = new ByteArrayOutputStream();
      byte[] ret = null;
      try {
          GZIPOutputStream zippedStream = new GZIPOutputStream(baos);
          zippedStream.write(buffer);
          zippedStream.finish();
          ret = baos.toByteArray();
          if (ret.length >= uncompressedLength) {
             log.fine("The compressed size is bigger than the original. Will not compress since it does not make sense");
             return buffer;
          }
          if (props != null) {
             props.put(DbWatcherConstants._UNCOMPRESSED_SIZE, "" + uncompressedLength);
             props.put(DbWatcherConstants._COMPRESSION_TYPE, DbWatcherConstants.COMPRESSION_TYPE_GZIP);
          }
          return ret;
      }
      catch(IOException ex) {
         log.severe("An exception occured when compressing: '" + ex.getMessage() + "' will not compress");
         return buffer;
      }
   }

   private final static String dumpProps(Map clientProperties) {
      if (clientProperties == null)
         return "";
      Iterator iter = clientProperties.values().iterator();
      StringBuffer buf = new StringBuffer(512);
      while (iter.hasNext()) {
         ClientProperty prop = (ClientProperty)iter.next();
         buf.append(prop.toXml()).append("\n");
      }
      return buf.toString();
   }
   
   /**
    * 
    * @param buffer
    * @param clientProperties
    * @deprecated you should use the one with InputStream instead since less memory hungry (for big messages)
    * @return
    */
   public static byte[] decompressXX(byte[] buffer, Map clientProperties) {
      if (clientProperties == null)
         return buffer;
      Object obj = clientProperties.get(DbWatcherConstants._COMPRESSION_TYPE);
      if (obj == null) {
         log.fine("The client property '" + DbWatcherConstants._COMPRESSION_TYPE + "' was not found. Will not expand");
         return buffer;
      }
      if (obj instanceof String)
         obj = new ClientProperty(DbWatcherConstants._COMPRESSION_TYPE, null, null, (String)obj);
      ClientProperty prop = (ClientProperty)obj;
      String compressionType = prop.getStringValue().trim();
      if (DbWatcherConstants.COMPRESSION_TYPE_GZIP.equals(compressionType)) {
         obj = clientProperties.get(DbWatcherConstants._UNCOMPRESSED_SIZE);
         if (obj == null) {
            log.severe("Can not expand message since no uncompressed size defined (will return it unexpanded)");
            return buffer;
         }
         if (obj instanceof String)
            obj = new ClientProperty(DbWatcherConstants._UNCOMPRESSED_SIZE, null, null, (String)obj);
         prop = (ClientProperty)obj;
         
         ByteArrayInputStream bais = new ByteArrayInputStream(buffer);
         byte[] ret = null;
         try {
             GZIPInputStream zippedStream = new GZIPInputStream(bais);
             ObjectInputStream objectInputStream = new ObjectInputStream(zippedStream);
             ret = (byte[])objectInputStream.readObject();
             objectInputStream.close();
             // in case we cascade it still works fine
             clientProperties.remove(DbWatcherConstants._COMPRESSION_TYPE);
             clientProperties.remove(DbWatcherConstants._UNCOMPRESSED_SIZE);
             return ret;
         }
         catch(IOException ex) {
            log.severe("An IOException occured when uncompressing. Will not expand: " + ex.getMessage() + ": props='" + dumpProps(clientProperties) + "' and content '" + new String(buffer) + "'");
            if (log.isLoggable(Level.FINE))
               ex.printStackTrace();
            return buffer;
         }
         catch(ClassNotFoundException ex) {
            log.severe("A ClassCastException occured when uncompressing. Will not expand: " + ex.getMessage());
            if (log.isLoggable(Level.FINE))
               ex.printStackTrace();
            return buffer;
         }
         
      }
      else {
         log.warning("The compression type '" + compressionType + "' is unknown: will not decompress");
         return buffer;
      }
   }

   public static InputStream decompress(InputStream is, Map clientProperties) {
      if (clientProperties == null)
         return is;
      Object obj = clientProperties.get(DbWatcherConstants._COMPRESSION_TYPE);
      if (obj == null) {
         log.fine("The client property '" + DbWatcherConstants._COMPRESSION_TYPE + "' was not found. Will not expand");
         return is;
      }
      if (obj instanceof String)
         obj = new ClientProperty(DbWatcherConstants._COMPRESSION_TYPE, null, null, (String)obj);
      ClientProperty prop = (ClientProperty)obj;
      String compressionType = prop.getStringValue().trim();
      if (DbWatcherConstants.COMPRESSION_TYPE_GZIP.equals(compressionType)) {
         obj = clientProperties.get(DbWatcherConstants._UNCOMPRESSED_SIZE);
         if (obj == null) {
            log.severe("Can not expand message since no uncompressed size defined (will return it unexpanded)");
            return is;
         }
         if (obj instanceof String)
            obj = new ClientProperty(DbWatcherConstants._UNCOMPRESSED_SIZE, null, null, (String)obj);
         prop = (ClientProperty)obj;
         
         try {
            GZIPInputStream ret = new GZIPInputStream(is);
            clientProperties.remove(DbWatcherConstants._COMPRESSION_TYPE);
            clientProperties.remove(DbWatcherConstants._UNCOMPRESSED_SIZE);
            return ret;
         }
         catch (IOException ex) {
            log.severe("An Exception occured when trying to decompress the stream, probably not gzipped");
            ex.printStackTrace();
            return is;
         }
      }
      return is;
   }

   public static void main(String[] args) {
      try {
         if (args.length < 1) {
            System.err.println("usage: filename");
            System.exit(-1);
         }
         String filename = args[0];
         Map<String, Charset> map = Charset.availableCharsets();
         String[] keys = map.keySet().toArray(new String[map.size()]);
         for (int i=0; i< keys.length; i++) {
            if (keys[i].contains("UTF"))
               log.info(keys[i]);
         }
         for (int i=0; i < 3; i++) {
            FileInputStream fis = new FileInputStream(filename);
              GZIPInputStream ret = new GZIPInputStream(fis);
              PropertiesInfo info = new PropertiesInfo(System.getProperties());
              // info.put("useReaderCharset",  "true");
              SqlInfoParser parser = new SqlInfoParser();
              parser.init(info);
              // SqlInfo sqlInfo = parser.readObject(ret, "iso-8859-1");
              // SqlInfo sqlInfo = parser.readObject(ret, "AL32UTF8");
              SqlInfo sqlInfo = parser.readObject(ret, "UTF-8");
              // SqlInfo sqlInfo = parser.readObject(ret, "UTF-8");
         }
      }
      catch(Exception ex) {
         ex.printStackTrace();
      }
   }
}
