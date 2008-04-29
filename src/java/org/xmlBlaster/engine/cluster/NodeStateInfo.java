/*------------------------------------------------------------------------------
Name:      NodeStateInfo.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Mapping from domain informations to master id
Author:    xmlBlaster@marcelruff.info
------------------------------------------------------------------------------*/
package org.xmlBlaster.engine.cluster;

import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.def.Constants;

import java.util.Map;
import java.util.TreeMap;
import java.util.Iterator;
import java.util.logging.Logger;

import org.xml.sax.Attributes;

/**
 * Holds performance data of a node. 
 * <p />
 * This is a demo code only, we need to change it to handle
 * some performance index which is comparable between
 * different nodes.
 * <pre>
 * &lt;!-- Messages of type "__sys__cluster.node.master[heron]": -->
 * &lt;state>
 *   &lt;cpu id='0' idle='40'/>   &lt;!-- currently 60% load on first CPU -->
 *   &lt;cpu id='1' idle='44'/>
 *   &lt;ram free='12000'/>       &lt;!-- xmlBlaster server has 12 MB free memory -->
 *   &lt;performance bogomips='1205.86' idleIndex='20'/>
 * &lt;/master>
 * </pre>
 */
public class NodeStateInfo {
   private static Logger log = Logger.getLogger(NodeStateInfo.class.getName());

   /** Free RAM memory in kBytes, -1 if not known */
   private int freeRam = - 1;

   /** Holds Cpu info objects, the key is the 'id' */
   private Map/*<String, Cpu>*/ cpuMap = new TreeMap();

   /** Average idle of all CPUs of the node, -1 if not known */
   private int avgCpuIdle = -1;

   public NodeStateInfo(Global global) {
   }

   /** @return Free RAM memory in kBytes */
   public int getFreeRam() {
      return freeRam;
   }

   /** Free RAM memory in kBytes */
   public void setFreeRam(int freeRam) {
      this.freeRam = freeRam;
   }

   /** @return Average idle of all CPUs of the node in percent, e.g. 40 is 40% idle */
   public int getAvgCpuIdle() {
      return avgCpuIdle;
   }

   /** Add or change the current CPU idle value */
   public void setCpu(int id, int idle) {
      Cpu cpu = (Cpu)cpuMap.get(""+id);
      if (cpu == null)
         cpuMap.put(""+id, new Cpu(id, idle));
      else
         cpu.idle = idle;

      // update average value
      if (cpuMap.size() == 1) {
         avgCpuIdle = idle;
      }
      else {
         int sum=0;
         Iterator it = cpuMap.values().iterator();
         while (it.hasNext()) {
            cpu = (Cpu)it.next();
            sum += cpu.idle;
         }
         avgCpuIdle = sum/cpuMap.size();
      }
   }

   /**
    * Called for SAX master start tag
    * @return true if ok, false on error
    */
   public final boolean startElement(String uri, String localName, String name, StringBuffer character, Attributes attrs) {
      if (name.equalsIgnoreCase("state"))
         return true;

      if (name.equalsIgnoreCase("cpu")) {
         if (attrs != null) {
            int id = 0;
            String tmp = attrs.getValue("id");
            if (tmp != null) {
               try { id = Integer.parseInt(tmp.trim()); } catch(NumberFormatException e) { log.severe("Invalid <cpu id='" + tmp + "'"); };
            }
            int idle = 50;
            tmp = attrs.getValue("idle");
            if (tmp != null) {
               try { idle = Integer.parseInt(tmp.trim()); } catch(NumberFormatException e) { log.severe("Invalid <cpu idle='" + tmp + "'"); };
            }
            setCpu(id, idle);
         }
         character.setLength(0);
         return true;
      }

      if (name.equalsIgnoreCase("ram")) {
         if (attrs != null) {
            String tmp = attrs.getValue("free");
            if (tmp != null) {
               try { setFreeRam(Integer.parseInt(tmp.trim())); } catch(NumberFormatException e) { log.severe("Invalid <ram free='" + tmp + "'"); };
            }
         }
         character.setLength(0);
         return true;
      }

      return false;
   }

   /**
    * Handle SAX parsed end element
    */
   public final void endElement(String uri, String localName, String name, StringBuffer character) {
      character.setLength(0);
      return;
   }

   /**
    * Dump state of this object into a XML ASCII string.
    */
   public final String toXml() {
      return toXml((String)null);
   }

   /**
    * Dump state of this object into a XML ASCII string.
    * @param extraOffset indenting of tags for nice output
    */
   public final String toXml(String extraOffset) {
      StringBuffer sb = new StringBuffer();
      if (extraOffset == null) extraOffset = "";
      String offset = Constants.OFFSET + extraOffset;

      sb.append(offset).append("<state>");
      Iterator it = cpuMap.values().iterator();
      while (it.hasNext()) {
         Cpu cpu = (Cpu)it.next();
         sb.append(offset).append(" <cpu id='").append(cpu.id).append("' idle='").append(cpu.idle).append("'/>");
      }
      if (getFreeRam() >= 0)
         sb.append(offset).append(" <ram free='").append(getFreeRam()).append("'/>");
      sb.append(offset).append("</state>");
      return sb.toString();
   }

   class Cpu
   {
      int id;
      int idle;
      public Cpu(int id, int idle) {
         this.id = id;
         this.idle = idle;
      }
   }
}

