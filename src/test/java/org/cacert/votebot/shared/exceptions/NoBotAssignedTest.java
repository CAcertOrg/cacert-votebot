/*
 * Copyright (c) 2016-2020. Jan Dittberner
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

package org.cacert.votebot.shared.exceptions;

import org.junit.jupiter.api.Test;

import java.util.ResourceBundle;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Tests for {@link NoBotAssigned}.
 *
 * @author Jan Dittberner
 */
public class NoBotAssignedTest {

    @Test
    public void testConstructor() {
        final ResourceBundle resourceBundle = ResourceBundle.getBundle("messages");
        final NoBotAssigned e = new NoBotAssigned();
        assertEquals(resourceBundle.getString("assign_bot_not_called"), e.getMessage());
    }
}