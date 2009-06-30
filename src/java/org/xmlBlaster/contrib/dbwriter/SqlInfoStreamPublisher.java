package org.xmlBlaster.contrib.dbwriter;

import java.io.*;
import java.sql.Time;
import java.text.*;
import java.util.*;
import java.util.logging.Logger;
import org.xmlBlaster.contrib.*;
import org.xmlBlaster.contrib.dbwriter.info.SqlInfo;
import org.xmlBlaster.jms.XBSession;
import org.xmlBlaster.util.*;

public class SqlInfoStreamPublisher implements I_ChangePublisher, I_Timeout {

   private final static Logger log = Logger.getLogger(SqlInfoStreamPublisher.class.getName());

   private static final String DATE = "yyyy-MM-dd";
   private static final String DATE_HR = "yyyy-MM-dd'T'hh";
   private static final String DATE_MIN = "yyyy-MM-dd'T'hh:mm";
   private static final String DATE_SEC = "yyyy-MM-dd'T'hh:mm:ss";
   private static final String DATE_MILLI = "yyyy-MM-dd'T'hh:mm:ss.s";
   private static final String DATE_ALL = "yyyy-MM-dd'T'hh:mm:ss.sZ";
   private String TAG;
   private static final String INPUT_FILENAME_TXT = "mom.file.inputFile";
   private static final String IMMEDIATE_TXT = "mom.file.immediate";
   private static final String MIN_DELAY_TXT = "mom.file.minDelay";
   private long nextTime;
   private long referenceMidnite;
   private long thisMidnite;
   private String filename;
   private boolean immediate;
   protected I_Update eventHandler;
   private I_Info info;
   private boolean stopped;
   private Global glob;
   private BufferedReader br;
   private SqlInfoParser sqlInfoParser;
   private Timeout timeout;
   private long minDelay;
   private boolean isInitialized;
   private int count;
   private boolean continuous;
   private long offsetTime;
   private String startTime;
   
   
    public SqlInfoStreamPublisher() {
       TAG = "sql";
       stopped = false;
       offsetTime = 0L;
    }

    private static Date txtToDate(String dateTxt, String formatTxt) {
       try {
          SimpleDateFormat format = new SimpleDateFormat(formatTxt);
          return format.parse(dateTxt.trim());
       }
       catch (ParseException ex) {
          return null;
       }
    }

    private static long getStartTime(String txt) {
       if(txt == null || txt.trim().length() < 1)
          return 0L;
       Date date = null;
       if(txt == null || txt.trim().length() < 1) {
          date = new Date(System.currentTimeMillis());
       } 
       else {
          txt.replace('/', '-');
          date = txtToDate(txt, DATE_ALL);
          if(date == null)
             date = txtToDate(txt, DATE_MILLI);
          if(date == null)
             date = txtToDate(txt, DATE_SEC);
          if(date == null)
             date = txtToDate(txt, DATE_MIN);
          if(date == null)
             date = txtToDate(txt, DATE_HR);
          if(date == null)
             date = txtToDate(txt, DATE);
        }
        if(date == null) {
           log.warning("The time '" + txt + "' could not be parsed, will ignore it");
           return 0L;
        } 
        else
            return date.getTime();
    }

    public void parse() throws Exception {
       try {
          String line = null;
          br = getBufferedReader(br, filename);
          while (true) {
             if((line = br.readLine()) == null)
                break;
             String endTag = "</" + TAG + ">";
             String startTag = "<" + TAG;
             // String startComment = "<!--";
             // String endComment = "-->";
             String content = null;
             boolean contentProcessed = false;
             while (true) {
                if(contentProcessed || (line = br.readLine()) == null || stopped)
                   break;
                int pos = line.indexOf(startTag);
                if(pos > -1) {
                   content = getContent(pos, line, endTag);
                   contentProcessed = true;
                } 
                else {
                   pos = line.indexOf("<!--");
                   if(pos > -1)
                      processComment(pos, line, "-->");
                }
             }
             if(content != null) {
                if(sqlInfoParser != null) {
                   SqlInfo obj = sqlInfoParser.parse(content);
                   content = obj.toXml("");
                }
                System.out.println("Date (old):  " + new Date(nextTime));
                System.out.println("Date (new):  " + new Date(calculateNewTime(nextTime)));
                System.out.println("Date (old):  " + new Time(nextTime));
                System.out.println("Date (new):  " + new Time(calculateNewTime(nextTime)));
                System.out.println(content);
             }
          }
       }
       catch(IOException ex) {
          ex.printStackTrace();
       }
    }

