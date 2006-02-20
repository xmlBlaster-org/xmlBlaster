// xmlBlaster/demo/javaclients/HelloWorldPublish.java
package javaclients;

import java.util.Map;
import java.util.Iterator;
import java.util.Random;
import org.jutils.log.LogChannel;
import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.MsgUnit;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.def.PriorityEnum;
import org.xmlBlaster.util.SessionName;
import org.xmlBlaster.util.qos.TopicProperty;
import org.xmlBlaster.util.qos.address.Destination;
import org.xmlBlaster.util.qos.storage.HistoryQueueProperty;
import org.xmlBlaster.client.qos.ConnectQos;
import org.xmlBlaster.client.qos.ConnectReturnQos;
import org.xmlBlaster.client.qos.DisconnectQos;
import org.xmlBlaster.client.key.PublishKey;
import org.xmlBlaster.client.key.EraseKey;
import org.xmlBlaster.client.qos.PublishQos;
import org.xmlBlaster.client.qos.PublishReturnQos;
import org.xmlBlaster.client.qos.EraseQos;
import org.xmlBlaster.client.qos.EraseReturnQos;
import org.xmlBlaster.client.I_XmlBlasterAccess;
import org.xmlBlaster.client.I_ConnectionStateListener;
import org.xmlBlaster.util.dispatch.ConnectionStateEnum;

/**
 * This client connects to xmlBlaster and publishes a configurable amount of messages. 
 * <p>
 * This is a nice client to experiment and play with xmlBlaster as there are many
 * command line options to specify the type and amount of messages published.
 * </p>
 * <p>
 * Try using 'java javaclients.HelloWorldSubscribe' in another window to subscribe to
 * our messages.
 * Further you can type 'd' in the window running xmlBlaster to get a server dump.
 * </p>
 *
 * Invoke (after starting the xmlBlaster server):
 * <pre>
 *Publish manually 10 messages:
 * java javaclients.HelloWorldPublish -interactive true -numPublish 10 -oid Hello -persistent true -erase true
 *
 *Publish automatically 10 messages and sleep 1 sec in between:
 * java javaclients.HelloWorldPublish -interactive false -sleep 1000 -numPublish 10 -oid Hello -persistent true -erase true
 *
 *Publish automatically 10 different topics with different DOM entries:
 * java javaclients.HelloWorldPublish -interactive false -numPublish 10 -oid Hello-%counter -clientTags "<org.xmlBlaster><demo-%counter/></org.xmlBlaster>"
 *
 *Login as joe/5 and send one persistent message:
 * java javaclients.HelloWorldPublish -session.name joe/5 -passwd secret -persistent true -dump[HelloWorldPublish] true
 *
 *Send a PtP message:
 * java javaclients.HelloWorldPublish -destination jack/17 -forceQueuing true -persistent true -subscribable true
 *
 *Add some client properties which will be send in the qos to the receivers:
 * java javaclients.HelloWorldPublish -clientProperty[transactionId] 0x23345 -clientProperty[myName] jack
 *creates a publish Qos containing:
 *   &lt;clientProperty name='transactionId'>0x23345&lt;/clientProperty>
 *   &lt;clientProperty name='myName'>jack&lt;/clientProperty>
 * </pre>
 * <p>
 * If interactive is false, the sleep gives the number of millis to sleep before publishing the next message.
 * </p>
 * <p>
 * If erase=false the message is not erase at the end, if disconnect=false we don't logout at the end.
 * </p>
 * <p>
 * You can add '%counter' to the clientTags or the content string, each occurrence will be replaced
 * by the current message number.
 * </p>
 * @see javaclients.HelloWorldSubscribe
 * @see <a href="http://www.xmlBlaster.org/xmlBlaster/doc/requirements/interface.html" target="others">xmlBlaster interface</a>
 */
public class HelloWorldPublish
{
   private final String ME = "HelloWorldPublish";
   private final Global glob;
   private final LogChannel log;

