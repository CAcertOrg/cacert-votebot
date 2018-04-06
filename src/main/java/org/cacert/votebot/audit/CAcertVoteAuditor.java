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

package org.cacert.votebot.audit;

import org.apache.commons.cli.ParseException;
import org.cacert.votebot.shared.CAcertVoteMechanics;
import org.cacert.votebot.shared.IRCBot;
import org.cacert.votebot.shared.IRCClient;
import org.cacert.votebot.shared.VoteType;
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
import java.util.Arrays;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Auditor bot for votes.
 *
 * @author Felix Doerre
 * @author Jan Dittberner
 */
@SpringBootApplication(scanBasePackageClasses = {CAcertVoteAuditor.class, IRCClient.class})
@Component
public class CAcertVoteAuditor extends IRCBot implements CommandLineRunner {
    private static final Logger LOGGER = LoggerFactory.getLogger(
            CAcertVoteAuditor.class);
    private static final String NEW_VOTE_REGEX =
            "New Vote: (.*) has started a vote on \"(.*)\"";

    @Value("${auditor.target.nick}")
    private String toAudit;

    @Value("${auditor.target.voteChn}")
    private String voteAuxChn;

    private final IRCClient ircClient;

    private final CAcertVoteMechanics voteMechanics;

    private final String[] capturedResults = new String[VoteType.values().length];

    private int counter = -1;

    @Autowired
    public CAcertVoteAuditor(IRCClient ircClient) {
        this.ircClient = ircClient;
        this.voteMechanics = new CAcertVoteMechanics();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected final IRCClient getIrcClient() {
        return ircClient;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public final synchronized void publicMessage(final String from, final String channel, final String message) {
        if (channel.equals(voteAuxChn)) {
            if (from.equals(toAudit)) {
                if (counter >= 0) {
                    capturedResults[counter++] = message;

                    if (counter == capturedResults.length) {
                        final String[] reals = voteMechanics.closeVote();

                        if (Arrays.equals(reals, capturedResults)) {
                            LOGGER.info("Audit for vote was successful.");
                        } else {
                            LOGGER.warn("Audit failed! Vote Bot (or Auditor) is probably broken.");
                        }

                        counter = -1;
                    }

                    return;
                }
                if (message.startsWith("New Vote: ")) {
                    LOGGER.info("detected vote-start");

                    final Pattern pattern = Pattern.compile(NEW_VOTE_REGEX);
                    final Matcher matcher = pattern.matcher(message);

                    if (!matcher.matches()) {
                        LOGGER.warn("error: vote-start malformed");
                        return;
                    }

                    voteMechanics.callVote(matcher.group(2), 0, 0);
                } else if (message.startsWith("Results: ")) {
                    LOGGER.info("detected vote-end. Reading results");

                    counter = 0;
                }
            } else {
                if (counter != -1) {
                    LOGGER.info("Vote after end.");
                    return;
                }

                LOGGER.info("detected vote");
                voteMechanics.evaluateVote(from, message);
                final String currentResult = voteMechanics.getCurrentResult();
                LOGGER.info("Current state: {}", currentResult);
            }
        }
    }

    /**
     * Do nothing for private messages.
     *
     * @param from    source nick for the message
     * @param message message text
     */
    @Override
    public synchronized void privateMessage(final String from, final String message) {
    }

    /**
     * Do nothing on join messages.
     *
     * @param referent joining nick
     * @param channel       channel name
     */
    @Override
    public synchronized void join(final String referent, final String channel) {
    }

    /**
     * Do nothing on part messages.
     *
     * @param referent leaving nick
     * @param channel       channel name
     */
    @Override
    public synchronized void part(final String referent, final String channel) {
    }

    /**
     * Run the audit bot.
     * {@inheritDoc}
     *
     * @param args command line arguments
     */
    @Override
    public final void run(final String... args) {
        try {
            getIrcClient().initializeFromArgs(args).assignBot(this);

            getIrcClient().join(voteAuxChn);
        } catch (IOException | InterruptedException | ParseException | IRCClientException e) {
            LOGGER.error("error running votebot {}", e.getMessage());
        }
    }
    /**
     * Entry point for the audit bot.
     *
     * @param args command line arguments
     */
    public static void main(final String... args) {
        SpringApplication.run(CAcertVoteAuditor.class, args);
    }
}
