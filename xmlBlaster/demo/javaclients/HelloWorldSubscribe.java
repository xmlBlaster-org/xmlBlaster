// xmlBlaster/demo/javaclients/HelloWorldSubscribe.java
package javaclients;

import org.jutils.log.LogChannel;
import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.def.Constants;
import org.xmlBlaster.util.def.ErrorCode;
import org.xmlBlaster.util.qos.HistoryQos;
import org.xmlBlaster.util.qos.ClientProperty;
import org.xmlBlaster.client.qos.ConnectQos;
import org.xmlBlaster.client.qos.ConnectReturnQos;
import org.xmlBlaster.client.qos.DisconnectQos;
import org.xmlBlaster.client.I_Callback;
import org.xmlBlaster.client.key.UpdateKey;
import org.xmlBlaster.client.key.SubscribeKey;
import org.xmlBlaster.client.key.UnSubscribeKey;
import org.xmlBlaster.client.qos.UpdateQos;
import org.xmlBlaster.client.qos.SubscribeQos;
import org.xmlBlaster.client.qos.SubscribeReturnQos;
import org.xmlBlaster.client.qos.UnSubscribeQos;
import org.xmlBlaster.client.qos.UnSubscribeReturnQos;
import org.xmlBlaster.util.qos.AccessFilterQos;
import org.xmlBlaster.client.I_XmlBlasterAccess;
import org.xmlBlaster.client.I_ConnectionStateListener;
import org.xmlBlaster.util.dispatch.ConnectionStateEnum;
import java.util.Map;
import java.util.Iterator;
import java.io.File;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.TimeZone;
import java.util.Date;


/**
 * This client connects to xmlBlaster and subscribes to messages. 
 * <p>
 * This is a nice client to experiment and play with xmlBlaster as there are many
 * command line options to specify the type and amount of messages published.
 * </p>
 * <p>
 * Try using 'java javaclients.HelloWorldPublish' in another window to publish some
 * messages.
 * Further you can type 'd' in the window running xmlBlaster to get a server dump.
 * </p>
 *
 * Invoke (after starting the xmlBlaster server):
 * <pre>
 * java javaclients.HelloWorldSubscribe -xpath //key -initialUpdate true -unSubscribe true
 *
 * java javaclients.HelloWorldSubscribe -interactive false -oid Hello -initialUpdate true -unSubscribe true
 *
 * java javaclients.HelloWorldSubscribe -session.name joeSubscriber/5 -passwd secret -initialUpdate true -dump[HelloWorldSubscribe] true
 *
 * java javaclients.HelloWorldSubscribe -xpath //key -filter.type GnuRegexFilter -filter.query "^__sys__jdbc.*"
 *
 * java javaclients.HelloWorldSubscribe -xpath //key -filter.type XPathFilter -filter.query "//tomato"
 *
 * java javaclients.HelloWorldSubscribe -xpath //key -filter.type ContentLenFilter -filter.query "10"
 * </pre>
 * <p>
 * If unSubscribe=false the message is not unsubscribed at the end, if disconnect=false we don't logout at the end.
 * </p>
 * @see java javaclients.HelloWorldPublish
 * @see java javaclients.HelloWorldGet
 * @see <a href="http://www.xmlBlaster.org/xmlBlaster/doc/requirements/interface.html" target="others">xmlBlaster interface</a>
 */
public class HelloWorldSubscribe implements I_Callback
{
   private final String ME = "HelloWorldSubscribe";
   private final Global glob;
   private final LogChannel log;
   private I_XmlBlasterAccess con;
   private SubscribeReturnQos srq;
   private String subscribeServerId;
   private int updateCounter;
   private boolean connectPersistent;
   private boolean firstConnect=true;
   private boolean interactive;
   private boolean interactiveUpdate;
   private long updateSleep;
   private String updateExceptionErrorCode;
   private String updateExceptionMessage;
   private String updateExceptionRuntime;
   private boolean shutdownCbServer;
   private String oid;
   private String domain;
   private String xpath;
   private boolean multiSubscribe;
   private boolean persistentSubscribe;
   private boolean notifyOnErase;
   private boolean local;
   private boolean initialUpdate;
   private boolean updateOneway;
   private boolean wantContent;
   private boolean dumpToFile;
   // only IF dumpToFile==true:
   private String fileExtension;
   private String filePrefix;
   private String fileDateFormat;
   private DateFormat formatter;
   private String fileLock;
   private String fileHeader;
   private int historyNumUpdates;
   private boolean historyNewestFirst;
   private String filterType;
   private String filterVersion;
   private String filterQuery;
   private boolean unSubscribe;
   private int maxContentLength;
   private boolean connectRefreshSession;
   private boolean runAsDaemon;
   private boolean dumpToConsole;

