Requirements
======================

application:

- receives import data from ``RabbitMQ`` as ``AMQP`` service
- creates ``FOXML`` file
- creates ``first page image`` from an ``original file``
- converts ``first page image`` to ``jpeg2000`` format using ``Kakadu`` software
- sends all data that ``Kramerius server`` needs to ``storage server`` by ``AMQP`` service
- copies ``FOXML`` file to ``Kramerius server`` by ``scp``
- calls ``REST API`` at ``Kramerius server`` to start import process
- calls ``REST API`` at ``Kramerius server`` to get to know import process status
- removes ``FOXML`` file at ``Kramerius server`` that was successfully imported
- sends back a message about export status to ``RabbitMQ`` by ``AMQP`` protocol
- validates that all links at import data exist
- calls ``convert`` binary (from ``ImageMagic`` package) to create ``first page image``

Constraints
============================

application:

- starts just one import process at a time at ``Kramerius server``
