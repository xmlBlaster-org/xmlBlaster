#------------------------------------------------------------------------------
#Name:      Makefile
#Project:   xmlBlaster
#Comment:   Makefile for Java based client utilities to access xmlBlaster
#------------------------------------------------------------------------------


#------------------------------------------------------------------------------
include $(XMLBLASTER_HOME)/Makefile.Include
CLASS_DIR = $(CLASS_TOP_DIR)/org/xmlBlaster/client/plugin/rmi
SRC_JAVA_DIR = $(SRC_JAVA_TOP_DIR)/org/xmlBlaster/client/plugin/rmi
#------------------------------------------------------------------------------


#------------------------------------------------------------------------------
SUB_DIRS =
LOCAL_FILES := $(patsubst %.java,$(CLASS_DIR)/%.class,$(wildcard *.java))
#------------------------------------------------------------------------------

#------------------------------------------------------------------------------
$(CLASS_DIR)/RmiCallbackServer_Stub.class : $(SRC_JAVA_DIR)/RmiConnection.java
        $(RMIC) org.xmlBlaster.client.protocol.rmi.RmiCallbackServer
#------------------------------------------------------------------------------

$(CLASS_DIR)/%.class: %.java
        $(JAVAC) $<

all:    all_SUB_DIRS precond java rmi policy

rmi: $(CLASS_DIR)/RmiCallbackServer_Stub.class
        @echo -e "\033[1mDONE ............ \033[32;40mRMI for $(SRC_JAVA_DIR)\033[0m";

precond:
        $(MKDIR_RECURSIVE) $(CLASS_DIR)

java: $(LOCAL_FILES)
        @echo -e "\033[1mDONE ............ \033[32;40mAll of $(SRC_JAVA_DIR)\033[0m";

policy:
        $(CP) $(XMLBLASTER_HOME)/config/xmlBlaster.policy $(CLASS_TOP_DIR)
        @echo -e "\033[1mDONE ............ \033[32;40mCopied $(XMLBLASTER_HOME)/config/xmlBlaster.policy to $(CLASS_TOP_DIR)\033[0m";

clean: clean_SUB_DIRS
        @echo "Removing all in $(CLASS_DIR) ..."
        $(RM) $(CLASS_DIR)/*.*

#doc:
#       $(JAVADOC) -d $(THIS_JAVADOC_DIR) $(SRC_JAVA_DIR)/*.java
