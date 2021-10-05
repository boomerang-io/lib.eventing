package io.boomerang.eventing.nats.jetstream;

import java.io.IOException;
import java.lang.ref.Reference;
import java.nio.charset.StandardCharsets;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import io.boomerang.eventing.nats.ConnectionPrimer;
import io.boomerang.eventing.nats.ConnectionPrimerListener;
import io.boomerang.eventing.nats.jetstream.exception.NoNatsConnectionException;
import io.boomerang.eventing.nats.jetstream.exception.StreamNotFoundException;
import io.boomerang.eventing.nats.jetstream.exception.SubjectMismatchException;
import io.nats.client.Connection;
import io.nats.client.JetStreamApiException;
import io.nats.client.Message;
import io.nats.client.api.ConsumerConfiguration;
import io.nats.client.api.PublishAck;
import io.nats.client.api.StreamConfiguration;
import io.nats.client.api.StreamInfo;
import io.nats.client.impl.NatsMessage;

public class TinCanCommunication implements ConnectionPrimerListener {

  private static final Logger logger = LogManager.getLogger(TinCanCommunication.class);

  private final ConnectionPrimer connectionPrimer;

  private final StreamConfiguration streamConfiguration;

  private final ConsumerConfiguration consumerConfiguration;

  private final TinCanConfiguration tinCanConfiguration;

  private Reference<TinCanMessageListener> messageListenerRef;

  public TinCanCommunication(ConnectionPrimer connectionPrimer, StreamConfiguration streamConfiguration,
      ConsumerConfiguration consumerConfiguration) {
    this(connectionPrimer, streamConfiguration, consumerConfiguration, TinCanConfiguration.builder().build());
  }

  public TinCanCommunication(ConnectionPrimer connectionPrimer, StreamConfiguration streamConfiguration,
      ConsumerConfiguration consumerConfiguration, TinCanConfiguration tinCanConfiguration) {
    this.connectionPrimer = connectionPrimer;
    this.streamConfiguration = streamConfiguration;
    this.consumerConfiguration = consumerConfiguration;
    this.tinCanConfiguration = tinCanConfiguration;
  }

  public void publish(String subject, String message)
      throws IOException, JetStreamApiException, StreamNotFoundException {

    // Check if the subject matched stream wildcard subject
    Boolean subjectMatches = streamConfiguration.getSubjects().stream()
        .anyMatch(wildcard -> SubjectMatchChecker.doSubjectsMatch(subject, wildcard));

    if (!subjectMatches) {
      throw new SubjectMismatchException("Subject \"" + subject + "\" does not match any subjects of the stream!");
    }

    // Get NATS connection
    Connection connection = connectionPrimer.getConnection();

    if (connection == null) {
      throw new NoNatsConnectionException("No connection to the NATS server!");
    }

    // Get Jetstream stream from the NATS server
    StreamInfo streamInfo = StreamManager.getStreamInfo(connection, streamConfiguration);

    if (streamInfo == null) {

      // Create the stream automatically?
      if (tinCanConfiguration.isAutomaticallyCreateStream()) {
        streamInfo = StreamManager.createNewStream(connection, streamConfiguration);
      } else {
        throw new StreamNotFoundException("Jetstream could not be found! Consider enabling "
            + "`automaticallyCreateStream` in `TinCanConfiguration`");
      }
    }

    // Create the NATS message
    // @formatter:off
    Message natsMessage = NatsMessage.builder()
        .subject(subject)
        .data(message, StandardCharsets.UTF_8)
        .build();
    // @formatter:on

    // Publish the message
    PublishAck publishAck = connection.jetStream().publish(natsMessage);

    logger.debug("Message published to the stream! " + publishAck);
  }

  @Override
  public void connectionUpdated(ConnectionPrimer connectionPrimer) {
    // TODO Auto-generated method stub

  }
}
