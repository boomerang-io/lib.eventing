<div align="center">

# 📦 This repository is archived

**`io.boomerang:lib-eventing` — the CloudEvents + NATS eventing library of Boomerang Flow v3 (last release 0.4.5).**

Flow v5 has no separate eventing library: inbound CloudEvents are accepted by service-core at `POST /api/v2/event`, and outbound events leave through a transactional outbox — no NATS, no broker.

[![Boomerang Flow v5](https://img.shields.io/badge/Boomerang%20Flow-v5%20monorepo-6f42c1?style=for-the-badge&logo=github)](https://github.com/boomerang-io/flow)
[![Issues](https://img.shields.io/badge/Issues-boomerang--io%2Fflow-1d76db?style=for-the-badge)](https://github.com/boomerang-io/flow/issues/new/choose)

</div>

> [!IMPORTANT]
> **Archived on 2026-09-01.** Read-only; nothing is maintained here.
> | What you probably want | Where |
> | --- | --- |
> | How Flow v5 sends and receives events | [`specifications/execution-model.md`](https://github.com/boomerang-io/flow/blob/feat-v5/specifications/execution-model.md) ("Outbound events: the transactional outbox") and [`specifications/api-contract.md`](https://github.com/boomerang-io/flow/blob/feat-v5/specifications/api-contract.md) ("Webhook and event endpoints"); code in `service-core/src/main/java/io/boomerang/event/` |
> | The library as Flow v3 used it | this archive — `io.boomerang:lib-eventing:0.4.5`, referenced from the v3 line of `boomerang-io/flow` (`essentials_v3_stable` branch, `pom.xml`) |
> | The specification it implemented | [CloudEvents](https://cloudevents.io/) — still the wire format in v5 |
>
> If a security fix is ever needed for a v3 install, unarchive, patch, tag, and re-archive.

---

<details>
<summary>Original README (historical)</summary>

# Boomerang Eventing Library <!-- omit in toc -->

## Table of Contents

- [Table of Contents](#table-of-contents)
- [Description](#description)
- [Dependencies](#dependencies)
  - [Compile Maven Dependencies](#compile-maven-dependencies)
  - [Test Maven Dependencies](#test-maven-dependencies)
- [Installation](#installation)
  - [Using Maven](#using-maven)
- [Basic usage](#basic-usage)
  - [Connecting](#connecting)
  - [Publishing messages to Jetstream](#publishing-messages-to-jetstream)
  - [Subscribing to receive messages from Jetstream](#subscribing-to-receive-messages-from-jetstream)
  - [Note](#note)
- [Known Issues](#known-issues)
- [Contributing](#contributing)
- [License](#license)

## Description

Boomerang Eventing Library (`lib.eventing`) is a Maven plugin that integrates event streaming technologies, currently limited to [NATS Jetstream][1] only.

This plugin has been built on top of [`jnats`][3] client library, targeting an easier integration with NATS Jetstream for the purposes of Boomerang platform, as well as for external services that want to publish and subscribe to Flow-related events to/from Boomerang through NATS.

## Dependencies

### Compile Maven Dependencies

1. Jnats ([`jnats`][3])
2. Apache Log4j Core ([`log4j-core`][4])
3. JaCoCo Maven Plugin ([`jacoco-maven-plugin`][2]) for generating code coverage reports

### Test Maven Dependencies

1. JUnit ([`junit`][5])
2. NATS server ([`nats-streaming-server-embedded`][9])
3. Awaitility ([`awaitility`][10])

## Installation

### Using Maven

Boomerang Eventing plugin is published to [GitHub Packages][6]. To install the package from GitHub's Apache Maven registry, edit the `pom.xml` file to include the package as a dependency:

```xml
<dependency>
  <groupId>io.boomerang</groupId>
  <artifactId>lib-jetstream</artifactId>
  <version>0.0.2</version>
</dependency>
```

Keep in mind that public GitHub packages require authentication, thus you need to authenticate to GitHub Packages with an access token. For more information, see ["Authenticating to GitHub Packages"][7].

Once you have set up the authentication to GitHub Packages and the plugin package has been added to your project's `pom.xml` file, you can build your project:

```bash
# Install
mvn install

# ...or compile
mvn compile

# ...or package
mvn package
```

## Basic usage

Sending and receiving messages through NATS is as simple as connecting to the NATS server and publishing or subscribing for messages.

### Connecting

The object responsible for handling the connection to the NATS server is named `ConnectionPrimer`, it is build on top of [`jnats`][3] `Connection`, thus sharing a lot of properties and options with [`jnats`][3].

There are multiple ways to connect to the NATS server with `lib.eventing`:

1. Connect to a one or more NATS servers by specifying one or more servers:

```java
// Connect to one server
ConnectionPrimer connectionPrimer = new ConnectionPrimer("nats://localhost:4222");

// Connect to multiple servers
ConnectionPrimer connectionPrimer = new ConnectionPrimer(List.of(
    "nats://mynats1:4222",
    "nats://mynats2:4222",
    "nats://mynats3:4222"));
```

2. Connect to NATS by providing a connection configuration `Options.Builder` object (from [`jnats`][3], see [class source code](https://github.com/nats-io/nats.java/blob/2f1f1c978dbaeb7eedf9408f718f862eaf4dc1d7/src/main/java/io/nats/client/Options.java#L543)):

```java
ConnectionPrimer connectionPrimer = new ConnectionPrimer(new Options.Builder()
    .server("nats://localhost:4222")
    .reconnectWait(Duration.ofSeconds(10)));
```

Take note that using this approach will overwrite the `connectionListener` and `errorListener` handlers (if set), since `ConnectionPrimer` needs these handlers to manage automatically the connection to the NATS server.

### Publishing messages to Jetstream

Once the connection to the server is established, there are two ways of publishing messages - through `PubTransmitter` and `PubSubTransceiver`, the difference between both is that the latter also supports subscribing for receiving messages, while `PubTransmitter` is just for publishing messages.

`PubTransmitter` objects implements the interface `PubOnlyTunnel` and there are three components that must to be provided when creating a `PubTransmitter` (for `PubSubTransceiver` see "[Subscribing to receive messages from Jetstream](#subscribing-to-receive-messages-from-jetstream)"):

1. `ConnectionPrimer` - see [connection](#connecting) to NATS server.
2. `StreamConfiguration` - this object is from [`jnats`][3] and is used to specify the configuration for retrieving or creating a Jetstream Stream on the server.
3. `PubOnlyConfiguration` - this class specifies the configuration for managing a NATS Jetstream Stream on the server, for example `automaticallyCreateStream`, a property that tells the library if it needs to automatically create a new Stream if one has not been found on the server. See class' javadoc for more information.

```java
ConnectionPrimer connectionPrimer = new ConnectionPrimer("nats://localhost:4222");
StreamConfiguration streamConfiguration = StreamConfiguration.builder()
    .name("test-stream")
    .storageType(StorageType.File)
    .subjects("test.one.*", "test.two.*", "test.six.>")
    .build();
PubOnlyConfiguration pubOnlyConfiguration = new PubOnlyConfiguration.Builder()
    .automaticallyCreateStream(true)
    .build();
PubOnlyTunnel pubOnlyTunnel =
    new PubTransmitter(connectionPrimer, streamConfiguration, pubOnlyConfiguration);
```

Once the `PubTransmitter` object has been created, you can easily publish messages to NATS Jetstream:

```java
String subject = "test.one.hello";
String message = "Hello world!";
pubOnlyTunnel.publish(subject, message);
```

Keep in mind that when publishing a message to NATS Jetstream, `lib.eventing` checks the subject of the message to be published to have an exact match with the Stream's subject, be it a wildcard or a fixed subject string. If it matches - the message is sent to the server, if not - an exception is raised. See [NATS subject wildcard format][11] for more information.

### Subscribing to receive messages from Jetstream

To start receiving messages from NATS Jetstream, the procedure is similar to configuring a `PubTransmitter` object. `PubSubTransceiver` implements the interface `PubSubTunnel` and there are four required components when creating the object:

1. `ConnectionPrimer` - see [connection](#connecting) to NATS server.
2. `StreamConfiguration` - same as for `PubTransmitter`, this object is from [`jnats`][3] and is used to specify the configuration for retrieving or creating a Jetstream Stream on the server.
3. `ConsumerConfiguration` - this object is also from [`jnats`][3] and is used to specify the configuration for retrieving or creating a Jetstream Consumer on the server.
4. `PubSubConfiguration` - this class specifies the configuration for managing both a NATS Jetstream Stream and a Consumer on the server, for example `automaticallyCreateConsumer`, a property that tells the library if it needs to automatically create a new Consumer if one has not been found on the server. See class' javadoc for more information.

```java
ConnectionPrimer connectionPrimer = new ConnectionPrimer("nats://localhost:4222");
StreamConfiguration streamConfiguration = StreamConfiguration.builder()
    .name("test-stream")
    .storageType(StorageType.File)
    .subjects("test.one.*", "test.two.*", "test.six.>")
    .build();
ConsumerConfiguration consumerConfiguration = ConsumerConfiguration.builder()
    .durable("test-consumer-pull")
    .build();
PubSubConfiguration pubSubConfiguration = new PubSubConfiguration.Builder()
    .automaticallyCreateStream(true)
    .automaticallyCreateConsumer(true)
    .build();
// @formatter:on
PubSubTunnel pubSubTunnel = new PubSubTransceiver(connectionPrimer, streamConfiguration,
    consumerConfiguration, pubSubConfiguration);
```

Once the `PubSubTransceiver` has been created, subscribing to receive messages from the NATS Jetstream is as simple as:

```java
pubSubTunnel.subscribe(new SubHandler() {
  @Override
  public void newMessageReceived(PubSubTunnel pubSubTunnel, String subject, String message) {
    System.out.println("Received message with subject: " + subject + "\nMessage:\n" + message);
  }
});
```

When configuring a NATS Jetstream Consumer, you can use both push-based and pull-based Consumer types, `lib.eventing` will automatically detect the difference between these and will subscribe to the server accordingly.

### Note

`ConnectionPrimer` object besides handling the connection and re-connection (if this has been lost) to the NATS server, it will also resubscribe any active listening `PubSubTransceiver` objects when a connection to the NATS server has been re-established.

## Known Issues

- If the connection to the NATS server is down, any published messaged through `PubTransmitter` or `PubSubTransceiver` will fail immediately. Client side fault tolerance needs to be implemented.

## Contributing

Pull requests are welcome. For major changes, please open an issue first to discuss what you would like to change.

Please make sure to update tests as appropriate.

## License

All of our work is licenses under the [Apache License Version 2.0][8] unless specified otherwise due to constraints by dependencies.

[1]: https://docs.nats.io/jetstream/jetstream "About JetStream"
[2]: https://mvnrepository.com/artifact/org.jacoco/jacoco-maven-plugin "Maven Repository - JaCoCo Maven Plugin"
[3]: https://mvnrepository.com/artifact/io.nats/jnats "Maven Repository - Jnats"
[4]: https://mvnrepository.com/artifact/org.apache.logging.log4j/log4j-core "Maven Repository - The Apache Log4j Implementation"
[5]: https://mvnrepository.com/artifact/junit/junit "Maven Repository – JUnit is a unit testing framework for Java"
[6]: https://docs.github.com/en/packages "GitHub Packages"
[7]: https://docs.github.com/en/packages/working-with-a-github-packages-registry/working-with-the-apache-maven-registry#authenticating-to-github-packages "Authenticating to GitHub Packages"
[8]: https://www.apache.org/licenses/LICENSE-2.0 "Apache License Version 2.0"
[9]: https://mvnrepository.com/artifact/berlin.yuna/nats-server "Maven Repository - NATS Streaming Server"
[10]: https://mvnrepository.com/artifact/org.awaitility/awaitility/4.1.1 "Maven Repository - Awaitility: a Java DSL for synchronizing asynchronous operations"
[11]: https://docs.nats.io/nats-concepts/subjects#wildcards "NATS subject-based messaging - wildcard"

</details>
