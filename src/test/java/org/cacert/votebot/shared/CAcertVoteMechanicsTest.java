/*
 * Copyright (c) 2016, 2018. Jan Dittberner
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

import org.cacert.votebot.shared.CAcertVoteMechanics.State;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.text.MessageFormat;
import java.util.Arrays;
import java.util.List;
import java.util.ResourceBundle;

import static org.cacert.votebot.shared.CAcertVoteMechanics.State.IDLE;
import static org.cacert.votebot.shared.CAcertVoteMechanics.State.RUNNING;

/**
 * @author Jan Dittberner
 */
public class CAcertVoteMechanicsTest {
    private CAcertVoteMechanics subject;
    private ResourceBundle messages;

    @Test
    public void testStateEnum() {
        State[] values = State.values();
        Assert.assertEquals(2, values.length);
        List<State> states = Arrays.asList(values);
        Assert.assertTrue(states.contains(RUNNING));
        Assert.assertTrue(states.contains(IDLE));
    }

    @Before
    public void setup() {
        messages = ResourceBundle.getBundle("messages");
        subject = new CAcertVoteMechanics();
    }

    @Test
    public void testNoVote() {
        String response = subject.evaluateVote("alice", "test");
        Assert.assertEquals(MessageFormat.format(messages.getString("no_vote_running"), "alice"), response);
    }

    @Test
    public void testCallVote() {
        String response = subject.callVote("test vote");
        Assert.assertEquals(messages.getString("vote_started"), response);
        Assert.assertEquals("test vote", subject.getTopic());
        Assert.assertEquals(RUNNING, subject.getState());
    }

    @Test
    public void testRefuseParallelCallVote() {
        subject.callVote("first");
        String response = subject.callVote("second");
        Assert.assertEquals(messages.getString("vote_running"), response);
        Assert.assertEquals("first", subject.getTopic());
        Assert.assertEquals(RUNNING, subject.getState());
    }

    @Test
    public void testFreshVoteResult() {
        subject.callVote("fresh vote");
        Assert.assertEquals("{}", subject.getCurrentResult());
    }

    @Test
    public void testVote() {
        subject.callVote("test");
        String response = subject.evaluateVote("alice", "aye");
        Assert.assertEquals(
                MessageFormat.format(messages.getString("count_vote"), "alice", "AYE"), response);
        Assert.assertEquals("{alice=AYE}", subject.getCurrentResult());
    }

    @Test
    public void testProxyVote() {
        subject.callVote("test");
        String response = subject.evaluateVote("alice", "proxy bob aye");
        Assert.assertEquals(
                MessageFormat.format(messages.getString("count_proxy_vote"), "alice", "bob", "AYE"),
                response);
        Assert.assertEquals("{bob=AYE}", subject.getCurrentResult());
    }

    @Test
    public void testInvalidVote() {
        subject.callVote("test");
        String response = subject.evaluateVote("alice", "moo");
        Assert.assertEquals(
                MessageFormat.format(messages.getString("vote_not_understood"), "alice"), response);
        Assert.assertEquals("{}", subject.getCurrentResult());
    }

    @Test
    public void testChangeVote() {
        subject.callVote("test");
        String response = subject.evaluateVote("alice", "aye");
        Assert.assertEquals(
                MessageFormat.format(messages.getString("count_vote"), "alice", "AYE"), response);
        Assert.assertEquals("{alice=AYE}", subject.getCurrentResult());
        response = subject.evaluateVote("alice", "naye");
        Assert.assertEquals(
                MessageFormat.format(messages.getString("count_vote"), "alice", "NAYE"), response);
        Assert.assertEquals("{alice=NAYE}", subject.getCurrentResult());
    }

    @Test
    public void testNoChangeForInvalidVote() {
        subject.callVote("test");
        String response = subject.evaluateVote("alice", "aye");
        Assert.assertEquals(
                MessageFormat.format(messages.getString("count_vote"), "alice", "AYE"), response);
        Assert.assertEquals("{alice=AYE}", subject.getCurrentResult());
        response = subject.evaluateVote("alice", "moo");
        Assert.assertEquals(
                MessageFormat.format(messages.getString("vote_not_understood"), "alice"), response);
        Assert.assertEquals("{alice=AYE}", subject.getCurrentResult());
    }

    @Test
    public void testInvalidProxyVote() {
        subject.callVote("test");
        String response = subject.evaluateVote("alice", "proxy bob moo");
        Assert.assertEquals(
                MessageFormat.format(messages.getString("vote_not_understood"), "alice"), response);
        Assert.assertEquals("{}", subject.getCurrentResult());
    }

    @Test
    public void testInvalidProxyVoteTokenCount() {
        subject.callVote("test");
        String response = subject.evaluateVote("alice", "proxy ");
        Assert.assertEquals(
                MessageFormat.format(messages.getString("invalid_proxy_vote"), "alice"), response);
        Assert.assertEquals("{}", subject.getCurrentResult());
    }

    @Test
    public void testCloseFreshVote() {
        subject.callVote("fresh vote");
        String[] response = subject.closeVote();
        Assert.assertArrayEquals(new String[]{"AYE: 0", "NAYE: 0", "ABSTAIN: 0"}, response);
        Assert.assertEquals("", subject.getTopic());
        Assert.assertEquals(IDLE, subject.getState());
    }

    @Test
    public void testCloseVote() {
        subject.callVote("fresh vote");
        subject.evaluateVote("alice", "AyE");
        subject.evaluateVote("bob", "NaYe");
        subject.evaluateVote("claire", "yes");
        subject.evaluateVote("debra", "abs");
        subject.evaluateVote("alice", "proxy mike no");
        subject.evaluateVote("debra", "ja");
        subject.evaluateVote("malory", "evil");
        String[] response = subject.closeVote();
        Assert.assertArrayEquals(new String[]{"AYE: 3", "NAYE: 2", "ABSTAIN: 0"}, response);
        Assert.assertEquals("", subject.getTopic());
        Assert.assertEquals(IDLE, subject.getState());
    }
}