#------------------------------------------------------------------------------
#Name:      Makefile
#Project:   xmlBlaster
#Comment:   Makefile for Java code
#------------------------------------------------------------------------------


#------------------------------------------------------------------------------
include $(XMLBLASTER_HOME)/Makefile.Include
CLASS_DIR = $(CLASS_TOP_DIR)/org/xmlBlaster/protocol
SRC_JAVA_DIR = $(SRC_JAVA_TOP_DIR)/org/xmlBlaster.protocol
#------------------------------------------------------------------------------


#------------------------------------------------------------------------------
SUB_DIRS = corba email http jdbc
LOCAL_FILES = \
			$(CLASS_DIR)/I_CallbackDriver.class
#------------------------------------------------------------------------------


$(CLASS_DIR)/%.class : $(SRC_JAVA_DIR)/%.java
	$(JAVAC) $(SRC_JAVA_DIR)/$(notdir $<)

all: 	all_SUB_DIRS precond java

precond:
	$(MKDIR_RECURSIVE) $(CLASS_DIR)

java: $(LOCAL_FILES)
	@echo -e "\033[1mDONE ............ \033[32;40mAll of $(SRC_JAVA_DIR)\033[0m";

clean: clean_SUB_DIRS
	@echo -e "Removing all in $(CLASS_DIR) ..."
	$(RM) $(CLASS_DIR)/*.*

xmlBlaster:
	@echo -e "\nStarting xmlBlaster server ...\n"
	${JAVA_WRAP} ${BlasterSystemProperties} ${BlasterServerMemory} org.xmlBlaster.MainGUI

#doc:
#	$(JAVADOC) -d $(THIS_JAVADOC_DIR) $(SRC_JAVA_DIR)/*.java
