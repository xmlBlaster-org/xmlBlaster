/*------------------------------------------------------------------------------
Name:      NodeStateInfo.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Mapping from domain informations to master id
Author:    ruff@swand.lake.de
------------------------------------------------------------------------------*/
package org.xmlBlaster.engine.cluster;

public class NodeStateInfo {
   public int getRamFree(){
         return ramFree;
      }

   public void setRamFree(int ramFree){
         this.ramFree = ramFree;
      }

   public int getAvgCpuIdl(){
         return avgCpuIdl;
      }

   public void setAvgCpuIdl(int avgCpuIdl){
         this.avgCpuIdl = avgCpuIdl;
      }

   private int ramFree;
   private int avgCpuIdl;
}
