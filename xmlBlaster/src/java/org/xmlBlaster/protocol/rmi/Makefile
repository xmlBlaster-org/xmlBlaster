#------------------------------------------------------------------------------
#Name:      Makefile
#Project:   xmlBlaster
#Comment:   Makefile for Java code
#------------------------------------------------------------------------------


#------------------------------------------------------------------------------
include $(XMLBLASTER_HOME)/Makefile.Include
CLASS_DIR = $(CLASS_TOP_DIR)/org/xmlBlaster/protocol/rmi
SRC_JAVA_DIR = $(SRC_JAVA_TOP_DIR)/org/xmlBlaster/protocol/rmi
#------------------------------------------------------------------------------


#------------------------------------------------------------------------------
SUB_DIRS = 
LOCAL_FILES := $(patsubst %.java,$(CLASS_DIR)/%.class,$(wildcard *.java))
#------------------------------------------------------------------------------


#------------------------------------------------------------------------------
$(CLASS_DIR)/%.class : $(SRC_JAVA_DIR)/%.java
        $(JAVAC) $(SRC_JAVA_DIR)/$(notdir $<)
#------------------------------------------------------------------------------


#------------------------------------------------------------------------------
$(CLASS_DIR)/AuthServerImpl_Stub.class : $(SRC_JAVA_DIR)/AuthServerImpl.java
        $(RMIC) org.xmlBlaster.protocol.rmi.AuthServerImpl

$(CLASS_DIR)/XmlBlasterImpl_Stub.class : $(SRC_JAVA_DIR)/XmlBlasterImpl.java
        $(RMIC) org.xmlBlaster.protocol.rmi.XmlBlasterImpl
#------------------------------------------------------------------------------

all:    all_SUB_DIRS precond java rmi policy

precond:
        $(MKDIR_RECURSIVE) $(CLASS_DIR)

java: $(LOCAL_FILES)
        @echo -e "\033[1mDONE ............ \033[32;40mAll of $(SRC_JAVA_DIR)\033[0m";

rmi: $(CLASS_DIR)/XmlBlasterImpl_Stub.class $(CLASS_DIR)/AuthServerImpl_Stub.class
        @echo -e "\033[1mDONE ............ \033[32;40mRMI for $(SRC_JAVA_DIR)\033[0m";

policy:
        $(CP) $(XMLBLASTER_HOME)/config/xmlBlaster.policy $(CLASS_TOP_DIR)
        @echo -e "\033[1mDONE ............ \033[32;40mCopied $(XMLBLASTER_HOME)/config/xmlBlaster.policy to $(CLASS_TOP_DIR)\033[0m";

clean: clean_SUB_DIRS
        @echo -e "Removing all in $(CLASS_DIR) ..."
        $(RM) $(CLASS_DIR)/*.*

xmlBlaster:
        @echo -e "\nStarting xmlBlaster server ...\n"
        ${JAVA_WRAP} ${BlasterSystemProperties} ${BlasterServerMemory} org.xmlBlaster.MainGUI

#doc:
#       $(JAVADOC) -d $(THIS_JAVADOC_DIR) $(SRC_JAVA_DIR)/*.java
