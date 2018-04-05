/*
 * Copyright (c) 2015  Felix Doerre
 * Copyright (c) 2015  Benny Baumann
 * Copyright (c) 2016, 2018  Jan Dittberner
 *
 * This file is part of CAcert VoteBot.
 *
 * CAcert VoteBot is free software: you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by the Free
 * Software Foundation, either version 3 of the License, or (at your option)
 * any later version.
 *
 * CAcert VoteBot is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
 * or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License for
 * more details.
 *
 * You should have received a copy of the GNU General Public License along with
 * CAcert VoteBot.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.cacert.votebot.vote;

import org.apache.commons.cli.ParseException;
import org.cacert.votebot.shared.CAcertVoteMechanics;
import org.cacert.votebot.shared.CAcertVoteMechanics.State;
import org.cacert.votebot.shared.IRCBot;
import org.cacert.votebot.shared.IRCClient;
import org.cacert.votebot.shared.exceptions.IRCClientException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.text.MessageFormat;
import java.time.Duration;
import java.util.Locale;
import java.util.ResourceBundle;


/**
 * VoteBot main class.
 *
 * @author Felix Doerre
 * @author Jan Dittberner
 */
@SpringBootApplication(scanBasePackageClasses = {IRCClient.class, CAcertVoteBot.class})
@Component
public class CAcertVoteBot extends IRCBot implements Runnable, CommandLineRunner {
    private static final Logger LOGGER = LoggerFactory.getLogger(CAcertVoteBot.class);
    private static final long MILLIS_ONE_SECOND = Duration.ofSeconds(1).toMillis();
    private final ResourceBundle messages = ResourceBundle.getBundle("messages");

    /**
     * Meeting channel where votes and results are published.
     */
    @Value("${voteBot.meetingChn:meeting}")
    private String meetingChannel;

    /**
     * Channel name where voting is performed.
     */
    @Value("${voteBot.voteChn:vote}")
    private String voteChannel;

    /**
     * Seconds to warn before a vote ends.
     */
    @Value("${voteBot.warnSecs:90}")
    private long warn;

    /**
     * Seconds before a vote times out.
     */
    @Value("${voteBot.timeoutSecs:120}")
    private long timeout;

    private final CAcertVoteMechanics voteMechanics;

    private final IRCClient ircClient;

    @Autowired
    public CAcertVoteBot(CAcertVoteMechanics voteMechanics, IRCClient ircClient) {
        this.voteMechanics = voteMechanics;
        this.ircClient = ircClient;
    }

    /**
     * {@inheritDoc}
     *
     * @param args command line arguments
     */
    @Override
    public final void run(final String... args) {
        try {
            getIrcClient().initializeFromArgs(args).assignBot(this);

            getIrcClient().join(meetingChannel);
            getIrcClient().join(voteChannel);

            new Thread(this).start();
        } catch (IOException | InterruptedException | ParseException | IRCClientException e) {
            LOGGER.error(String.format("error running votebot %s", e.getMessage()));
        }
    }

    @Override
    protected final IRCClient getIrcClient() {
        return ircClient;
    }

    @Override
    public final synchronized void publicMessage(final String from, final String channel, final String message) throws
            IRCClientException {
        if (channel.equals(voteChannel)) {
            sendPublicMessage(voteChannel, voteMechanics.evaluateVote(from, message));
        }
    }

    @Override
    public final synchronized void privateMessage(final String from, final String message) throws IRCClientException {
        if (message != null && message.length() > 0) {
            String[] parts = message.split("\\s+", 2);
            try {
                VoteBotCommand command = VoteBotCommand.valueOf(parts[0].toUpperCase(Locale.ENGLISH));
                switch (command) {
                    case VOTE:
                        startVote(from, parts[1]);
                        break;
                    case HELP:
                        giveHelp(from);
                        break;
                }
            } catch (IllegalArgumentException e) {
                sendUnknownCommand(from, parts[0]);
            }
        }
    }

    private void sendUnknownCommand(String from, String command) throws IRCClientException {
        sendPrivateMessage(from, String.format(messages.getString("unknown_command"), command));
    }

    private void giveHelp(String from) throws IRCClientException {
        sendPrivateMessage(from, messages.getString("help_message"));
    }

    private void startVote(final String from, final String message) throws IRCClientException {
        final String response = voteMechanics.callVote(message);
        sendPrivateMessage(from, response);

        if (response.startsWith("Sorry,")) {
            return;
        }

        announce(MessageFormat.format(messages.getString("new_vote"), from, voteMechanics.getTopic()));
        sendPublicMessage(
                meetingChannel,
                MessageFormat.format(messages.getString("cast_vote_in_vote_channel"), voteChannel));
        sendPublicMessage(
                voteChannel, MessageFormat.format(messages.getString("cast_vote_in_next_seconds"), timeout));
    }

    private synchronized void announce(final String msg) throws IRCClientException {
        sendPublicMessage(meetingChannel, msg);
        sendPublicMessage(voteChannel, msg);
    }

    @Override
    public final void run() {
        try {
            //noinspection InfiniteLoopStatement
            while (true) {
                while (voteMechanics.getState() == State.IDLE) {
                    Thread.sleep(MILLIS_ONE_SECOND);
                }

                Thread.sleep(warn * MILLIS_ONE_SECOND);
                announce("Voting on " + voteMechanics.getTopic() + " will end in " + (timeout - warn) + " seconds.");
                Thread.sleep((timeout - warn) * MILLIS_ONE_SECOND);
                announce("Voting on " + voteMechanics.getTopic() + " has closed.");
                final String[] res = voteMechanics.closeVote();
                announce("Results: for " + voteMechanics.getTopic() + ":");

                for (final String re : res) {
                    announce(re);
                }
            }
        } catch (final InterruptedException | IRCClientException e) {
            LOGGER.error(e.getMessage(), e);
        }
    }

    @Override
    public synchronized void join(final String referent, final String chn) {

    }

    @Override
    public synchronized void part(final String referent, final String channel) {

    }

    /**
     * Entry point for the vote bot.
     *
     * @param args command line arguments
     */
    public static void main(final String... args) {
        SpringApplication.run(CAcertVoteBot.class, args);
    }
}
