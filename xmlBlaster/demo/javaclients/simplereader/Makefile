#------------------------------------------------------------------------------
#Name:      Makefile
#Project:   xmlBlaster
#Comment:   Makefile for Java code
#------------------------------------------------------------------------------


#------------------------------------------------------------------------------
include $(XMLBLASTER_HOME)/Makefile.Include
CLASS_DIR = $(CLASS_TOP_DIR)/javaclients/simplereader
SRC_JAVA_DIR = $(XMLBLASTER_HOME)/demo/javaclients/simplereader
#------------------------------------------------------------------------------


#------------------------------------------------------------------------------
LOCAL_FILES := $(patsubst %.java,$(CLASS_DIR)/%.class,$(wildcard *.java))
#------------------------------------------------------------------------------


#------------------------------------------------------------------------------
$(CLASS_DIR)/%.class : $(SRC_JAVA_DIR)/%.java
	$(JAVAC) $(SRC_JAVA_DIR)/$(notdir $<)
#------------------------------------------------------------------------------


all: precond java

precond:
	$(MKDIR_RECURSIVE) $(CLASS_DIR)
	$(CP) blue.gif $(CLASS_DIR)
	$(CP) red.gif $(CLASS_DIR)

java: $(LOCAL_FILES)
	@echo -e "\033[1mDONE ............ \033[32;40mAll of $(SRC_JAVA_DIR)\033[0m";

clean:
	@echo "Removing all in $(CLASS_DIR) ..."
	$(RM) $(CLASS_DIR)/*.*

sleep4:
	sleep 4

xmlBlaster: all
	@echo -e "\nStarting xmlBlaster server (without jit) ...\n"
	xterm -bg yellow -fg black -sb -sl 4000 -geom 160x24 \
	      -T "xmlBlaster server without JIT and ${BlasterSystemProperties} ${BlasterServerMemory}" \
	      -e ${JAVA_WRAP} -Djava.compiler= ${BlasterSystemProperties} ${BlasterServerMemory} org.xmlBlaster.MainGUI &

