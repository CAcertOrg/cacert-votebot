/*
 * Copyright (c) 2015  Felix Doerre
 * Copyright (c) 2016  Jan Dittberner
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

import org.cacert.votebot.shared.exceptions.IRCClientException;

/**
 * Base class for IRC bot implementations.
 *
 * @author Felix Doerre
 * @author Jan Dittberner
 */
public abstract class IRCBot {
    /**
     * @return IRC client implementation associated with the bot
     */
    protected abstract IRCClient getIrcClient();

    /**
     * Handle a received public message.
     *
     * @param from    sender nick name
     * @param channel channel name
     * @param message message text
     * @throws IRCClientException for IRC client problems
     */
    public abstract void publicMessage(String from, String channel,
                                       String message) throws IRCClientException;

    /**
     * Handle a received private message.
     *
     * @param from    sender nick name
     * @param message message text
     * @throws IRCClientException for IRC client problems
     */
    public abstract void privateMessage(String from, String message) throws IRCClientException;

    /**
     * Send a public message.
     *
     * @param channel channel name
     * @param message message text
     * @throws IRCClientException for IRC client problems
     */
    protected final void sendPublicMessage(final String channel, final String message) throws IRCClientException {
        getIrcClient().send(message, channel);
    }

    /**
     * Send a private message.
     *
     * @param to      recipient nick name
     * @param message message text
     * @throws IRCClientException for IRC client problems
     */
    protected final void sendPrivateMessage(final String to, final String message) throws IRCClientException {
        getIrcClient().sendPrivate(message, to);
    }

    /**
     * Handle leave message.
     *
     * @param referent nick name
     * @param channel  channel name
     */
    public abstract void part(String referent, String channel);

    /**
     * Handle join message.
     *
     * @param referent nick name
     * @param channel  channel name
     */
    public abstract void join(String referent, String channel);
}
