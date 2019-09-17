#開発用 modbatを修正した時はallを、モデルだけ修正した時はmqtttestを使用
MODBAT_PATH := ..
MODBAT := $(MODBAT_PATH)/build/modbat.jar
OPTION := -n=1 --log-path=./log
MODEL := FirstTest

all:
	cd $(MODBAT_PATH); ./gradlew build -x test
	-zip -d $(MODBAT) META-INF/ECLIPSE_.RSA META-INF/ECLIPSE_.SF
	make mqtttest
mqtttest: $(MODBAT) $(MODEL).scala
	scalac -cp $(MODBAT) $(MODEL).scala
.PHONY: run cleanlog
run:
	scala -cp .:$(MODBAT) $(MODBAT) $(OPTION) $(MODEL)
cleanlog:
	find -name "*.log" | xargs rm
