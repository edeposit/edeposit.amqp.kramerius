#!/usr/bin/python
import pika
msg = open("resources/export-request.json","rb").read()
conn = pika.BlockingConnection(pika.URLParameters("http://guest:guest@localhost:5672/kramerius"))
channel = conn.channel()
channel.basic_publish("export", "request", msg,
                      pika.BasicProperties(content_type="application/json",
                                           delivery_mode=2))
