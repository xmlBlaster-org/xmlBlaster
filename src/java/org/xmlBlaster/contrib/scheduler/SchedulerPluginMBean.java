package org.xmlBlaster.contrib.scheduler;

import org.quartz.SchedulerException;
import org.xmlBlaster.util.XmlBlasterException;

public interface SchedulerPluginMBean {

   String removeScheduler(String name);
   void shutdown() throws XmlBlasterException;
   String getSchedulerList();
   String addScheduler(String name, String value);
   String getJobNames() throws SchedulerException;

}
