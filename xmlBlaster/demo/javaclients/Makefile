#------------------------------------------------------------------------------
#Name:      Makefile
#Project:   xmlBlaster
#Comment:   Makefile for Java code
#------------------------------------------------------------------------------


#------------------------------------------------------------------------------
include $(XMLBLASTER_HOME)/Makefile.Include
CLASS_DIR = $(CLASS_TOP_DIR)/javaclients
SRC_JAVA_DIR = $(XMLBLASTER_HOME)/demo/javaclients
#------------------------------------------------------------------------------

#------------------------------------------------------------------------------
SUB_DIRS = chat jdbc HelloWorldApplet
#------------------------------------------------------------------------------


#------------------------------------------------------------------------------
LOCAL_FILES = \
			$(CLASS_DIR)/Util.class \
			$(CLASS_DIR)/BlasterCallbackImpl.class \
			$(CLASS_DIR)/ClientOid.class \
			$(CLASS_DIR)/ClientGet.class \
			$(CLASS_DIR)/ClientXml.class \
			$(CLASS_DIR)/ClientSub.class \
			$(CLASS_DIR)/ClientQuery.class \
			$(CLASS_DIR)/ClientRaw.class \
			$(CLASS_DIR)/ClientErase.class \
			$(CLASS_DIR)/ClientPubDestination.class 
#------------------------------------------------------------------------------


$(CLASS_DIR)/%.class : $(SRC_JAVA_DIR)/%.java
	$(JAVAC) $(SRC_JAVA_DIR)/$(notdir $<)

all: all_SUB_DIRS precond java

precond:
	$(MKDIR_RECURSIVE) $(CLASS_DIR)

java: $(LOCAL_FILES)
	@echo -e "\033[1mDONE ............ \033[32;40mAll of $(SRC_JAVA_DIR)\033[0m";

clean: clean_SUB_DIRS
	@echo "Removing all in $(CLASS_DIR) ..."
	$(RM) $(CLASS_DIR)/*.*

sleep4:
	sleep 4

xmlBlaster: all
	@echo -e "\nStarting xmlBlaster server (without jit) ...\n"
	xterm -bg yellow -fg black -sb -sl 4000 -geom 160x24 \
	      -T "xmlBlaster server without JIT and ${BlasterSystemProperties} ${BlasterServerMemory}" \
	      -e ${JAVA_WRAP} -Djava.compiler= ${BlasterSystemProperties} ${BlasterServerMemory} org.xmlBlaster.MainGUI &

