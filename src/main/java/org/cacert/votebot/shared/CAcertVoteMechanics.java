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

package org.cacert.votebot.shared;

import org.springframework.stereotype.Component;

import java.text.MessageFormat;
import java.util.Calendar;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.ResourceBundle;
import java.util.regex.Pattern;

/**
 * Represents the voting-automate for voting in IRC channels.
 */
@Component
public class CAcertVoteMechanics {
    private static final Pattern PROXY_RE = Pattern.compile("^\\s*proxy\\s.*");
    private static final int VOTE_MESSAGE_PART_COUNT = 3;

    private State state = State.IDLE;
    private String topic;
    private final Map<String, VoteType> votes = new HashMap<>();
    private final ResourceBundle messages = ResourceBundle.getBundle("messages");

    public Calendar getWarnTime() {
        return warnTime;
    }

    public Calendar getEndTime() {
        return endTime;
    }

    private Calendar warnTime;
    private Calendar endTime;
    private boolean warned;

    public boolean isWarned() {
        return warned;
    }

    public synchronized void setWarned() {
        this.warned = true;
    }

    /**
     * Voting state indicating whether a vote is currently running or not.
     */
    public enum State {
        /**
         * No vote is running.
         */
        IDLE,
        /**
         * A vote is currently running.
         */
        RUNNING,
        /**
         * A vote is about to stop.
         */
        STOPPING
    }

    private String vote(final String voter, final String actor, final VoteType type) {
        votes.put(voter, type);

        if (voter.equals(actor)) {
            return MessageFormat.format(messages.getString("count_vote"), actor, type);
        } else {
            return MessageFormat.format(messages.getString("count_proxy_vote"), actor, voter, type);
        }
    }

    private String voteError(final String actor) {
        return MessageFormat.format(messages.getString("vote_not_understood"), actor);
    }

    private String proxyVoteError(final String actor) {
        return MessageFormat.format(messages.getString("invalid_proxy_vote"), actor);
    }

    /**
     * Adds a vote to the current topic. This interprets proxies.
     *
     * @param actor the person that sent this vote
     * @param txt   the text that the person sent.
     * @return A message to <code>actor</code> indicating the result of his action.
     */
    public synchronized String evaluateVote(final String actor, final String txt) {
        if (state != State.RUNNING) {
            return MessageFormat.format(messages.getString("no_vote_running"), actor);
        }

        final String voter;
        final String value;

        if (PROXY_RE.matcher(txt.toLowerCase()).matches()) {
            String[] parts = txt.split("\\s+");
            if (parts.length == VOTE_MESSAGE_PART_COUNT) {
                voter = parts[1];
                value = parts[2];
            } else {
                return proxyVoteError(actor);
            }
        } else {
            voter = actor;
            value = txt.trim();
        }

        try {
            return vote(voter, actor, VoteType.evaluate(value));
        } catch (IllegalArgumentException iae) {
            return voteError(actor);
        }
    }

    /**
     * A new vote begins.
     *
     * @param topic the topic of the vote
     * @param warn seconds before the end of the vote to issue a warning
     * @param timeout seconds from the current time to the end of the vote
     * @return A response to <code>from</code> indicating success or failure.
     */
    public synchronized String callVote(final String topic, long warn, long timeout) {
        if (state != State.IDLE) {
            return messages.getString("vote_running");
        }

        this.topic = topic;
        votes.clear();

        this.warnTime = Calendar.getInstance();
        this.warnTime.add(Calendar.SECOND, Math.toIntExact(warn));
        this.warned = false;

        this.endTime = Calendar.getInstance();
        this.endTime.add(Calendar.SECOND, Math.toIntExact(timeout));

        state = State.RUNNING;

        return messages.getString("vote_started");
    }

    public synchronized String stopVote(String stopSource) {
        if (state != State.RUNNING) {
            throw new IllegalStateException(messages.getString("no_vote_running_private"));
        }

        state = State.STOPPING;

        return MessageFormat.format(messages.getString("finishing_vote"), this.topic, stopSource);
    }

    /**
     * Ends a vote.
     *
     * @return An array of Strings containing result status messages.
     */
    public synchronized String[] closeVote() {
        final String[] results = new String[VoteType.values().length];

        if (state != State.STOPPING) {
            throw new IllegalStateException(messages.getString("cannot_close_running_vote"));
        }

        final int[] resultCounts = new int[VoteType.values().length];

        for (final Entry<String, VoteType> voteEntry : votes.entrySet()) {
            resultCounts[voteEntry.getValue().ordinal()]++;
        }

        for (int i = 0; i < results.length; i++) {
            results[i] = MessageFormat.format("{0}: {1}", VoteType.values()[i], resultCounts[i]);
        }

        votes.clear();
        state = State.IDLE;
        topic = "";

        return results;
    }

    /**
     * @return Topic of the current vote.
     */
    public String getTopic() {
        return topic;
    }

    /**
     * @return Voting state
     */
    public State getState() {
        return state;
    }

    /**
     * @return current vote results as string
     */
    public String getCurrentResult() {
        return votes.toString();
    }

}
