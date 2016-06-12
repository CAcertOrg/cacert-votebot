/*
 * Copyright (c) 2016. Jan Dittberner
 *
 * This file is part of CAcert votebot.
 *
 * CAcert votebot is free software: you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by the Free
 * Software Foundation, either version 3 of the License, or (at your option)
 * any later version.
 *
 * CAcert votebot is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
 * or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License for
 * more details.
 *
 * You should have received a copy of the GNU General Public License along with
 * CAcert votebot.  If not, see <http://www.gnu.org/licenses/>.
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


/**
 * Vote bot.
 *
 * @author Felix Doerre
 * @author Jan Dittberner
 */
@SpringBootApplication(scanBasePackageClasses = {IRCClient.class, CAcertVoteBot.class})
@Component
public class CAcertVoteBot extends IRCBot implements Runnable, CommandLineRunner {
    private static final Logger LOGGER = LoggerFactory.getLogger(CAcertVoteBot.class);
    private static final int MILLIS_ONE_SECOND = 1000;

    /**
     * Meeting channel where votes and results are published.
     */
    @Value("${voteBot.meetingChn}")
    private String meetingChannel;

    /**
     * Channel name where voting is performed.
     */
    @Value("${voteBot.voteChn}")
    private String voteChannel;

    /**
     * Seconds to warn before a vote ends.
     */
    @Value("${voteBot.warnSecs}")
    private long warn;

    /**
     * Seconds before a vote times out.
     */
    @Value("${voteBot.timeoutSecs}")
    private long timeout;

    @Autowired
    private CAcertVoteMechanics voteMechanics;

    @Autowired
    private IRCClient ircClient;

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
        if (message.startsWith("vote ")) {
            final String response = voteMechanics.callVote(message.substring(5));
            sendPrivateMessage(from, response);

            if (response.startsWith("Sorry,")) {
                return;
            }

            announce("New Vote: " + from + " has started a vote on \"" + voteMechanics.getTopic() + "\"");
            sendPublicMessage(meetingChannel, "Please cast your vote in #vote");
            sendPublicMessage(voteChannel, "Please cast your vote in the next " + timeout + " seconds.");
        }
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
