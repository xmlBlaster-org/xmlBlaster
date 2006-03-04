#
#  File: convertJutilsToJavaLogging.sh   2006-03-04
#
#  Comment: Use to convert old xmlBlaster code before version 1.1.2
#           Replaces deprecated 'jutils logging' with java.util.logging
#
#  Usage:
#    for i in `find . -name "*.java"` ; do $XMLBLASTER_HOME/bin/convertJutilsToJavaLogging.sh $i ; done
#   or
#    find . -name "*.java" -exec $XMLBLASTER_HOME/bin/convertJutilsToJavaLogging.sh {} \;

echo "$0 Replacing file $1"
export BASE=`basename $1 .java`
export FN="$1"

# [ :#a-zA-Z1-9\"+().-]*,[ ]*
cp $1 $1.tmp; cat $1.tmp | sed s/"this\.log\."/"log."/ > $1

cp $1 $1.tmp; cat $1.tmp | sed s/".dump([ :#a-zA-Z1-9\"+().-]*,[ ]*"/".finest("/ > $1
cp $1 $1.tmp; cat $1.tmp | sed s/".call([ :#a-zA-Z1-9\"+().-]*,[ ]*"/".finer("/ > $1
cp $1 $1.tmp; cat $1.tmp | sed s/".trace([ :#a-zA-Z1-9\"+().-]*,[ ]*"/".fine("/ > $1
cp $1 $1.tmp; cat $1.tmp | sed s/".info([ :#a-zA-Z1-9\"+().-]*,[ ]*"/".info("/ > $1
cp $1 $1.tmp; cat $1.tmp | sed s/".warn([ :#a-zA-Z1-9\"+().-]*,[ ]*"/".warning("/ > $1
cp $1 $1.tmp; cat $1.tmp | sed s/".error([ :#a-zA-Z1-9\"+().-]*,[ ]*"/".severe("/ > $1
cp $1 $1.tmp; cat $1.tmp | sed s/"log.plain([ :#a-zA-Z1-9\"+().-]*,[ ]*"/"System.out.println("/ > $1
cp $1 $1.tmp; cat $1.tmp | sed s/".time([ :#a-zA-Z1-9\"+().-]*,[ ]*"/".finest("/ > $1

cp $FN $FN.tmp
cat $FN.tmp | sed s/"import org.jutils.log.LogChannel;"/"import java.util.logging.Logger;\\nimport java.util.logging.Level;"/ > $FN

# final LogChannel log = glob.getLog(null);
cp $FN $FN.tmp
cat $FN.tmp | sed s/".*final.*LogChannel.*=.*;"/"   private static Logger log = Logger.getLogger($BASE.class.getName());"/ > $FN

cp $FN $FN.tmp
cat $FN.tmp | sed s/".* this\.log[ ].*=.*getLog(.*"//   |   sed s/".* log[ ].*=.*getLog(.*"// > $FN

# private final LogChannel log;
cp $FN $FN.tmp
cat $FN.tmp | sed s/".*\(pr\).*LogChannel.*;"/"   private static Logger log = Logger.getLogger($BASE.class.getName());"/ > $FN

# final LogChannel log;
cp $FN $FN.tmp
cat $FN.tmp | sed s/".*final.*LogChannel.*;"/"   private static Logger log = Logger.getLogger($BASE.class.getName());"/ > $FN

# log.removeLogLevel("INFO");  log.addLogLevel("INFO");	 log.setLogLevel( log.changeLogLevel(
cp $FN $FN.tmp
cat $FN.tmp | sed s/".*\.*.LogLevel(\".*;"// > $FN

cp $FN $FN.tmp
cat $FN.tmp | sed s/"this\.log\."/"log."/ > $FN

#  if (log.TRACE) log.fine("Publishing " + entries.length + " volatile dead messages");
#  if (log.isLoggable(Level.FINE)) 

cp $FN $FN.tmp; cat $FN.tmp | sed s/"\.DUMP[ ]*)"/.isLoggable\(Level.FINEST\)\)/ > $FN
cp $FN $FN.tmp; cat $FN.tmp | sed s/"\.CALL[ ]*)"/.isLoggable\(Level.FINER\)\)/ > $FN
cp $FN $FN.tmp; cat $FN.tmp | sed s/"\.TRACE[ ]*)"/.isLoggable\(Level.FINE\)\)/ > $FN
cp $FN $FN.tmp; cat $FN.tmp | sed s/"\.INFO[ ]*)"/.isLoggable\(Level.INFO\)\)/ > $FN
cp $FN $FN.tmp; cat $FN.tmp | sed s/"\.WARN[ ]*)"/.isLoggable\(Level.WARNING\)\)/ > $FN
cp $FN $FN.tmp; cat $FN.tmp | sed s/"\.TRACE[ ]*)"/.isLoggable\(Level.FINE\)\)/ > $FN
cp $FN $FN.tmp; cat $FN.tmp | sed s/"\.ERROR[ ]*)"/.isLoggable\(Level.SEVERE\)\)/ > $FN

# Finally remove all LogChannel occurrences:
#  public MsgInterceptor(Global glob, LogChannel log, I_Callback testsuite) {
#   public final LogChannel getLog() {
#      return (LogChannel)this.weaklog.get();
cp $FN $FN.tmp
cat $FN.tmp | sed s/"LogChannel"/"Logger"/ > $FN

