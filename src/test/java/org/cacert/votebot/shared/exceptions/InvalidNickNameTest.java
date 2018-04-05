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

package org.cacert.votebot.shared.exceptions;

import org.junit.Test;

import java.text.MessageFormat;
import java.util.ResourceBundle;

import static org.junit.Assert.assertEquals;

/**
 * @author Jan Dittberner
 */
public class InvalidNickNameTest {
    @Test
    public void testConstructor() {
        final InvalidNickName e = new InvalidNickName("test");
        assertEquals(
                e.getMessage(),
                MessageFormat.format(
                        ResourceBundle.getBundle("messages").getString("invalid_nick_name"),
                        "test"));
    }
}