    private String getContent(int pos, String origLine, String endTag) throws Exception {
       String line = origLine.substring(pos);
       StringBuffer buf = new StringBuffer();
       pos = line.indexOf(endTag);
       if(pos < 0) {
          buf.append(line);
          while (true) {
             if((line = br.readLine()) == null)
                break;
             pos = line.indexOf(endTag);
             if(pos > -1)
                break;
             buf.append(line);
          }
       }
       if(line == null) {
          return null;
       } 
       else {
          String endOfLine = line.substring(0, pos + endTag.length());
          buf.append(endOfLine);
          return buf.toString();
       }
    }

    private void processComment(int pos, String line, String endTag) throws Exception {
       String content = getContent(pos, line, endTag);
       if(content != null) {
          // String TOKEN = "currentTimestamp ";
          pos = content.indexOf("currentTimestamp ");
          if(pos > -1) {
             content = content.substring(pos + "currentTimestamp ".length());
             pos = content.indexOf(' ');
             if(pos > -1) {
                content = content.substring(0, pos);
                try {
                   nextTime = Long.parseLong(content.trim());
                }
                catch(Throwable ex) {
                   ex.printStackTrace();
                }
             }
          }
       }
    }

    private long calculateMidnite(long time) {
       long round = 0x5265c00L;
       long days = time / round;
       return days * round;
    }

    private long calculateNewTime(long time) {
       if(startTime == null) {
          if(thisMidnite == 0L)
             thisMidnite = calculateMidnite(System.currentTimeMillis());
          if(referenceMidnite == 0L)
             referenceMidnite = calculateMidnite(time);
          return (time - referenceMidnite) + thisMidnite;
       }
       if(offsetTime == 0L) {
          offsetTime = getStartTime(startTime) - time;
          if(offsetTime < 0L)
             offsetTime = 0L;
       }
       return time + offsetTime;
    }

    public XBSession getJmsSession() {
       return null;
    }

    public void init(I_Info info_) throws Exception {
       info = info_;
       Global globOrig = (Global)info.getObject("org.xmlBlaster.engine.Global");
       if(globOrig == null) {
          if(info instanceof GlobalInfo) {
             glob = ((GlobalInfo)info).getGlobal();
          } 
          else {
             Iterator iter = info.getKeys().iterator();
             ArrayList argsList = new ArrayList();
             while(true) {
                if(!iter.hasNext())
                   break;
                String key = (String)iter.next();
                String value = info.get(key, null);
                if(value != null) {
                   argsList.add("-" + key);
                   argsList.add(value);
                }
             }
             glob = new Global((String[])(String[])argsList.toArray(new String[argsList.size()]));
          }
          
       } 
       else {
          glob = globOrig.getClone(globOrig.getNativeConnectArgs());
          glob.addObjectEntry("ServerNodeScope", globOrig.getObjectEntry("ServerNodeScope"));
       }
       
       filename = info.get(INPUT_FILENAME_TXT, null);
       immediate = info.getBoolean(IMMEDIATE_TXT, false);
       minDelay = info.getLong(MIN_DELAY_TXT, 0L);
       if(filename == null)
          throw new Exception("The property 'mom.file.inputFile' is mandatory: input the location of the file from which to read");
       info.putObject("org.xmlBlaster.contrib.dbwriter.mom.MomEventEngine", this);
       info.putObject("org.xmlBlaster.contrib.dbwatcher.mom.I_EventEngine", this);
       continuous = info.getBoolean("replication.player.continuous", true);
       startTime = info.get("replication.player.startTime", null);
       br = getBufferedReader(br, filename);
       if(br == null) {
          throw new Exception("File not found '" + filename + "'");
       } 
       else {
          sqlInfoParser = null;
          isInitialized = true;
          startTimeout();
          return;
       }
    }

    
    private BufferedReader getBufferedReader(BufferedReader br, String filename) {
       int internCount;
       if(br != null) {
          try {
             br.close();
          }
          catch(IOException ex) {
             log.severe("Could not close the file '" + filename + "' instance '" + count + "'");
             ex.printStackTrace();
          }
       }
       br = null;
       count++;
       if(filename == null || count < 0)
          return null;
       internCount = 0;

       String tmpFilename = "";
       DecimalFormat format = new DecimalFormat("000000");
       tmpFilename = filename + format.format(count);

       while (br == null) {
          try {
             FileReader in = new FileReader(tmpFilename);
             br = new BufferedReader(in);
          }
          catch (FileNotFoundException ex) {
             if(!continuous || stopped)
                return null;
          }
          log.info("New file '" + filename + "' opened");
          
          try {
             Thread.sleep(5000L);
          }
          catch(InterruptedException e) {
             e.printStackTrace();
          }
          if(internCount > 60)
             internCount = 0;
          if(internCount == 0)
             log.info("Still waiting for the file '" + tmpFilename + "' to be put on place");
          internCount++;
       }
       
       return br;
    }

