Features
======================

application:

- receives import data from ``RabbitMQ`` by ``AMQP`` protocol
- creates ``FOXML`` file
- does not creates first page image
- receives first page image at import data
- copies ``FOXML`` file to ``Kramerius server``
- calls ``REST API`` at ``Kramerius server`` to start import process
- imports just one ePublication at a time into ``Kramerius server``
- calls ``REST API`` at ``Kramerius server`` to get to know import process status
- removes ``FOXML`` file at ``Kramerius server`` that was successfully imported
- sends back a message about export status to ``RabbitMQ`` by ``AMQP`` protocol

