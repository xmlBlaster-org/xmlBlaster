// xmlBlaster/demo/javaclients/HelloWorldPublish.java
package javaclients;

import org.jutils.log.LogChannel;
import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.MsgUnit;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.enum.PriorityEnum;
import org.xmlBlaster.util.SessionName;
import org.xmlBlaster.util.qos.TopicProperty;
import org.xmlBlaster.util.qos.address.Destination;
import org.xmlBlaster.util.qos.storage.HistoryQueueProperty;
import org.xmlBlaster.client.qos.ConnectQos;
import org.xmlBlaster.client.qos.ConnectReturnQos;
import org.xmlBlaster.client.qos.DisconnectQos;
import org.xmlBlaster.client.key.UpdateKey;
import org.xmlBlaster.client.key.PublishKey;
import org.xmlBlaster.client.key.GetKey;
import org.xmlBlaster.client.key.SubscribeKey;
import org.xmlBlaster.client.key.UnSubscribeKey;
import org.xmlBlaster.client.key.EraseKey;
import org.xmlBlaster.client.qos.GetQos;
import org.xmlBlaster.client.qos.GetReturnQos;
import org.xmlBlaster.client.qos.PublishQos;
import org.xmlBlaster.client.qos.PublishReturnQos;
import org.xmlBlaster.client.qos.UpdateQos;
import org.xmlBlaster.client.qos.UpdateReturnQos;
import org.xmlBlaster.client.qos.SubscribeQos;
import org.xmlBlaster.client.qos.SubscribeReturnQos;
import org.xmlBlaster.client.qos.EraseQos;
import org.xmlBlaster.client.qos.EraseReturnQos;
import org.xmlBlaster.client.qos.UnSubscribeQos;
import org.xmlBlaster.client.I_XmlBlasterAccess;


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
 *Login as joe/5 and send one persistent message:
 * java javaclients.HelloWorldPublish -session.name joe/5 -passwd secret -persistent true -dump[HelloWorldPublish] true
 *
 *Send a PtP message:
 * java javaclients.HelloWorldPublish -destination jack/17 -forceQueuing true -persistent true -subscribeable true
 * </pre>
 * <p>
 * If interactive is false, the sleep gives the number of millis to sleep before publishing the next message.
 * </p>
 * <p>
 * If erase=false the message is not erase at the end, if disconnect=false we don't logout at the end.
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
         String oid = glob.getProperty().get("oid", "Hello");
         byte[] content = glob.getProperty().get("content", "Hi").getBytes();
         PriorityEnum priority = PriorityEnum.toPriorityEnum(glob.getProperty().get("priority", PriorityEnum.NORM_PRIORITY.getInt()));
         boolean persistent = glob.getProperty().get("persistent", true);
         long lifeTime = glob.getProperty().get("lifeTime", -1L);
         boolean forceDestroy = glob.getProperty().get("forceDestroy", false);
         boolean readonly = glob.getProperty().get("readonly", false);
         long destroyDelay = glob.getProperty().get("destroyDelay", 60000L);
         boolean createDomEntry = glob.getProperty().get("createDomEntry", true);
         long historyMaxMsg = glob.getProperty().get("queue/history/maxEntries", -1L);
         boolean forceQueuing = glob.getProperty().get("forceQueuing", true);
         boolean subscribeable = glob.getProperty().get("subscribeable", true);
         String destination = glob.getProperty().get("destination", (String)null);
         boolean erase = glob.getProperty().get("erase", true);
         boolean disconnect = glob.getProperty().get("disconnect", true);
         int contentSize = glob.getProperty().get("contentSize", -1); // 2000000);

         if (historyMaxMsg < 1 && !glob.getProperty().propertyExists("destroyDelay"))
            destroyDelay = 24L*60L*60L*1000L; // Increase destroyDelay to one day if no history queue is used

         log.info(ME, "Used settings are:");
         log.info(ME, "   -interactive    " + interactive);
         log.info(ME, "   -sleep          " + org.jutils.time.TimeHelper.millisToNice(sleep));
         log.info(ME, "   -oneway         " + oneway);
         log.info(ME, "   -erase          " + erase);
         log.info(ME, "   -disconnect     " + disconnect);
         log.info(ME, " Pub/Sub settings");
         log.info(ME, "   -numPublish     " + numPublish);
         log.info(ME, "   -oid            " + oid);
         if (contentSize >= 0) {
            log.info(ME, "   -content        [generated]");
            log.info(ME, "   -contentSize    " + contentSize);
         }
         else {
            log.info(ME, "   -content        " + new String(content));
            log.info(ME, "   -contentSize    " + content.length);
         }
         log.info(ME, "   -priority       " + priority.toString());
         log.info(ME, "   -persistent     " + persistent);
         log.info(ME, "   -lifeTime       " + org.jutils.time.TimeHelper.millisToNice(lifeTime));
         log.info(ME, "   -forceDestroy   " + forceDestroy);
         log.info(ME, " Topic settings");
         log.info(ME, "   -readonly       " + readonly);
         log.info(ME, "   -destroyDelay   " + org.jutils.time.TimeHelper.millisToNice(destroyDelay));
         log.info(ME, "   -createDomEntry " + createDomEntry);
         log.info(ME, "   -queue/history/maxEntries " + historyMaxMsg);
         log.info(ME, " PtP settings");
         log.info(ME, "   -subscribeable  " + subscribeable);
         log.info(ME, "   -forceQueuing   " + forceQueuing);
         log.info(ME, "   -destination    " + destination);
         log.info(ME, "For more info please read:");
         log.info(ME, "   http://www.xmlBlaster.org/xmlBlaster/doc/requirements/interface.publish.html");

         I_XmlBlasterAccess con = glob.getXmlBlasterAccess();

         // ConnectQos checks -session.name and -passwd from command line
         log.info(ME, "============= CreatingConnectQos");
         ConnectQos qos = new ConnectQos(glob);
         log.info(ME, "ConnectQos is " + qos.toXml());
         ConnectReturnQos crq = con.connect(qos, null);  // Login to xmlBlaster, register for updates
         log.info(ME, "Connect success as " + crq.toXml());

         for(int i=0; i<numPublish; i++) {

            if (interactive) {
               log.info(ME, "Hit a key to publish '" + oid + "' #" + (i+1) + "/" + numPublish);
               try { System.in.read(); } catch(java.io.IOException e) {}
            }
            else {
               if (sleep > 0) {
                  try { Thread.currentThread().sleep(sleep); } catch( InterruptedException e) {}
               }
               log.info(ME, "Publish '" + oid + "' #" + (i+1) + "/" + numPublish);
            }

            PublishKey pk = new PublishKey(glob, oid, "text/xml", "1.0");
            pk.setClientTags("<org.xmlBlaster><demo/></org.xmlBlaster>");
            PublishQos pq = new PublishQos(glob);
            pq.setPriority(priority);
            pq.setPersistent(persistent);
            pq.setLifeTime(lifeTime);
            pq.setForceDestroy(forceDestroy);
            pq.setSubscribeable(subscribeable);
            
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
               Destination dest = new Destination(glob, new SessionName(glob, destination));
               dest.forceQueuing(forceQueuing);
               pq.addDestination(dest);
            }

            if (contentSize >= 0) {
               content = new byte[contentSize];
               for (int j=0; j<content.length; j++)
                  content[j] = (byte)(j % 255);
            }

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

         if (erase) {
            if (interactive) {
               log.info(ME, "Hit a key to erase");
               try { System.in.read(); } catch(java.io.IOException e) {}
            }

            EraseKey ek = new EraseKey(glob, oid);
            EraseQos eq = new EraseQos(glob);
            EraseReturnQos[] eraseArr = con.erase(ek, eq);
            log.info(ME, "Erase success");
         }

         log.info(ME, "Hit a key to exit");
         try { System.in.read(); } catch(java.io.IOException e) {}

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
         System.exit(1);
      }

      new HelloWorldPublish(glob);
   }
}