   private void readEnv() {
      this.connectPersistent = glob.getProperty().get("connect/qos/persistent", false);
      this.interactive = glob.getProperty().get("interactive", true);
      this.interactiveUpdate = glob.getProperty().get("interactiveUpdate", false);
      this.updateSleep = glob.getProperty().get("updateSleep", 0L);
      this.updateExceptionErrorCode = glob.getProperty().get("updateException.errorCode", (String)null);
      this.updateExceptionMessage = glob.getProperty().get("updateException.message", (String)null);
      this.updateExceptionRuntime = glob.getProperty().get("updateException.runtime", (String)null);
      this.shutdownCbServer = glob.getProperty().get("shutdownCbServer", false);
      this.oid = glob.getProperty().get("oid", "");
      this.domain = glob.getProperty().get("domain", "");
      this.xpath = glob.getProperty().get("xpath", "");
      this.multiSubscribe = glob.getProperty().get("multiSubscribe", true);
      this.persistentSubscribe = glob.getProperty().get("persistentSubscribe", false);
      this.notifyOnErase = glob.getProperty().get("notifyOnErase", true);
      this.local = glob.getProperty().get("local", true);
      this.initialUpdate = glob.getProperty().get("initialUpdate", true);
      this.updateOneway = glob.getProperty().get("updateOneway", false);
      this.wantContent = glob.getProperty().get("wantContent", true);
      this.dumpToFile = glob.getProperty().get("dumpToFile", false);
      // only IF dumpToFile==true:
      this.fileExtension = glob.getProperty().get("fileExtension", ""); // for example ".jpg"
      this.filePrefix = glob.getProperty().get("filePrefix", "");       // Fixed file name instead of topic as file name
      this.fileDateFormat = glob.getProperty().get("fileDateFormat", "yyyy-MM-dd'T'HHmmss.S"); // How to format the date of the file name (ISO 8601)
      this.fileLock = glob.getProperty().get("fileLock", "");           // add extension for lock file during Fixed file name instead of topic as file name, ".lck"
      this.fileHeader = glob.getProperty().get("fileHeader", "");       // add a header text to the file, e.g. "<?xml version='1.0' encoding='UTF-8' ?>\n"
      this.historyNumUpdates = glob.getProperty().get("historyNumUpdates", 1);
      this.historyNewestFirst = glob.getProperty().get("historyNewestFirst", true);
      this.filterType = glob.getProperty().get("filter.type", "GnuRegexFilter");// XPathFilter | ContentLenFilter
      this.filterVersion = glob.getProperty().get("filter.version", "1.0");
      this.filterQuery = glob.getProperty().get("filter.query", "");
      this.unSubscribe = glob.getProperty().get("unSubscribe", true);
      this.maxContentLength = glob.getProperty().get("maxContentLength", 250);
      this.connectRefreshSession = glob.getProperty().get("connect/qos/sessionRefresh", false);
      this.runAsDaemon = glob.getProperty().get("runAsDaemon", false);
      this.dumpToConsole = glob.getProperty().get("dumpToConsole", true);
   }

