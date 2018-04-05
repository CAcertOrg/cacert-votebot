/*
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

import org.cacert.votebot.shared.CAcertVoteMechanics;
import org.cacert.votebot.shared.IRCClient;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.util.ReflectionTestUtils;

import java.text.MessageFormat;
import java.util.ResourceBundle;

import static org.mockito.Mockito.*;

@SuppressWarnings("SpringJavaAutowiredMembersInspection")
@RunWith(SpringRunner.class)
@ContextConfiguration(classes = {TestConfiguration.class})
public class CAcertVoteBotTest {

    private static final String TEST_VOTE_TOPIC = "Is it really a vote or just a fake election?";

    @Autowired
    private IRCClient ircClient;
    @Autowired
    private CAcertVoteMechanics mechanics;

    private ResourceBundle messages;

    private CAcertVoteBot bot;

    @Before
    public void setupTest() {
        messages = ResourceBundle.getBundle("messages");
        bot = new CAcertVoteBot(mechanics, ircClient);
        ReflectionTestUtils.setField(bot, "meetingChannel", "meeting");
        ReflectionTestUtils.setField(bot, "voteChannel", "vote");
        ReflectionTestUtils.setField(bot, "timeout", 120);
    }

    @Test
    public void testStartVoteBot() throws Exception {
        when(mechanics.callVote(TEST_VOTE_TOPIC)).thenReturn(messages.getString("vote_started"));
        when(mechanics.getTopic()).thenReturn(TEST_VOTE_TOPIC);
        bot.privateMessage("test", String.format("vote %s", TEST_VOTE_TOPIC));
        verify(ircClient).send(
                MessageFormat.format(messages.getString("new_vote"), "test", TEST_VOTE_TOPIC),
                "meeting");
        verify(ircClient).send(
                MessageFormat.format(messages.getString("new_vote"), "test", TEST_VOTE_TOPIC),
                "vote");
        verify(ircClient).send(
                MessageFormat.format(messages.getString("cast_vote_in_vote_channel"), "vote"),
                "meeting");
        verify(ircClient).send(
                MessageFormat.format(messages.getString("cast_vote_in_next_seconds"), 120),
                "vote");
    }

    @Test
    public void testHelp() throws Exception {
        bot.privateMessage("test", "help");
        verify(ircClient).sendPrivate(messages.getString("help_message"), "test");
        verifyNoMoreInteractions(ircClient);
    }
}
