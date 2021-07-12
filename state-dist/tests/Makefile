MODBAT_PATH:=../../..
MODBAT:=$(MODBAT_PATH)/build/modbat.jar

SUBDIRS := $(filter-out log/,$(wildcard */))

export LOG_PATH
export MODBAT_PATH


all: $(SUBDIRS)
$(SUBDIRS): LOG_PATH=../log/$@
$(SUBDIRS):
	@$(MAKE) -C $@

modbat:
	cd $(MODBAT_PATH); ./gradlew build -x test
	-zip -d $(MODBAT) META-INF/ECLIPSE_.RSA META-INF/ECLIPSE_.SF

.PHONY: all $(SUBDIRS) modbat