   public HelloWorldPublish(Global glob) {
      this.glob = glob;
      this.log = glob.getLog("HelloWorldPublish");
      try {
         boolean interactive = glob.getProperty().get("interactive", true);
         boolean oneway = glob.getProperty().get("oneway", false);
         long sleep = glob.getProperty().get("sleep", 1000L);
         int numPublish = glob.getProperty().get("numPublish", 1);
         String oid = glob.getProperty().get("oid", "Hello");  // "HelloTopic_#%counter"
         String domain = glob.getProperty().get("domain", (String)null);
         String clientTags = glob.getProperty().get("clientTags", "<org.xmlBlaster><demo-%counter/></org.xmlBlaster>");
         String contentStr = glob.getProperty().get("content", "Hi-%counter");
         String contentFile = glob.getProperty().get("contentFile", (String)null);
         PriorityEnum priority = PriorityEnum.toPriorityEnum(glob.getProperty().get("priority", PriorityEnum.NORM_PRIORITY.getInt()));
         boolean persistent = glob.getProperty().get("persistent", true);
         long lifeTime = glob.getProperty().get("lifeTime", -1L);
         boolean forceUpdate = glob.getProperty().get("forceUpdate", true);
         boolean forceDestroy = glob.getProperty().get("forceDestroy", false);
         boolean readonly = glob.getProperty().get("readonly", false);
         long destroyDelay = glob.getProperty().get("destroyDelay", 60000L);
         boolean createDomEntry = glob.getProperty().get("createDomEntry", true);
         long historyMaxMsg = glob.getProperty().get("queue/history/maxEntries", -1L);
         boolean forceQueuing = glob.getProperty().get("forceQueuing", true);
         boolean subscribable = glob.getProperty().get("subscribable", true);
         String destination = glob.getProperty().get("destination", (String)null);
         boolean erase = glob.getProperty().get("erase", true);
         boolean disconnect = glob.getProperty().get("disconnect", true);
         final boolean eraseTailback = glob.getProperty().get("eraseTailback", false);
         int contentSize = glob.getProperty().get("contentSize", -1); // 2000000);
         boolean eraseForceDestroy = glob.getProperty().get("erase.forceDestroy", false);
         boolean connectPersistent = glob.getProperty().get("connect/qos/persistent", false);
         
         Map clientPropertyMap = glob.getProperty().get("clientProperty", (Map)null);

         if (historyMaxMsg < 1 && !glob.getProperty().propertyExists("destroyDelay"))
            destroyDelay = 24L*60L*60L*1000L; // Increase destroyDelay to one day if no history queue is used

         log.info(ME, "Used settings are:");
         log.info(ME, "   -interactive    " + interactive);
         log.info(ME, "   -sleep          " + org.jutils.time.TimeHelper.millisToNice(sleep));
         log.info(ME, "   -oneway         " + oneway);
         log.info(ME, "   -erase          " + erase);
         log.info(ME, "   -disconnect     " + disconnect);
         log.info(ME, "   -eraseTailback  " + eraseTailback);
         log.info(ME, " Pub/Sub settings");
         log.info(ME, "   -numPublish     " + numPublish);
         log.info(ME, "   -oid            " + oid);
         log.info(ME, "   -clientTags     " + clientTags);
         log.info(ME, "   -domain         " + ((domain==null)?"":domain));
         if (contentSize >= 0) {
            log.info(ME, "   -content        [generated]");
            log.info(ME, "   -contentSize    " + contentSize);
         }
         else if (contentFile != null && contentFile.length() > 0) {
            log.info(ME, "   -contentFile    " + contentFile);
         }
         else {
            log.info(ME, "   -content        " + contentStr);
            log.info(ME, "   -contentSize    " + contentStr.length());
         }
         log.info(ME, "   -priority       " + priority.toString());
         log.info(ME, "   -persistent     " + persistent);
         log.info(ME, "   -lifeTime       " + org.jutils.time.TimeHelper.millisToNice(lifeTime));
         log.info(ME, "   -forceUpdate    " + forceUpdate);
         log.info(ME, "   -forceDestroy   " + forceDestroy);
         if (clientPropertyMap != null) {
            Iterator it = clientPropertyMap.keySet().iterator();
            while (it.hasNext()) {
               String key = (String)it.next();
               log.info(ME, "   -clientProperty["+key+"]   " + clientPropertyMap.get(key).toString());
            }
         }
         else {
            log.info(ME, "   -clientProperty[]   ");
         }
         log.info(ME, " Topic settings");
         log.info(ME, "   -readonly       " + readonly);
         log.info(ME, "   -destroyDelay   " + org.jutils.time.TimeHelper.millisToNice(destroyDelay));
         log.info(ME, "   -createDomEntry " + createDomEntry);
         log.info(ME, "   -queue/history/maxEntries " + historyMaxMsg);
         log.info(ME, " PtP settings");
         log.info(ME, "   -subscribable   " + subscribable);
         log.info(ME, "   -forceQueuing   " + forceQueuing);
         log.info(ME, "   -destination    " + destination);
         log.info(ME, " Erase settings");
         log.info(ME, "   -erase.forceDestroy " + eraseForceDestroy);
         log.info(ME, "   -erase.domain   " + ((domain==null)?"":domain));
         log.info(ME, " ConnectQos settings");
         log.info(ME, "   -connect/qos/persistent " + connectPersistent);
         log.info(ME, "For more info please read:");
         log.info(ME, "   http://www.xmlBlaster.org/xmlBlaster/doc/requirements/interface.publish.html");

         I_XmlBlasterAccess con = glob.getXmlBlasterAccess();

         // Handle lost server explicitly
         con.registerConnectionListener(new I_ConnectionStateListener() {
               
               public void reachedAlive(ConnectionStateEnum oldState,
                                        I_XmlBlasterAccess connection) {
                  /*
                  ConnectReturnQos conRetQos = connection.getConnectReturnQos();
                  log.info(ME, "I_ConnectionStateListener: We were lucky, connected to " +
                     connection.getGlobal().getId() + " as " + conRetQos.getSessionName());
                     */
                  if (eraseTailback) {
                     log.info(ME, "Destroying " + connection.getQueue().getNumOfEntries() +
                                  " client side tailback messages");
                     connection.getQueue().clear();
                  }
               }
               public void reachedPolling(ConnectionStateEnum oldState,
                                          I_XmlBlasterAccess connection) {
                  log.warn(ME, "I_ConnectionStateListener: No connection to xmlBlaster server, we are polling ...");
               }
               public void reachedDead(ConnectionStateEnum oldState,
                                       I_XmlBlasterAccess connection) {
                  log.warn(ME, "I_ConnectionStateListener: Connection from " +
                          connection.getGlobal().getId() + " to xmlBlaster is DEAD, doing exit.");
                  System.exit(1);
               }
            });
   
         // ConnectQos checks -session.name and -passwd from command line
         log.info(ME, "============= CreatingConnectQos");
         ConnectQos qos = new ConnectQos(glob);
         if (connectPersistent) {
            qos.setPersistent(connectPersistent);
         }
         log.info(ME, "ConnectQos is " + qos.toXml());
         ConnectReturnQos crq = con.connect(qos, null);  // Login to xmlBlaster, register for updates
         log.info(ME, "Connect success as " + crq.toXml());

         org.jutils.time.StopWatch stopWatch = new org.jutils.time.StopWatch();
         for(int i=0; i<numPublish; i++) {

            String currOid = org.jutils.text.StringHelper.replaceAll(oid, "%counter", ""+(i+1));

            if (interactive) {
               Global.waitOnKeyboardHit("Hit a key to publish '" + currOid + "' #" + (i+1) + "/" + numPublish);
            }
            else {
               if (sleep > 0) {
                  try { Thread.sleep(sleep); } catch( InterruptedException e) {}
               }
               log.info(ME, "Publish '" + currOid + "' #" + (i+1) + "/" + numPublish);
            }

            PublishKey pk = new PublishKey(glob, currOid, "text/xml", "1.0");
            if (domain != null) pk.setDomain(domain);
            pk.setClientTags(org.jutils.text.StringHelper.replaceAll(clientTags, "%counter", ""+(i+1)));
            PublishQos pq = new PublishQos(glob);
            pq.setPriority(priority);
            pq.setPersistent(persistent);
            pq.setLifeTime(lifeTime);
            pq.setForceUpdate(forceUpdate);
            pq.setForceDestroy(forceDestroy);
            pq.setSubscribable(subscribable);
            if (clientPropertyMap != null) {
               Iterator it = clientPropertyMap.keySet().iterator();
               while (it.hasNext()) {
                  String key = (String)it.next();
                  pq.addClientProperty(key, clientPropertyMap.get(key).toString());
               }
               //Example for a typed property:
               //pq.getData().addClientProperty("ALONG", (new Long(12)));
            }
            
            if (i == 0) {
               TopicProperty topicProperty = new TopicProperty(glob);
               topicProperty.setDestroyDelay(destroyDelay);
               topicProperty.setCreateDomEntry(createDomEntry);
               topicProperty.setReadonly(readonly);
               if (historyMaxMsg >= 0L) {
                  HistoryQueueProperty prop = new HistoryQueueProperty(this.glob, null);
                  prop.setMaxEntries(historyMaxMsg);
                  topicProperty.setHistoryQueueProperty(prop);
               }
               pq.setTopicProperty(topicProperty);
               log.info(ME, "Added TopicProperty on first publish: " + topicProperty.toXml());
            }
            
            if (destination != null) {
               log.trace("", "Using destination: '" + destination + "'");
               Destination dest = new Destination(glob, new SessionName(glob, destination));
               dest.forceQueuing(forceQueuing);
               pq.addDestination(dest);
            }

            byte[] content;
            if (contentSize >= 0) {
               content = new byte[contentSize];
               Random random = new Random();
               for (int j=0; j<content.length; j++) {
                  content[j] = (byte)(random.nextInt(96)+32);
                  //content[j] = (byte)('X');
                  //content[j] = (byte)(j % 255);
               }
            }
            else if (contentFile != null && contentFile.length() > 0) {
               content = org.jutils.io.FileUtil.readFile(contentFile);
            }
            else {
               content = org.jutils.text.StringHelper.replaceAll(contentStr, "%counter", ""+(i+1)).getBytes();
            }

            if (log.DUMP) log.dump("", "Going to parse publish message: " + pk.toXml() + " : " + content + " : " + pq.toXml());
            MsgUnit msgUnit = new MsgUnit(pk, content, pq);
            if (log.DUMP) log.dump("", "Going to publish message: " + msgUnit.toXml());

            if (oneway) {
               MsgUnit msgUnitArr[] = { msgUnit };
               con.publishOneway(msgUnitArr);
               log.info(ME, "#" + (i+1) + "/" + numPublish +
                         ": Published oneway message '" + msgUnit.getKeyOid() + "'");
            }
            else {
               PublishReturnQos prq = con.publish(msgUnit);
               if (log.DUMP) log.dump("", "Returned: " + prq.toXml());

               log.info(ME, "#" + (i+1) + "/" + numPublish +
                         ": Got status='" + prq.getState() + "' rcvTimestamp=" + prq.getRcvTimestamp() +
                         " for published message '" + prq.getKeyOid() + "'");
            }
         }
         log.info(ME, "Elapsed since starting to publish: " + stopWatch.nice(numPublish));

         if (erase) {
            if (interactive) {
               Global.waitOnKeyboardHit("Hit a key to erase");
            }

            EraseKey ek = new EraseKey(glob, oid);
            if (domain != null) ek.setDomain(domain);
            EraseQos eq = new EraseQos(glob);
            eq.setForceDestroy(eraseForceDestroy);
            if (log.DUMP) log.dump("", "Going to erase the topic: " + ek.toXml() + eq.toXml());
            EraseReturnQos[] eraseArr = con.erase(ek, eq);
            log.info(ME, "Erase success");
         }

         Global.waitOnKeyboardHit("Hit a key to exit");

         if (disconnect) {
            DisconnectQos dq = new DisconnectQos(glob);
            con.disconnect(dq);
         }
      }
      catch (XmlBlasterException e) {
         log.error(ME, e.getMessage());
      }
      catch (Exception e) {
         e.printStackTrace();
         log.error(ME, e.toString());
      }
   }

   /**
    * Try
    * <pre>
    *   java javaclients.HelloWorldPublish -help
    * </pre>
    * for usage help
    */
   public static void main(String args[]) {
      Global glob = new Global();
      
      if (glob.init(args) != 0) { // Get help with -help
         System.out.println(glob.usage());
         System.err.println("\nExample:");
         System.err.println("  java javaclients.HelloWorldPublish -interactive false -sleep 1000 -numPublish 10 -oid Hello -persistent true -erase true\n");
         System.err.println("  java javaclients.HelloWorldPublish  -clientProperty[myString] Hello -clientProperty[correlationId] 100\n");
         System.exit(1);
      }

      new HelloWorldPublish(glob);
   }
}
