Požadavky
======================

aplikace:

- přijímá data k importu z aplikace ``RabbitMQ`` přes ``AMQP`` protokol
- vytváří ``FOXML`` soubor
- přijímá náhled první strany ve formátu ``jpeg2000``
- kopíruje importní balíček do ``Kramerius server`` přes ``scp``
  protokol
- odesílá archivní balíček do ``Storage server`` pres ``AMQP`` protokol
- startuje proces importu voláním ``REST API`` na  ``Kramerius server``
- zjištuje průběh importu voláním ``REST API`` na ``Kramerius server``
- maže importní balíček na ``Kramerius server`` poté, co jej
  ``Kramerius server`` úspěšně naimportuje
- posílá zpátky zprávu o úspěšném importu do aplikace ``RabbitMQ`` přes ``AMQP`` protokol
- kontroluje, že všechny linky ve ``FOXML`` souboru existují
- 1x import zopakuje, jestli dojde k chybě importu

Omezení
============================

aplikace:

- pouští v ``Kramerius server`` k importu jen jeden proces najednou.
  Až jeden import skončí, pustí druhý.
