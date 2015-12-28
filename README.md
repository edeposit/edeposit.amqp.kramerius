# edeposit.amqp.kramerius
![travis status](https://travis-ci.org/edeposit/edeposit.amqp.kramerius.png)
[![Documentation Status](https://readthedocs.org/projects/edeposit-amqp-kramerius/badge/?version=latest)](https://readthedocs.org/projects/edeposit-amqp-kramerius/?badge=latest)

A Clojure library designed to ... well, that part is up to you.

[Full documentation](http://edeposit-amqp-kramerius.readthedocs.org/en/latest/)

## External utilities

There are some external programs for this module to work:

- pdftk
- gm

It is necessary to install them.

## Configuration

All configuration is at:

- resources/lein-env-example
- src/edeposit/amqp/kramerius/systems.clj

## Usage

- create vhost =kramerius= at RabbitMQ
- set permissions to this vhost 
- do those steps:

```
edeposit@edeposit-test:~/src> git clone https://github.com/edeposit/edeposit.amqp.kramerius.git
edeposit@edeposit-test:~/src> cd edeposit.amqp.kramerius/
edeposit@edeposit-test:~/src/edeposit.amqp.kramerius> cp resources/lein-env-example .lein-env
edeposit@edeposit-test:~/src/edeposit.amqp.kramerius> vim resources/lein-env-example .lein-env
edeposit@edeposit-test:~/src/edeposit.amqp.kramerius> lein run -- --amqp
```

## License

Copyright © 2015 Jan Stavel

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