   public HelloWorldSubscribe(Global glob_) {
      this.glob = glob_;
      this.log = glob.getLog("HelloWorldSubscribe");
      boolean disconnect = glob.getProperty().get("disconnect", true);
      try {
         readEnv();

         if (oid.length() < 1 && xpath.length() < 1) {
            log.warn(ME, "No -oid or -xpath given, we subscribe to oid='Hello'.");
            oid = "Hello";
         }

         if (this.updateSleep > 0L && interactiveUpdate == true) {
            log.warn(ME, "You can't set 'updateSleep' and  'interactiveUpdate' simultaneous, we reset interactiveUpdate to false");
            this.interactiveUpdate = false;
         }

         if (this.updateExceptionErrorCode != null && this.updateExceptionRuntime != null) {
            log.warn(ME, "You can't throw a runtime and an XmlBlasterException simultaneous, please check your settings " +
                          " -updateException.errorCode and -updateException.runtime");
            this.updateExceptionRuntime = null;
         }

         log.info(ME, "Used settings are:");
         log.info(ME, "   -connect/qos/persistent     " + connectPersistent);
         log.info(ME, "   -connect/qos/sessionRefresh " + connectRefreshSession);
         log.info(ME, "   -interactive       " + interactive);
         log.info(ME, "   -interactiveUpdate " + this.interactiveUpdate);
         log.info(ME, "   -updateSleep       " + this.updateSleep);
         log.info(ME, "   -updateException.errorCode " + this.updateExceptionErrorCode);
         log.info(ME, "   -updateException.message   " + this.updateExceptionMessage);
         log.info(ME, "   -updateException.runtime   " + this.updateExceptionRuntime);
         log.info(ME, "   -shutdownCbServer          " + shutdownCbServer);
         log.info(ME, "   -oid               " + oid);
         log.info(ME, "   -domain            " + domain);
         log.info(ME, "   -xpath             " + xpath);
         log.info(ME, "   -multiSubscribe    " + multiSubscribe);
         log.info(ME, "   -persistentSubscribe " + persistentSubscribe);
         log.info(ME, "   -notifyOnErase     " + notifyOnErase);
         log.info(ME, "   -local             " + local);
         log.info(ME, "   -initialUpdate     " + initialUpdate);
         log.info(ME, "   -updateOneway      " + updateOneway);
         log.info(ME, "   -historyNumUpdates " + historyNumUpdates);
         log.info(ME, "   -historyNewestFirst " + historyNewestFirst);
         log.info(ME, "   -wantContent       " + wantContent);
         log.info(ME, "   -dumpToFile        " + dumpToFile);
         log.info(ME, "   -fileExtension     " + fileExtension);
         log.info(ME, "   -unSubscribe       " + unSubscribe);
         log.info(ME, "   -disconnect        " + disconnect);
         log.info(ME, "   -filter.type       " + filterType);
         log.info(ME, "   -filter.version    " + filterVersion);
         log.info(ME, "   -filter.query      " + filterQuery);
         log.info(ME, "For more info please read:");
         log.info(ME, "   http://www.xmlBlaster.org/xmlBlaster/doc/requirements/interface.subscribe.html");

         con = glob.getXmlBlasterAccess();

         // Doe fail safe handling:
         con.registerConnectionListener(new I_ConnectionStateListener() {
            public void reachedAlive(ConnectionStateEnum oldState, I_XmlBlasterAccess connection) {
               if (connection.getConnectReturnQos().isReconnected())
                  log.info(ME, "I_ConnectionStateListener.reachedAlive(): Same server instance found");
               else
                  log.info(ME, "I_ConnectionStateListener.reachedAlive(): New server instance found, connected to " +
                        connection.getConnectReturnQos().getSessionName());

               if (connection.getQueue().getNumOfEntries() > 0) {
                  log.info(ME, "I_ConnectionStateListener.reachedAlive(): Queue contains " +
                           connection.getQueue().getNumOfEntries() + " messages: " +
                           connection.getQueue().toXml(""));
                  // connection.getQueue().clear(); -> Would destroy ConnectQos if new connected
               }

               String id = connection.getConnectReturnQos().getSecretSessionId() + connection.getConnectReturnQos().getServerInstanceId();

               if (!firstConnect && (subscribeServerId == null ||
                   !subscribeServerId.equals(id) && !persistentSubscribe)) {
                  subscribe(); // We lost the old subscription, initialize subscription again
               }
            }
            public void reachedPolling(ConnectionStateEnum oldState, I_XmlBlasterAccess connection) {
               log.warn(ME, "I_ConnectionStateListener.reachedPolling(): No connection to " + glob.getId() + ", we are polling ...");
            }
            public void reachedDead(ConnectionStateEnum oldState, I_XmlBlasterAccess connection) {
               log.error(ME, "I_ConnectionStateListener.reachedDead(): Connection to " + glob.getId() + " is dead, good bye");
               System.exit(1);
            }
         });

         // ConnectQos checks -session.name and -passwd from command line
         ConnectQos qos = new ConnectQos(glob);
         qos.setPersistent(connectPersistent);
         qos.setRefreshSession(connectRefreshSession);
         log.info(ME, "ConnectQos is " + qos.toXml());
         ConnectReturnQos crq = con.connect(qos, this);  // Login to xmlBlaster, register for updates
         // crq can be null if '-dispatch/connection/doSendConnect false' is set
         log.info(ME, "Connect success as " + ((crq==null)?" faked connect":crq.toXml()));

         subscribe(); // first time

         if (shutdownCbServer) {
            Global.waitOnKeyboardHit("Hit a key to shutdown callback server");
            con.getCbServer().shutdown();
            log.info(ME, "Callback server halted, no update should arrive ...");
            /*
            for (int ii=0; ii<4; ii++) {
               Global.waitOnKeyboardHit("Hit a key to publish " + ii + "/4 ...");
               org.xmlBlaster.util.MsgUnit msgUnit = new org.xmlBlaster.util.MsgUnit("<key oid='FromSubscriber'/>", (new String("BLA")).getBytes(), "<qos/>");
               con.publish(msgUnit);
               log.info(ME, "Published message");
            }
            */
         }
         else {
            log.info(ME, "Waiting on update ...");
         }

         if (interactiveUpdate) {
            try { Thread.sleep(1000000000); } catch( InterruptedException i) {}
         }

         if (unSubscribe && srq!=null) {
            if (interactive) {
               Global.waitOnKeyboardHit("Hit a key to unSubscribe");
            }

            UnSubscribeKey uk = new UnSubscribeKey(glob, srq.getSubscriptionId());
            if (domain.length() > 0)  // cluster routing information
               uk.setDomain(domain);
            UnSubscribeQos uq = new UnSubscribeQos(glob);
            log.info(ME, "UnSubscribeKey=\n" + uk.toXml());
            log.info(ME, "UnSubscribeQos=\n" + uq.toXml());
            UnSubscribeReturnQos[] urqArr = con.unSubscribe(uk, uq);
            log.info(ME, "UnSubscribe on " + urqArr.length + " subscriptions done");
         }

         if (runAsDaemon) {
            while (true) {
               try {
                  Thread.sleep(1000000000L);
               }
               catch (Exception e) {}
            }
         }
         else {
            Global.waitOnKeyboardHit("Hit a key to exit");
         }
      }
      catch (XmlBlasterException e) {
         log.error(ME, e.getMessage());
      }
      catch (Exception e) {
         e.printStackTrace();
         log.error(ME, e.toString());
      }
      finally {
         if (con != null && disconnect) {
            DisconnectQos dq = new DisconnectQos(glob);
            con.disconnect(dq);
            log.info(ME, "Disconnected");
         }
      }
   }

