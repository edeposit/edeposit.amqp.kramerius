Požadavky
======================

aplikace:

- přijímá data k importu z aplikace ``RabbitMQ`` přes ``AMQP`` protokol
- vytváří ``FOXML`` soubor
- kopíruje ``FOXML`` soubor do ``Kramerius server`` přes ``scp`` protokol
- startuje proces importu voláním ``REST API`` na  ``Kramerius server``
- zjištuje průběh importu voláním ``REST API`` na ``Kramerius server``
- maže ``FOXML`` souboru na ``Kramerius server`` poté, co je
  ``Kramerius server`` úspěšně naimportuje
- posílá zpátky zprávu o úspěšném importu do aplikace ``RabbitMQ`` přes ``AMQP`` protokol
- kontroluje, že všechny linky ve ``FOXML`` souboru existují

Omezení
============================

aplikace:

- pouští v ``Kramerius server`` k importu jen jeden proces najednou.
  Až jeden import skončí, pustí druhý.
