#------------------------------------------------------------------------------
#Name:      Makefile
#Project:   xmlBlaster
#Comment:   Makefile for Java code
#------------------------------------------------------------------------------


#------------------------------------------------------------------------------
include $(XMLBLASTER_HOME)/Makefile.Include
CLASS_DIR = $(CLASS_TOP_DIR)/org/xmlBlaster/engine
SRC_JAVA_DIR = $(SRC_JAVA_TOP_DIR)/org/xmlBlaster/engine
#------------------------------------------------------------------------------


#------------------------------------------------------------------------------
SUB_DIRS = persistence
LOCAL_FILES = \
			$(CLASS_DIR)/ClientInfo.class \
			$(CLASS_DIR)/RequestBroker.class \
			$(CLASS_DIR)/ClientSubscriptions.class \
			$(CLASS_DIR)/MessageUnitWrapper.class \
			$(CLASS_DIR)/MessageUnitHandler.class \
			$(CLASS_DIR)/ClientUpdateQueue.class \
			$(CLASS_DIR)/CallbackCorbaDriver.class \
			$(CLASS_DIR)/XmlKey.class \
			$(CLASS_DIR)/BigXmlKeyDOM.class \
			$(CLASS_DIR)/SubscriptionInfo.class \
			$(CLASS_DIR)/SubscribeQoS.class \
			$(CLASS_DIR)/UnSubscribeQoS.class \
			$(CLASS_DIR)/EraseQoS.class \
			$(CLASS_DIR)/GetQoS.class \
			$(CLASS_DIR)/PublishQoS.class
#------------------------------------------------------------------------------


$(CLASS_DIR)/%.class : $(SRC_JAVA_DIR)/%.java
	$(JAVAC) $(SRC_JAVA_DIR)/$(notdir $<)

all: 	all_SUB_DIRS precond java

precond:
	$(MKDIR_RECURSIVE) $(CLASS_DIR)

java: $(LOCAL_FILES)
	@echo -e "\033[1mDONE ............ \033[32;40mAll of $(SRC_JAVA_DIR)\033[0m";

clean:
	@echo "Removing all in $(CLASS_DIR) ..."
	$(RM) $(CLASS_DIR)/*.*

#doc:
#	$(JAVADOC) -d $(THIS_JAVADOC_DIR) $(SRC_JAVA_DIR)/*.java