   /**
    * Does the xmlBlaster subscribe. 
    */
   private void subscribe() {
      try {
         SubscribeKey sk = null;
         String qStr = null;
         if (oid.length() > 0) {
            sk = new SubscribeKey(glob, oid);
            qStr = oid;
         }
         else if (xpath.length() > 0) {
            sk = new SubscribeKey(glob, xpath, Constants.XPATH);
            qStr = xpath;
         }
         if (domain.length() > 0) {  // cluster routing information
            if (sk == null) sk = new SubscribeKey(glob, "", Constants.DOMAIN); // usually never
            sk.setDomain(domain);
            qStr = domain;
         }
         SubscribeQos sq = new SubscribeQos(glob);
         sq.setWantInitialUpdate(initialUpdate);
         sq.setWantUpdateOneway(updateOneway);
         sq.setMultiSubscribe(multiSubscribe);
         sq.setPersistent(persistentSubscribe);
         sq.setWantNotify(notifyOnErase);
         sq.setWantLocal(local);
         sq.setWantContent(wantContent);
         
         HistoryQos historyQos = new HistoryQos(glob);
         historyQos.setNumEntries(historyNumUpdates);
         historyQos.setNewestFirst(historyNewestFirst);
         sq.setHistoryQos(historyQos);

         if (filterQuery.length() > 0) {
            AccessFilterQos filter = new AccessFilterQos(glob, filterType, filterVersion, filterQuery);
            sq.addAccessFilter(filter);
         }

         log.info(ME, "SubscribeKey=\n" + sk.toXml());
         log.info(ME, "SubscribeQos=\n" + sq.toXml());

         if (firstConnect && interactive) {
            Global.waitOnKeyboardHit("Hit a key to subscribe '" + qStr + "'");
         }
         firstConnect = false;

         this.srq = con.subscribe(sk, sq);

         subscribeServerId = con.getConnectReturnQos().getSecretSessionId() + con.getConnectReturnQos().getServerInstanceId();

         log.info(ME, "Subscribed on topic '" + ((oid.length() > 0) ? oid : xpath) +
                        "', got subscription id='" + this.srq.getSubscriptionId() + "'\n" + this.srq.toXml());
         if (log.DUMP) log.dump("", "Subscribed: " + sk.toXml() + sq.toXml() + srq.toXml());
      }
      catch (XmlBlasterException e) {
         log.error(ME, e.getMessage());
      }
   }

