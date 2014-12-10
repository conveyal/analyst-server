# Analyst Server

Analyst Server is a graphical user interface-driven, web-based tool for accessibility analysis of transportation networks:

<img src="splash.png" alt="Analyst Server performing accessibility analysis in Portland, Ore." />

Its current focus is on public transportation systems, but that is not a design goal; that is simply what has been implemented
so far. It allows the user to upload a representation of a transit network (real or hypothetical) and perform analysis
against it almost immediately.

## Installation

First, clone the repository locally. The only external dependency is a copy of [Play](http://www.playframework.com) and
a [vanilla extract](https://github.com/conveyal/vanilla-extract.git) server, which may be local or remote; configure its
URL in conf/application.conf.

Start the program by typing `activator run -mem <large number>`. The number is in megabytes, and should be as much as
you can spare; I typically use 8GB (8192), although you should be able to get by with less. Create the first user by going to http://localhost:9000/createUser?username=...&password=...&email=...

Finally, browse to [http://localhost:9000](http://localhost:9000) to log in and start the tutorial.
