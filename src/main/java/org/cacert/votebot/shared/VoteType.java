/*
 * Copyright (c) 2015  Felix Doerre
 * Copyright (c) 2015  Benny Baumann
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

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * Type for vote values.
 *
 * @author Felix Doerre
 * @author Jan Dittberner
 */
public enum VoteType {
    /**
     * Vote counts as yes.
     */
    AYE("aye", "yes", "oui", "ja"),
    /**
     * Vote counts as no.
     */
    NAYE("naye", "nay", "no", "non", "nein"),
    /**
     * Vote counts as abstain.
     */
    ABSTAIN("abstain", "enthaltung", "abs");

    private final Set<String> variants;

    /**
     * @param variants words that are counted as this vote type
     */
    VoteType(final String... variants) {
        this.variants = new HashSet<>(Arrays.asList(variants));
    }

    /**
     * Evaluate a given word to a VoteType value.
     *
     * @param vote word
     * @return VoteType value
     * @throws IllegalArgumentException if the word can not be evaluated
     */
    public static VoteType evaluate(final String vote) {
        final String normalized = vote.trim().toLowerCase();
        for (final VoteType value : values()) {
            if (value.variants.contains(normalized)) {
                return value;
            }
        }
        throw new IllegalArgumentException(
                String.format("%s is no valid vote", vote));
    }
}