   /**
    * Here the messages from xmlBlaster arrive. 
    */
   public String update(String cbSessionId, UpdateKey updateKey, byte[] content,
                        UpdateQos updateQos) throws XmlBlasterException {
      if (updateQos.isErased() && oid.length() > 0) { // Erased topic with EXACT subscription?
         if (dumpToConsole) {
            System.out.println("============= Topic '" + updateKey.getOid() + "' is ERASED =======================");
         }
         subscribe();             // topic is erased -> re-subsribe
         return Constants.RET_OK; // "<qos><state id='OK'/></qos>";
      }
      ++updateCounter;
      if (dumpToConsole) {
         System.out.println("");
         System.out.println("============= START #" + updateCounter + " '" + updateKey.getOid() + "' =======================");
         log.info(ME, "Receiving update #" + updateCounter + " of a message ...");
         System.out.println("<xmlBlaster>");
         System.out.println(updateKey.toXml());
         System.out.println("");
         System.out.println("<content size='"+content.length+"'>");
         if (maxContentLength < 0 || content.length < maxContentLength) {
            System.out.println(new String(content));
         }
         else {
            String str = new String(content, 0,maxContentLength-5);
            System.out.println(str + " ...");
         }
         System.out.println("</content>");
         System.out.println(updateQos.toXml());
         System.out.println("</xmlBlaster>");
      }

      if (dumpToFile) {
         String pre = (this.filePrefix.length() > 0) ? this.filePrefix : (updateKey.getOid() + "-");
         String time = formatDate(updateQos.getRcvTimestamp().getMillis()); // 2005-06-15T052536
         String fileName = pre + time;
         if (fileExtension != null && fileExtension.length() > 0) {
            fileName += fileExtension;
         }
         String lckFile = "";
         if (this.fileLock.length() > 0) {
            lckFile = fileName + this.fileLock;
         }
         try {
            if (lckFile.length() > 0) {
               org.jutils.io.FileUtil.writeFile(lckFile, "Writing " + fileName + " ...");
            }
            //byte[] tmp = "<?xml version='1.0' encoding='UTF-8' ?>\n".getBytes() + content;
            byte[] tmp = content;
            if (this.fileHeader.length() > 0) {
               byte[] fh = this.fileHeader.getBytes();
               tmp = new byte[fh.length+content.length];
               System.arraycopy(fh, 0, tmp, 0, fh.length);
               System.arraycopy(content, 0, tmp, fh.length, content.length);
            }

            org.jutils.io.FileUtil.writeFile(fileName, tmp);
            log.info(ME, "Dumped content #" + updateCounter + " of topic '" + updateKey.getOid() + "' to file '" + fileName + "'");
         }
         catch (org.jutils.JUtilsException e) {
            log.error(ME, "Can't dump content to file '" + fileName + "': " + e.toString());
         }
         finally {
            try {
               if (lckFile.length() > 0) {
                  File f = new File(lckFile);
                  if (f.exists())
                     f.delete();
               }
            }
            catch (Exception e) {
               log.error(ME, "Can't remove lock file '" + lckFile + "': " + e.toString());
            }
         }
      }

      // If clientProperty is base64 encoded we print the real value as well:
      Map map = updateQos.getClientProperties();
      Iterator it = map.values().iterator();
      while (it.hasNext()) {
         ClientProperty clientProperty = (ClientProperty)it.next();
         if (clientProperty.isBase64()) {
            if (dumpToConsole) {
               System.out.println("\nClientProperty decoded: " + clientProperty.getName() + "='" + clientProperty.getStringValue() + "'");
            }
         }
      }

      if (dumpToConsole) {
         System.out.println("============= END #" + updateCounter + " '" + updateKey.getOid() + "' =========================");
         System.out.println("");
      }

      if (this.updateSleep > 0L) {
         log.info(ME, "Sleeping for " + this.updateSleep + " millis ...");
         try { Thread.sleep(this.updateSleep); } catch( InterruptedException i) {}
         log.info(ME, "Waking up.");
      } else if (this.interactiveUpdate) {
         Global.waitOnKeyboardHit("Hit a key to return from update() (we are blocking the server callback) ...");
         log.info(ME, "Returning update() - control goes back to server");
      }

      if (this.updateExceptionErrorCode != null) {
         log.info(ME, "Throwing XmlBlasterException with errorCode='" + this.updateExceptionErrorCode + "' back to server ...");
         ErrorCode errorCode;
         try {
            errorCode = ErrorCode.toErrorCode(this.updateExceptionErrorCode);
         }
         catch (IllegalArgumentException e) {
            log.error(ME, "Please supply a valid exception errorCode (see ErrorCode.java) for instead of -updateException.errorCode " + this.updateExceptionErrorCode + "");
            return Constants.RET_OK; // "<qos><state id='OK'/></qos>";
         }
         throw new XmlBlasterException(updateKey.getGlobal(), errorCode, ME, this.updateExceptionMessage); 
      }

      if (this.updateExceptionRuntime != null) {
         log.info(ME, "Throwing RuntimeException '" + this.updateExceptionRuntime + "'");
         throw new RuntimeException(this.updateExceptionRuntime);
      }

      return Constants.RET_OK; // "<qos><state id='OK'/></qos>";
   }

