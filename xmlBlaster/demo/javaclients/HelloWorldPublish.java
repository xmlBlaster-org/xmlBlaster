// xmlBlaster/demo/javaclients/HelloWorldPublish.java
package javaclients;

import org.jutils.log.LogChannel;
import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.MsgUnit;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.enum.PriorityEnum;
import org.xmlBlaster.util.qos.TopicProperty;
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
import org.xmlBlaster.client.protocol.XmlBlasterConnection;


/**
 * This client connects to xmlBlaster and publishes a configurable amount of messages. 
 * <p>
 * This is a nice client to experiment and play with xmlBlaster as there are many
 * command line options to specify the type and amount of messages published.
 * </p>
 *
 * Invoke:
 * <pre>
 * java javaclients.HelloWorldPublish -interactive true -numPublish 10 -oid Hello -persistent true -erase true
 *
 * java javaclients.HelloWorldPublish -interactive false -sleep 1000 -numPublish 10 -oid Hello -persistent true -erase true
 *
 * java javaclients.HelloWorldPublish -session.name joe/5 -passwd secret -persistent true -dump[HelloWorldPublish] true
 * </pre>
 * <p>
 * If interactive is false, the sleep gives the number of millis to sleep before publishing the next message.
 * </p>
 * <p>
 * If erase=false the message is not erase at the end, if disconnect=false we don't logout at the end.
 * </p>
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
         long sleep = glob.getProperty().get("sleep", 1000L);
         int numPublish = glob.getProperty().get("numPublish", 1);
         String oid = glob.getProperty().get("oid", "Hello");
         String content = glob.getProperty().get("content", "Hi");
         PriorityEnum priority = PriorityEnum.toPriorityEnum(glob.getProperty().get("priority", PriorityEnum.NORM_PRIORITY.getInt()));
         boolean persistent = glob.getProperty().get("persistent", true);
         long lifeTime = glob.getProperty().get("lifeTime", -1L);
         boolean readonly = glob.getProperty().get("readonly", false);
         long destroyDelay = glob.getProperty().get("destroyDelay", 60000L);
         boolean createDomEntry = glob.getProperty().get("createDomEntry", true);
         boolean erase = glob.getProperty().get("erase", true);
         boolean disconnect = glob.getProperty().get("disconnect", true);

         log.info(ME, "Used settings are:");
         log.info(ME, "   -interactive    " + interactive);
         log.info(ME, "   -sleep          " + sleep);
         log.info(ME, "   -numPublish     " + numPublish);
         log.info(ME, "   -oid            " + oid);
         log.info(ME, "   -content        " + content);
         log.info(ME, "   -priority       " + priority.toString());
         log.info(ME, "   -persistent     " + persistent);
         log.info(ME, "   -lifeTime       " + lifeTime);
         log.info(ME, "   -readonly       " + readonly);
         log.info(ME, "   -destroyDelay   " + destroyDelay);
         log.info(ME, "   -createDomEntry " + createDomEntry);
         log.info(ME, "   -erase          " + erase);
         log.info(ME, "   -disconnect     " + disconnect);
         log.info(ME, "For more info please read:");
         log.info(ME, "   http://www.xmlBlaster.org/xmlBlaster/doc/requirements/interface.publish.html");

         XmlBlasterConnection con = new XmlBlasterConnection(glob);

         // ConnectQos checks -session.name and -passwd from command line
         log.info(ME, "============= CreatingConnectQos");
         ConnectQos qos = new ConnectQos(glob);
         log.info(ME, "ConnectQos is " + qos.toXml());
         ConnectReturnQos crq = con.connect(qos, null);  // Login to xmlBlaster, register for updates
         log.info(ME, "Connect success as " + crq.toXml());

         for(int i=0; i<numPublish; i++) {

            if (interactive) {
               log.info(ME, "Hit a key to publish'" + oid + "' #" + i + "/" + numPublish);
               try { System.in.read(); } catch(java.io.IOException e) {}
            }
            else {
               try { Thread.currentThread().sleep(sleep); } catch( InterruptedException e) {}
               log.info(ME, "Publish '" + oid + "' #" + i + "/" + numPublish);
            }

            PublishKey pk = new PublishKey(glob, oid, "text/xml", "1.0");
            pk.setClientTags("<org.xmlBlaster><demo/></org.xmlBlaster>");
            PublishQos pq = new PublishQos(glob);
            pq.setPriority(priority);
            pq.setPersistent(persistent);
            pq.setLifeTime(lifeTime);
            if (i == 0) {
               TopicProperty topicProperty = new TopicProperty(glob);
               topicProperty.setDestroyDelay(destroyDelay);
               topicProperty.setCreateDomEntry(createDomEntry);
               topicProperty.setReadonly(readonly);
               pq.setTopicProperty(topicProperty);
               log.info(ME, "Added TopicProperty on first publish: " + topicProperty.toXml());
            }
            MsgUnit msgUnit = new MsgUnit(glob, pk, content, pq);
            PublishReturnQos prq = con.publish(msgUnit);
            if (log.DUMP) log.dump("", "Published message: " + msgUnit.toXml());
            if (log.DUMP) log.dump("", "Returned: " + prq.toXml());

            log.info(ME, "Got status='" + prq.getState() + "' rcvTimestamp=" + prq.getRcvTimestamp().toString() +
                         " for published message '" + prq.getKeyOid() + "'");
         }

         if (erase) {
            if (interactive) {
               log.info(ME, "Hit a key to erase");
               try { System.in.read(); } catch(java.io.IOException e) {}
            }

            EraseKey ek = new EraseKey(glob, "HelloWorldPublish");
            EraseQos eq = new EraseQos(glob);
            EraseReturnQos[] eraseArr = con.erase(ek.toXml(), eq.toXml());
            log.info(ME, "Erase success");
         }

         log.info(ME, "Hit a key to exit");
         try { System.in.read(); } catch(java.io.IOException e) {}

         DisconnectQos dq = new DisconnectQos(glob);
         con.disconnect(dq);
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
         XmlBlasterConnection.usage();
         System.err.println("\nExample:");
         System.err.println("  java javaclients.HelloWorldPublish -interactive false -sleep 1000 -numPublish 10 -oid Hello -persistent true -erase true\n");
         System.exit(1);
      }

      new HelloWorldPublish(glob);
   }
}
