#------------------------------------------------------------------------------
#Name:      Makefile
#Project:   xmlBlaster.org
#Comment:   Makefile for Java code
#------------------------------------------------------------------------------


#------------------------------------------------------------------------------
include $(XMLBLASTER_HOME)/Makefile.Include
CLASS_DIR = $(CLASS_TOP_DIR)/testsuite/org/xmlBlaster
SRC_JAVA_DIR = $(SRC_TESTSUITE_TOP_DIR)/org/xmlBlaster
#------------------------------------------------------------------------------


#------------------------------------------------------------------------------
SUB_DIRS = classtest clustertest query xmldb authentication mime
#LOCAL_FILES   := $(patsubst %.java,$(CLASS_DIR)/%.class,$(wildcard *.java))
LOCAL_FILES = \
    $(CLASS_DIR)/Util.class \
    $(CLASS_DIR)/TestCorbaThreads.class \
    $(CLASS_DIR)/LoadTestSub.class \
    $(CLASS_DIR)/RamTest.class \
    $(CLASS_DIR)/TestCallback.class \
    $(CLASS_DIR)/TestCallbackConfig.class \
    $(CLASS_DIR)/TestPersistence.class \
    $(CLASS_DIR)/TestPersistence2.class \
    $(CLASS_DIR)/TestPersistenceXMLDB.class \
    $(CLASS_DIR)/TestVolatile.class \
    $(CLASS_DIR)/TestPtD.class \
    $(CLASS_DIR)/TestPtDQueue.class \
    $(CLASS_DIR)/TestPub.class \
    $(CLASS_DIR)/TestPubBurstMode.class \
    $(CLASS_DIR)/TestPubForce.class \
    $(CLASS_DIR)/TestFailSave.class \
    $(CLASS_DIR)/TestFailSavePing.class \
    $(CLASS_DIR)/TestFailSaveAsync.class \
    $(CLASS_DIR)/TestSub.class \
    $(CLASS_DIR)/TestSubDispatch.class \
    $(CLASS_DIR)/TestSubExact.class \
    $(CLASS_DIR)/TestSubXPath.class \
    $(CLASS_DIR)/TestSubXPathMany.class \
    $(CLASS_DIR)/TestSubMulti.class \
    $(CLASS_DIR)/TestGet.class \
    $(CLASS_DIR)/TestUnSub.class \
    $(CLASS_DIR)/TestInvocationRecorder.class \
    $(CLASS_DIR)/TestSubManyClients.class \
    $(CLASS_DIR)/TestSubLostClient.class \
    $(CLASS_DIR)/TestAll.class \
    $(CLASS_DIR)/AllTests.class
#------------------------------------------------------------------------------

$(CLASS_DIR)/%.class: %.java
	$(JAVAC) $<

all: precond java all_SUB_DIRS

precond:
	$(MKDIR_RECURSIVE) $(CLASS_DIR)

java: $(LOCAL_FILES)
	@echo -e "\033[1mDONE ............ \033[32;40mAll of $(SRC_JAVA_DIR)\033[0m";

clean: clean_SUB_DIRS
	@echo "Removing all in $(CLASS_DIR) ..."
	$(RM) $(CLASS_DIR)/*.*
	$(RM) *.html

doc:
	$(JAVADOC) -d $(THIS_JAVADOC_DIR) $(SRC_JAVA_DIR)/*.java

sleep4:
	sleep 4

xmlBlaster: all
	@echo -e "\nStarting xmlBlaster server (without jit) ...\n"
	xterm -bg yellow -fg black -sb -sl 4000 -geom 160x24 \
	      -T "xmlBlaster server without JIT and ${BlasterSystemProperties} ${BlasterServerMemory}" \
	      -e ${JAVA_WRAP} -Djava.compiler= ${BlasterSystemProperties} ${BlasterServerMemory} org.xmlBlaster.MainGUI &

xmlBlaster-console: all
	@echo -e "\nStarting xmlBlaster server (without jit) ...\n"
	${JAVA_WRAP} -Djava.compiler= ${BlasterSystemProperties} ${BlasterServerMemory} org.xmlBlaster.Main &

testsuite:
	@echo -e "\nStarting xmlBlaster test suite (without jit) ...\n"
	${JAVA_WRAP} -Djava.compiler= $(BlasterSystemProperties) junit.ui.TestRunner -noloading testsuite.org.xmlBlaster.TestAll

testsuite-console:
	@echo -e "\nStarting xmlBlaster test suite (without jit) ...\n"
	${JAVA_WRAP} -Djava.compiler= $(BlasterSystemProperties) junit.textui.TestRunner -noloading testsuite.org.xmlBlaster.TestAll

test: xmlBlaster sleep4 testsuite

test-console: xmlBlaster-console sleep4 testsuite-console

#------- Generate syntax highlighted html files from java files for reference manual
objects := $(patsubst %.java,%.html,$(wildcard *.java))

$(objects): %.html: %.java
	code2html -ljava $< $@

html: html_SUB_DIRS $(objects)
	@echo "Generated syntax highlighted html files from java files"