    public String publish(String changeKey, byte message[], Map attrMap) throws Exception {
        return null;
    }

    public boolean registerAlertListener(I_Update update, Map attrs) throws Exception {
       if(eventHandler != null) {
          return false;
       } 
       else {
          eventHandler = update;
          startTimeout();
          return true;
       }
    }

    private void startTimeout() {
       if(eventHandler != null && isInitialized) {
          timeout = new Timeout("replication.streamFeeder");
          timeout.addTimeoutListener(this, 1000L, null);
       }
    }

    public void shutdown() {
       stopped = true;
       if(timeout != null) {
          timeout.removeAll();
          timeout.shutdown();
          timeout = null;
       }
       if(br != null) {
          try {
             br.close();
          }
          catch(Exception ex) {
             ex.printStackTrace();
          }
       }
    }

    public Set getUsedPropertyKeys() {
       Set set = new HashSet();
       set.add(INPUT_FILENAME_TXT);
       set.add(IMMEDIATE_TXT);
       return set;
    }

    public void timeout(Object userData) {
       String endTag = "</" + TAG + ">";
       String startTag = "<" + TAG;
       String startComment = "<!--";
       String endComment = "-->";
       String content = null;
       boolean contentProcessed = false;
       boolean doExit = false;
       try {
          content = (String)userData;
          if(eventHandler != null && content != null) {
             ByteArrayInputStream bais = new ByteArrayInputStream(content.getBytes());
             eventHandler.update("replication", bais, new HashMap());
          }
          while (true) {
             if(contentProcessed)
                break;
             if(br == null) {
                doExit = true;
                break;
             }
             String line = br.readLine();
             while (true) {
                if(line != null || br == null)
                   break;
                br = getBufferedReader(br, filename);
                if(br != null)
                   line = br.readLine();
             }
             
             if(br == null)
                stopped = true;
             if(stopped) {
                doExit = true;
                break;
             }
             
             int pos = line.indexOf(startTag);
             if(pos > -1) {
                content = getContent(pos, line, endTag);
                contentProcessed = true;
             } 
             else {
                pos = line.indexOf("<!--");
                if(pos > -1)
                   processComment(pos, line, "-->");
             }
          }
          
          if(content != null) {
             if(sqlInfoParser != null) {
                SqlInfo obj = sqlInfoParser.parse(content);
                content = obj.toXml("");
             }
             long fireTime = calculateNewTime(nextTime);
             long delay = fireTime - System.currentTimeMillis();
             if(delay < 0L || immediate)
                delay = minDelay;
             System.out.println("Date (old):  " + new Date(nextTime));
             System.out.println("Date (new):  " + new Date(fireTime));
             if(timeout != null && content != null)
                timeout.addTimeoutListener(this, delay, content);
          } 
          else {
             doExit = true;
          }
       }
       catch(Exception ex) {
          ex.printStackTrace();
          doExit = true;
       }
       if(doExit) {
          // System.exit(0);
          log.info("THE END OF THE INPUT FILES HAS BEEN REACHED: you can stop the application now");
       }
    }

    public static void main(String args[]) {
       SqlInfoStreamPublisher parser = new SqlInfoStreamPublisher();
       PropertiesInfo info = new PropertiesInfo(System.getProperties());
       info.put(INPUT_FILENAME_TXT, args[0]);
       info.put(IMMEDIATE_TXT, "true");
       try {
          parser.init(info);
          parser.parse();
       }
       catch(Exception ex) {
          ex.printStackTrace();
       }
    }

}
