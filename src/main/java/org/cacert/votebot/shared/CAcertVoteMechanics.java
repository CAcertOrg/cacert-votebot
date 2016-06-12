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

package org.cacert.votebot.shared;

import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.ResourceBundle;

/**
 * Represents the voting-automate for voting in IRC channels.
 */
@Component
public final class CAcertVoteMechanics {
    private static final String PROXY_RE = "^\\s*proxy\\s.*";
    private static final int VOTE_MESSAGE_PART_COUNT = 3;

    private State state = State.IDLE;
    private String topic;
    private final Map<String, VoteType> votes = new HashMap<>();
    private final ResourceBundle resourceBundle = ResourceBundle.getBundle("messages");

    /**
     * Voting state indicating whether a vote is currently running or not.
     */
    public enum State {
        /**
         * A vote is currently running.
         */
        RUNNING,
        /**
         * No vote is running.
         */
        IDLE
    }

    private String vote(final String voter, final String actor, final VoteType type) {
        votes.put(voter, type);

        if (voter.equals(actor)) {
            return String.format(resourceBundle.getString("count_vote"), actor, type);
        } else {
            return String.format(resourceBundle.getString("count_proxy_vote"), actor, voter, type);
        }
    }

    private String voteError(final String actor) {
        return String.format(resourceBundle.getString("vote_not_understood"), actor);
    }

    private String proxyVoteError(final String actor) {
        return String.format(resourceBundle.getString("invalid_proxy_vote"), actor);
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
            return String.format(resourceBundle.getString("no_vote_running"), actor);
        }

        final String voter;
        final String value;

        if (txt.toLowerCase().matches(PROXY_RE)) {
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
     * @return A response to <code>from</code> indicating success or failure.
     */
    public synchronized String callVote(final String topic) {
        if (state != State.IDLE) {
            return resourceBundle.getString("vote_running");
        }

        this.topic = topic;
        votes.clear();

        state = State.RUNNING;

        return resourceBundle.getString("vote_started");
    }

    /**
     * Ends a vote.
     *
     * @return An array of Strings containing result status messages.
     */
    public synchronized String[] closeVote() {
        final int[] resultCounts = new int[VoteType.values().length];

        for (final Entry<String, VoteType> voteEntry : votes.entrySet()) {
            resultCounts[voteEntry.getValue().ordinal()]++;
        }

        final String[] results = new String[VoteType.values().length];

        for (int i = 0; i < results.length; i++) {
            results[i] = String.format("%s: %d", VoteType.values()[i], resultCounts[i]);
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