   /**
    * Convert the long milli second time to a readable format as given with fileDateFormat. 
    * "yyyy-MM-dd'T'HHmmss" or with milli seconds "yyyy-MM-dd'T'HHmmss.S"
    * @return Defaults to "2005-06-19T152029.344" (ISO 8601)
    */
   private String formatDate(long timestamp) {
      if (this.fileDateFormat == null || this.fileDateFormat.length() == 0) {
         return "";
      }
      if (this.fileDateFormat.equals("long")) {
         return ""+timestamp;
      }
      if (this.formatter == null) { // cache
         synchronized (this) {
            if (this.formatter == null) {
               this.formatter = new SimpleDateFormat(this.fileDateFormat); //, Locale.US);
               this.formatter.setTimeZone(TimeZone.getDefault()); // from "user.timezone"
            }
         }
      }
      return this.formatter.format(new Date(timestamp));
   }

   /**
    * Try
    * <pre>
    *   java javaclients.HelloWorldSubscribe -help
    * </pre>
    * for usage help
    */
   public static void main(String args[]) {
      Global glob = new Global();
      
      if (glob.init(args) != 0) { // Get help with -help
         System.out.println(glob.usage());
         System.err.println("\nExample:");
         System.err.println("  java javaclients.HelloWorldSubscribe -oid Hello -initialUpdate true\n");
         System.exit(1);
      }

      new HelloWorldSubscribe(glob);
   }
}
