#開発用 modbatを修正した時はallを、モデルだけ修正した時はmqtttestを使用
MODBAT_PATH ?= ../../..
MODBAT := $(MODBAT_PATH)/build/modbat.jar
LOG_PATH ?= ./log
OPTION := -n=100 --log-path=$(LOG_PATH) --log-level=info
MODEL := CounterTest

build_run: test run


all:
	cd $(MODBAT_PATH); ./gradlew build -x test
	-zip -d $(MODBAT) META-INF/ECLIPSE_.RSA META-INF/ECLIPSE_.SF
	make test
modbat:
	cd $(MODBAT_PATH); ./gradlew build -x test
	-zip -d $(MODBAT) META-INF/ECLIPSE_.RSA META-INF/ECLIPSE_.SF

test: $(MODBAT) $(MODEL).scala
	scalac -cp $(MODBAT) $(MODEL).scala

.PHONY: run cleanlog build_run
run:
	mkdir -p $(LOG_PATH)
	@make cleanlog
	scala -cp .:$(MODBAT) $(MODBAT) $(OPTION) $(MODEL)
cleanlog:
	find -name "$(LOG_PATH)*.log" | xargs rm -f
	find -name "$(LOG_PATH)*.err" | xargs rm -f
clean:
	make cleanlog
	find -name "*.class" | xargs rm -f
	find -name "paho*" | xargs rm -rf
